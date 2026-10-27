import { initializeApp } from "firebase-admin/app";
import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import {
  completionDocumentId,
  dailyCompletionDocumentId,
  requireAuthenticated,
  validateCompletion,
} from "./completion";
import { defaultDisplayName, normalizeDisplayName } from "./profile";

initializeApp();
const firestore = getFirestore();

function periodsFor(instant: Timestamp): { championshipPeriod: string; arenaPeriod: string } {
  const date = instant.toDate();
  const championshipPeriod = String(date.getUTCFullYear());
  const arenaPeriod = `${championshipPeriod}-${String(date.getUTCMonth() + 1).padStart(2, "0")}`;
  return { championshipPeriod, arenaPeriod };
}

export const submitCompletion = onCall({ region: "europe-west1" }, async (call) => {
  requireAuthenticated(call.auth);
  const validated = validateCompletion(call.data);
  const { request, points, effectiveDifficulty } = validated;
  const uid = call.auth.uid;
  const playerRef = firestore.collection("players").doc(uid);
  const profileRef = firestore.collection("profiles").doc(uid);
  const dedupeRef = playerRef.collection("completions").doc(completionDocumentId(request));
  const dailyDate = dailyCompletionDocumentId(request);
  const dailyRef = dailyDate ? playerRef.collection("daily").doc(dailyDate) : undefined;
  const instant = Timestamp.now();
  const { championshipPeriod, arenaPeriod } = periodsFor(instant);

  await firestore.runTransaction(async (transaction) => {
    const [player, profile, duplicate] = await Promise.all([
      transaction.get(playerRef),
      transaction.get(profileRef),
      transaction.get(dedupeRef),
    ]);
    const dailyDuplicate = dailyRef ? await transaction.get(dailyRef) : undefined;
    const lastCompletionAt = player.get("lastCompletionAt") as Timestamp | undefined;
    if (lastCompletionAt &&
        instant.toMillis() - lastCompletionAt.toMillis() < 0.8 * request.elapsedMillis) {
      throw new HttpsError("resource-exhausted", "Completion rate limit exceeded.");
    }
    if (duplicate.exists || dailyDuplicate?.exists) {
      throw new HttpsError("already-exists", "Completion was already submitted.");
    }

    const displayName = (profile.get("displayName") as string | undefined) ?? defaultDisplayName(uid);
    const score = {
      uid,
      displayName,
      points: FieldValue.increment(points),
      games: FieldValue.increment(1),
      updatedAt: FieldValue.serverTimestamp(),
    };
    transaction.set(
      firestore.collection("championship").doc(championshipPeriod).collection("scores").doc(uid),
      score,
      { merge: true },
    );
    if (request.variant === "SAMURAI" || request.variant === "FLOWER") {
      transaction.set(
        firestore.collection("arena").doc(arenaPeriod).collection("scores").doc(uid),
        score,
        { merge: true },
      );
    }
    transaction.create(dedupeRef, {
      kind: request.kind,
      variant: request.variant ?? null,
      gridSize: request.gridSize,
      givens: request.givens,
      effectiveDifficulty,
      points,
      createdAt: FieldValue.serverTimestamp(),
    });
    if (dailyRef) {
      transaction.create(dailyRef, {
        dailyDate,
        createdAt: FieldValue.serverTimestamp(),
      });
    }
    transaction.set(playerRef, { lastCompletionAt: FieldValue.serverTimestamp() }, { merge: true });
  });

  return {
    points,
    effectiveDifficulty,
    championshipPeriod,
    ...(request.variant === "SAMURAI" || request.variant === "FLOWER" ? { arenaPeriod } : {}),
  };
});

export const setDisplayName = onCall({ region: "europe-west1" }, async (call) => {
  requireAuthenticated(call.auth);
  const name = normalizeDisplayName(call.data?.name);
  const uid = call.auth.uid;
  const instant = Timestamp.now();
  const { championshipPeriod, arenaPeriod } = periodsFor(instant);
  const playerRef = firestore.collection("players").doc(uid);
  const profileRef = firestore.collection("profiles").doc(uid);
  const leaderboardRefs = [9, 12, 15, 18].flatMap((gridSize) =>
    ["EASY", "MEDIUM", "HARD"].map((difficulty) =>
      firestore.collection("leaderboards").doc(`${gridSize}_${difficulty}`)
        .collection("scores").doc(uid)));
  const championshipRef = firestore.collection("championship").doc(championshipPeriod)
    .collection("scores").doc(uid);
  const arenaRef = firestore.collection("arena").doc(arenaPeriod).collection("scores").doc(uid);

  await firestore.runTransaction(async (transaction) => {
    const snapshots = await transaction.getAll(
      playerRef,
      profileRef,
      ...leaderboardRefs,
      championshipRef,
      arenaRef,
    );
    const player = snapshots[0];
    const nameChangedAt = player.get("nameChangedAt") as Timestamp | undefined;
    if (nameChangedAt && instant.toMillis() - nameChangedAt.toMillis() < 30_000) {
      throw new HttpsError(
        "resource-exhausted",
        "Please wait before changing your display name again.",
        { reason: "RATE_LIMIT" },
      );
    }

    for (const score of snapshots.slice(2)) {
      if (score.exists) transaction.update(score.ref, { displayName: name });
    }
    transaction.set(profileRef, { displayName: name }, { merge: true });
    transaction.set(
      playerRef,
      { nameChangedAt: FieldValue.serverTimestamp() },
      { merge: true },
    );
  });

  return { displayName: name };
});
