const projectId = process.env.GCLOUD_PROJECT ?? "sudoku-local";
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST ?? "127.0.0.1:9099";
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST ?? "127.0.0.1:8080";
const projectRoot = `projects/${projectId}/databases/(default)/documents`;

async function createUser() {
  const response = await fetch(
    `http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ returnSecureToken: true }),
    },
  );
  if (!response.ok) throw new Error(`Auth emulator sign-up failed: ${response.status} ${await response.text()}`);
  return response.json();
}

function encodeFields(fields) {
  return Object.fromEntries(
    Object.entries(fields).map(([key, value]) => [
      key,
      typeof value === "string" ? { stringValue: value } : { integerValue: String(value) },
    ]),
  );
}

async function write(token, path, fields, exists, timestampField = "updatedAt") {
  const response = await fetch(
    `http://${firestoreHost}/v1/${projectRoot}:commit`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        writes: [
          {
            update: { name: `${projectRoot}/${path}`, fields: encodeFields(fields) },
            updateTransforms: [{ fieldPath: timestampField, setToServerValue: "REQUEST_TIME" }],
            currentDocument: { exists },
          },
        ],
      }),
    },
  );
  return { ok: response.ok, status: response.status, body: await response.text() };
}

function check(condition, message) {
  if (!condition) throw new Error(message);
  console.log(`PASS ${message}`);
}

async function expectDenied(result, label) {
  check(!result.ok, `${label} rejected (HTTP ${result.status})`);
}

const owner = await createUser();
const now = new Date();
const month = `${now.getUTCFullYear()}-${String(now.getUTCMonth() + 1).padStart(2, "0")}`;
const year = String(now.getUTCFullYear());
const arenaPath = `arena/${month}/scores/${owner.localId}`;
const baseFields = {
  uid: owner.localId,
  displayName: "Rule Tester",
  points: 1_200,
  games: 1,
};

await expectDenied(
  await write(owner.idToken, arenaPath, baseFields, false),
  "direct Arena create",
);
await expectDenied(
  await write(owner.idToken, arenaPath, { ...baseFields, points: 1_300, games: 2 }, true),
  "direct Arena update",
);
await expectDenied(
  await write(owner.idToken, `championship/${year}/scores/${owner.localId}`, baseFields, false),
  "direct Championship create",
);
await expectDenied(
  await write(owner.idToken, `players/${owner.localId}`, baseFields, false),
  "direct player-document write",
);
const classic = await write(
  owner.idToken,
  `leaderboards/9_EASY/scores/${owner.localId}`,
  {
    uid: owner.localId,
    displayName: "Rule Tester",
    gridSize: 9,
    difficulty: "EASY",
    timeMillis: 12_000,
    moves: 10,
    mistakes: 0,
    points: 100,
  },
  false,
  "completedAt",
);
check(classic.ok, `existing classic leaderboard create (HTTP ${classic.status}: ${classic.body})`);

await expectDenied(
  await write(
    owner.idToken,
    `players/${owner.localId}/completions/test`,
    { ...baseFields },
    false,
  ),
  "direct player completion write",
);

console.log("All Firestore engagement rules checks passed.");
