const projectId = process.env.GCLOUD_PROJECT ?? "sudoku-local";
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST ?? "127.0.0.1:9098";
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST ?? "127.0.0.1:8081";
const projectRoot = `projects/${projectId}/databases/(default)/documents`;
const maxPerGame = 66_420;

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

async function expectAllowed(result, label) {
  check(result.ok, `${label} (HTTP ${result.status}: ${result.body})`);
}

async function expectDenied(result, label) {
  check(!result.ok, `${label} rejected (HTTP ${result.status})`);
}

const owner = await createUser();
const other = await createUser();
const now = new Date();
const month = `${now.getUTCFullYear()}-${String(now.getUTCMonth() + 1).padStart(2, "0")}`;
const previousMonthDate = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 1, 1));
const previousMonth =
  `${previousMonthDate.getUTCFullYear()}-${String(previousMonthDate.getUTCMonth() + 1).padStart(2, "0")}`;
const year = String(now.getUTCFullYear());
const arenaPath = `arena/${month}/scores/${owner.localId}`;
const baseFields = {
  uid: owner.localId,
  displayName: "Rule Tester",
  points: 1_200,
  games: 1,
};

await expectAllowed(
  await write(owner.idToken, arenaPath, baseFields, false),
  "valid Arena create",
);
await expectAllowed(
  await write(owner.idToken, arenaPath, { ...baseFields, points: 1_300, games: 2 }, true),
  "valid +1 Arena update",
);
await expectDenied(
  await write(
    owner.idToken,
    arenaPath,
    { ...baseFields, points: 1_300 + maxPerGame + 1, games: 3 },
    true,
  ),
  "over-max score delta",
);
await expectDenied(
  await write(owner.idToken, arenaPath, { ...baseFields, points: 1_310, games: 4 }, true),
  "skipped game count",
);
await expectDenied(
  await write(
    owner.idToken,
    `arena/${month}/scores/${other.localId}`,
    { ...baseFields, uid: other.localId },
    false,
  ),
  "wrong document owner",
);
await expectDenied(
  await write(owner.idToken, `arena/${previousMonth}/scores/${owner.localId}`, baseFields, false),
  "write to last month's Arena",
);
await expectAllowed(
  await write(
    owner.idToken,
    `championship/${year}/scores/${owner.localId}`,
    baseFields,
    false,
  ),
  "valid Championship create",
);
await expectAllowed(
  await write(
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
  ),
  "existing classic leaderboard create",
);

console.log("All Firestore engagement rules checks passed.");
