const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const {
  completionDocumentId,
  computePoints,
  dailyCompletionDocumentId,
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
    const validated = validateCompletion(fixture.payload, Date.parse("2026-10-24T12:00:00Z"));
    assert.equal(computePoints(fixture.payload, validated.emptyCells, fixture.payload.difficulty),
      fixture.expectedPoints, fixture.name);
    assert.equal(validated.points,
      computePoints(fixture.payload, validated.emptyCells, validated.effectiveDifficulty), fixture.name);
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
  const activeCells = validClassic.gridSize * validClassic.gridSize;
  expectCode(() => validateCompletion({ ...validClassic, moves: activeCells * 10 + 1 }), "invalid-argument");
  expectCode(() => validateCompletion({ ...validClassic, mistakes: activeCells * 10 + 1 }), "invalid-argument");
  expectCode(() => validateCompletion({ ...validClassic, hintsUsed: activeCells + 1 }), "invalid-argument");
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
  const tooFewEmpties = [...validClassic.solutionCells];
  let remainingEmpties = minimumEmptyCells(validClassic) - 1;
  for (let index = 0; index < validClassic.givens.length && remainingEmpties > 0; index++) {
    if (validClassic.givens[index] === 0) {
      tooFewEmpties[index] = 0;
      remainingEmpties--;
    }
  }
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
  for (const fixture of fixtures) {
    const activeCells = fixture.payload.solutionCells.filter((value) => value !== 0).length;
    assert.equal(minimumEmptyCells(fixture.payload), Math.floor(activeCells * 0.25), fixture.name);
  }
  const id = completionDocumentId(validClassic);
  assert.equal(id, completionDocumentId({ ...validClassic }));
  const changed = { ...validClassic, givens: [...validClassic.givens] };
  changed.givens[0] = validClassic.solutionCells[0];
  assert.notEqual(id, completionDocumentId(changed));
});

test("effective difficulty caps points to empties and claimed difficulty", () => {
  const puzzleWithEmpties = (emptyCells, difficulty) => {
    const givens = [...validClassic.solutionCells];
    const activeIndices = validClassic.solutionCells.flatMap((value, index) => value === 0 ? [] : [index]);
    for (const index of activeIndices.slice(0, emptyCells)) givens[index] = 0;
    return {
      ...validClassic,
      difficulty,
      givens,
      comboCells: [],
      elapsedMillis: emptyCells * 1_000,
    };
  };

  const sparseHard = puzzleWithEmpties(21, "HARD");
  const sparseResult = validateCompletion(sparseHard, Date.parse("2026-10-24T12:00:00Z"));
  assert.equal(sparseResult.effectiveDifficulty, "EASY");
  assert.equal(sparseResult.points, computePoints(sparseHard, 21, "EASY"));
  assert.notEqual(sparseResult.points, computePoints(sparseHard, 21, "HARD"));

  const genuineHard = puzzleWithEmpties(50, "HARD");
  const hardResult = validateCompletion(genuineHard, Date.parse("2026-10-24T12:00:00Z"));
  assert.equal(hardResult.effectiveDifficulty, "HARD");
  assert.equal(hardResult.points, computePoints(genuineHard, 50, "HARD"));

  const claimedEasy = puzzleWithEmpties(50, "EASY");
  const easyResult = validateCompletion(claimedEasy, Date.parse("2026-10-24T12:00:00Z"));
  assert.equal(easyResult.effectiveDifficulty, "EASY");
  assert.equal(easyResult.points, computePoints(claimedEasy, 50, "EASY"));
});

test("35-empty claimed-hard Classic matches the client score at effective Medium", () => {
  const givens = [...validClassic.solutionCells];
  const activeIndices = validClassic.solutionCells.flatMap((value, index) => value === 0 ? [] : [index]);
  for (const index of activeIndices.slice(0, 35)) givens[index] = 0;
  const request = {
    ...validClassic,
    difficulty: "HARD",
    givens,
    comboCells: [],
    elapsedMillis: 280_000,
    moves: 35,
    mistakes: 0,
    hintsUsed: 0,
  };

  const validated = validateCompletion(request, Date.parse("2026-10-24T12:00:00Z"));
  assert.equal(validated.emptyCells, 35);
  assert.equal(validated.effectiveDifficulty, "MEDIUM");
  assert.equal(computePoints(request, validated.emptyCells, validated.effectiveDifficulty), 788);
  assert.equal(validated.points, 788);
});

test("daily dedupe identity is scoped to the user and date", () => {
  const daily = byName["daily-medium"].payload;
  assert.equal(dailyCompletionDocumentId(daily), daily.dailyDate);
  assert.equal(dailyCompletionDocumentId({ ...daily, dailyDate: "2026-10-25" }), "2026-10-25");
  assert.equal(dailyCompletionDocumentId(validClassic), undefined);
});
