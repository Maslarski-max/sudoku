import { initializeApp } from "firebase-admin/app";
import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import {
  completionDocumentId,
  dailyCompletionDocumentId,
  requireAuthenticated,
  validateCompletion,
} from "./completion";

initializeApp();
const firestore = getFirestore();

export const submitCompletion = onCall({ region: "europe-west1" }, async (call) => {
  requireAuthenticated(call.auth);
  const validated = validateCompletion(call.data);
  const { request, points, effectiveDifficulty } = validated;
  const uid = call.auth.uid;
  const playerRef = firestore.collection("players").doc(uid);
  const dedupeRef = playerRef.collection("completions").doc(completionDocumentId(request));
  const dailyDate = dailyCompletionDocumentId(request);
  const dailyRef = dailyDate ? playerRef.collection("daily").doc(dailyDate) : undefined;
  const instant = Timestamp.now();
  const date = instant.toDate();
  const championshipPeriod = String(date.getUTCFullYear());
  const arenaPeriod = `${championshipPeriod}-${String(date.getUTCMonth() + 1).padStart(2, "0")}`;
  const displayName = `Player ${uid.slice(0, 6).toUpperCase()}`;

  await firestore.runTransaction(async (transaction) => {
    const [player, duplicate] = await Promise.all([
      transaction.get(playerRef),
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
