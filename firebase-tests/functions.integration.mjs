import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const projectId = process.env.GCLOUD_PROJECT ?? "sudoku-local";
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST ?? "127.0.0.1:9099";
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST ?? "127.0.0.1:8080";
const functionsHost = process.env.FUNCTIONS_EMULATOR_HOST ?? "127.0.0.1:5001";
const root = `projects/${projectId}/databases/(default)/documents`;
const fixtures = JSON.parse(await readFile(new URL("../functions/test/fixtures/scoring.json", import.meta.url), "utf8"));
const classicFixture = fixtures.find((fixture) => fixture.name === "classic-9-easy").payload;
const samuraiFixture = fixtures.find((fixture) => fixture.name === "samurai-easy").payload;
const dailyFixture = fixtures.find((fixture) => fixture.name === "daily-medium").payload;

function pass(message) {
  console.log(`PASS ${message}`);
}

async function createUser() {
  const response = await fetch(
    `http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ returnSecureToken: true }),
    },
  );
  if (!response.ok) {
    assert.fail(`Auth sign-up: ${response.status} ${await response.text()}`);
  }
  return response.json();
}

async function callCompletion(user, payload) {
  const response = await fetch(
    `http://${functionsHost}/${projectId}/europe-west1/submitCompletion`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${user.idToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ data: payload }),
    },
  );
  const body = await response.json();
  if (!response.ok || body.error) {
    return {
      ok: false,
      code: String(body.error?.status ?? response.status).toLowerCase().replaceAll("_", "-"),
      body,
    };
  }
  return { ok: true, result: body.result ?? body.data };
}

async function readDocument(user, docPath) {
  const response = await fetch(`http://${firestoreHost}/v1/${root}/${docPath}`, {
    headers: { Authorization: `Bearer ${user.idToken}` },
  });
  if (!response.ok) {
    assert.fail(`Firestore read ${docPath}: ${response.status} ${await response.text()}`);
  }
  return response.json();
}

function integer(document, name) {
  return Number(document.fields[name].integerValue);
}

const classic = structuredClone(classicFixture);
const classicEmptyCount = classic.givens.filter((value) => value === 0).length;
classic.elapsedMillis = classicEmptyCount * 1_000;
const classicUser = await createUser();
const classicResult = await callCompletion(classicUser, classic);
assert.equal(classicResult.ok, true, JSON.stringify(classicResult));
const classicGames = await readDocument(
  classicUser,
  `championship/${classicResult.result.championshipPeriod}/scores/${classicUser.localId}`,
);
assert.equal(integer(classicGames, "games"), 1);
assert.equal(integer(classicGames, "points"), classicResult.result.points);
pass("valid Classic accepted and Championship incremented exactly once");

const samurai = structuredClone(samuraiFixture);
samurai.elapsedMillis = samurai.givens.filter((value) => value === 0).length * 1_000;
const samuraiUser = await createUser();
const samuraiResult = await callCompletion(samuraiUser, samurai);
assert.equal(samuraiResult.ok, true, JSON.stringify(samuraiResult));
assert.ok(samuraiResult.result.arenaPeriod);
const arenaScore = await readDocument(
  samuraiUser,
  `arena/${samuraiResult.result.arenaPeriod}/scores/${samuraiUser.localId}`,
);
assert.equal(integer(arenaScore, "games"), 1);
pass("valid Samurai accepted and Arena incremented");

const tampered = structuredClone(classic);
tampered.solutionCells[1] = tampered.solutionCells[0];
const tamperedResult = await callCompletion(await createUser(), tampered);
assert.equal(tamperedResult.code, "failed-precondition", JSON.stringify(tamperedResult));
pass("tampered grid rejected");

const duplicateUser = await createUser();
const duplicateFirst = await callCompletion(duplicateUser, classic);
assert.equal(duplicateFirst.ok, true, JSON.stringify(duplicateFirst));
await new Promise((resolve) => setTimeout(resolve, 30_000));
const duplicateResult = await callCompletion(duplicateUser, classic);
assert.equal(duplicateResult.code, "already-exists", JSON.stringify(duplicateResult));
const duplicateScore = await readDocument(
  duplicateUser,
  `championship/${duplicateFirst.result.championshipPeriod}/scores/${duplicateUser.localId}`,
);
assert.equal(integer(duplicateScore, "games"), 1);
pass("duplicate rejected without a second tournament increment");

const daily = structuredClone(dailyFixture);
daily.dailyDate = new Date().toISOString().slice(0, 10);
daily.elapsedMillis = daily.givens.filter((value) => value === 0).length * 1_000;
const dailyUser = await createUser();
const dailyResult = await callCompletion(dailyUser, daily);
assert.equal(dailyResult.ok, true, JSON.stringify(dailyResult));
await new Promise((resolve) => setTimeout(resolve, 40_000));
const changedDaily = structuredClone(daily);
const dailyGivenIndex = changedDaily.givens.findIndex((value) => value !== 0);
changedDaily.givens[dailyGivenIndex] = 0;
changedDaily.elapsedMillis = changedDaily.givens.filter((value) => value === 0).length * 1_000;
const dailyDuplicate = await callCompletion(dailyUser, changedDaily);
assert.equal(dailyDuplicate.code, "already-exists", JSON.stringify(dailyDuplicate));
const dailyScore = await readDocument(
  dailyUser,
  `championship/${dailyResult.result.championshipPeriod}/scores/${dailyUser.localId}`,
);
assert.equal(integer(dailyScore, "games"), 1);
pass("second Daily completion for the same uid and date rejected");

const throttleUser = await createUser();
const throttleFirst = await callCompletion(throttleUser, classic);
assert.equal(throttleFirst.ok, true, JSON.stringify(throttleFirst));
const distinctCompletion = structuredClone(classic);
const givenIndex = distinctCompletion.givens.findIndex((value) => value !== 0);
distinctCompletion.givens[givenIndex] = 0;
distinctCompletion.elapsedMillis = distinctCompletion.givens.filter((value) => value === 0).length * 1_000;
const tooFastResult = await callCompletion(throttleUser, distinctCompletion);
assert.equal(tooFastResult.code, "resource-exhausted", JSON.stringify(tooFastResult));
pass("too-fast subsequent completion rejected");

const arenaPath = `arena/${samuraiResult.result.arenaPeriod}/scores/${samuraiUser.localId}`;
const writeResponse = await fetch(`http://${firestoreHost}/v1/${root}:commit`, {
  method: "POST",
  headers: {
    Authorization: `Bearer ${samuraiUser.idToken}`,
    "Content-Type": "application/json",
  },
  body: JSON.stringify({
    writes: [{
      update: {
        name: `${root}/${arenaPath}`,
        fields: {
          uid: { stringValue: samuraiUser.localId },
          displayName: { stringValue: "Cheater" },
          points: { integerValue: "999999" },
          games: { integerValue: "99" },
        },
      },
    }],
  }),
});
assert.equal(writeResponse.status, 403, `Direct Arena write returned ${writeResponse.status}.`);
pass("direct client Arena write denied");
