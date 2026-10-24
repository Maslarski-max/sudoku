const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const {
  completionDocumentId,
  minimumEmptyCells,
  requireAuthenticated,
  validateCompletion,
} = require("../lib/completion");
const fixtures = JSON.parse(fs.readFileSync(path.join(__dirname, "fixtures/scoring.json"), "utf8"));
const byName = Object.fromEntries(fixtures.map((fixture) => [fixture.name, fixture]));
const validClassic = byName["classic-9-easy"].payload;
const validOutside = byName["outside-easy"].payload;

function expectCode(action, code) {
  assert.throws(action, (error) => error.code === code);
}

test("Kotlin scoring fixtures match exactly", () => {
  for (const fixture of fixtures) {
    assert.equal(validateCompletion(fixture.payload, Date.parse("2026-10-24T12:00:00Z")).points,
      fixture.expectedPoints, fixture.name);
  }
});

test("authentication is required", () => {
  expectCode(() => requireAuthenticated(null), "unauthenticated");
  assert.equal(requireAuthenticated({ uid: "user" }), undefined);
});

test("rejects invalid shape, enums, array lengths, and values", () => {
  expectCode(() => validateCompletion({ ...validClassic, kind: "CHEAT" }), "invalid-argument");
  expectCode(() => validateCompletion({ ...validClassic, gridSize: 11 }), "invalid-argument");
  expectCode(() => validateCompletion({ ...validClassic, givens: [] }), "invalid-argument");
  expectCode(() => validateCompletion({ ...validClassic, elapsedMillis: 0 }), "invalid-argument");
  expectCode(() => validateCompletion({ ...validClassic, moves: -1 }), "invalid-argument");
  const outOfRange = [...validClassic.solutionCells];
  outOfRange[0] = 10;
  expectCode(() => validateCompletion({ ...validClassic, solutionCells: outOfRange }), "invalid-argument");
});

test("rejects invalid topology solutions and wrong givens", () => {
  const tampered = [...validClassic.solutionCells];
  tampered[1] = tampered[0];
  expectCode(() => validateCompletion({ ...validClassic, solutionCells: tampered }), "failed-precondition");
  const wrongGiven = [...validClassic.givens];
  const givenIndex = wrongGiven.findIndex((value) => value !== 0);
  wrongGiven[givenIndex] = wrongGiven[givenIndex] % validClassic.gridSize + 1;
  expectCode(() => validateCompletion({ ...validClassic, givens: wrongGiven }), "failed-precondition");
  const unfilledSolution = [...validClassic.solutionCells];
  unfilledSolution[validClassic.givens.findIndex((value) => value === 0)] = 0;
  expectCode(() => validateCompletion({ ...validClassic, solutionCells: unfilledSolution }), "failed-precondition");
});

test("rejects Outside clue violations", () => {
  const clues = structuredClone(validOutside.outsideClues);
  const firstThree = validOutside.solutionCells.slice(0, 3);
  clues.rowsFromLeft[0] = [Array.from({ length: 9 }, (_, index) => index + 1)
    .find((digit) => !firstThree.includes(digit))];
  expectCode(() => validateCompletion({ ...validOutside, outsideClues: clues }), "failed-precondition");
});

test("rejects implausible completion time, empties, combos, and stale daily dates", () => {
  const empties = validClassic.givens.filter((value) => value === 0).length;
  expectCode(() => validateCompletion({ ...validClassic, elapsedMillis: empties * 1_000 - 1 }), "failed-precondition");
  const tooFewEmpties = [...validClassic.givens];
  const emptyIndex = tooFewEmpties.findIndex((value) => value === 0);
  tooFewEmpties[emptyIndex] = validClassic.solutionCells[emptyIndex];
  expectCode(() => validateCompletion({ ...validClassic, givens: tooFewEmpties }), "failed-precondition");
  const givenIndex = validClassic.givens.findIndex((value) => value !== 0);
  expectCode(() => validateCompletion({ ...validClassic, comboCells: [givenIndex] }), "failed-precondition");
  const daily = byName["daily-medium"].payload;
  expectCode(() => validateCompletion({ ...daily, dailyDate: "2026-10-20" }, Date.parse("2026-10-24T12:00:00Z")),
    "failed-precondition");
  expectCode(() => validateCompletion({ ...daily, dailyDate: "2026-02-30" }, Date.parse("2026-10-24T12:00:00Z")),
    "invalid-argument");
  expectCode(() => validateCompletion({ ...daily, outsideClues: {} }, Date.parse("2026-10-24T12:00:00Z")),
    "invalid-argument");
});

test("generator thresholds and dedupe identity are stable", () => {
  assert.equal(minimumEmptyCells(validClassic), Math.floor(81 * 0.42));
  const id = completionDocumentId(validClassic);
  assert.equal(id, completionDocumentId({ ...validClassic }));
  const changed = { ...validClassic, givens: [...validClassic.givens] };
  changed.givens[0] = validClassic.solutionCells[0];
  assert.notEqual(id, completionDocumentId(changed));
});
