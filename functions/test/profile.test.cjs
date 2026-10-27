const assert = require("node:assert/strict");
const test = require("node:test");
const { defaultDisplayName, normalizeDisplayName } = require("../lib/profile");

function expectReason(raw, reason) {
  assert.throws(() => normalizeDisplayName(raw), (error) => {
    assert.equal(error.code, "invalid-argument");
    assert.equal(error.details.reason, reason);
    return true;
  });
}

test("trims and collapses whitespace", () => {
  assert.equal(normalizeDisplayName("  Max   99 "), "Max 99");
});

test("builds the default display name from the first six uid characters", () => {
  assert.equal(defaultDisplayName("ab12cdEF"), "Player AB12CD");
});

test("enforces code-point length after normalization", () => {
  expectReason("ab", "LENGTH");
  assert.equal(normalizeDisplayName("abc"), "abc");
  assert.equal(normalizeDisplayName("a".repeat(20)), "a".repeat(20));
  expectReason("a".repeat(21), "LENGTH");
  expectReason("😀😀", "LENGTH");
});

test("accepts Unicode letters and rejects unsupported characters", () => {
  assert.equal(normalizeDisplayName("Željko"), "Željko");
  expectReason("Max🙂", "CHARACTERS");
  expectReason("_max", "CHARACTERS");
  expectReason(42, "CHARACTERS");
});

test("reserves automatic Player names without blocking Playerone", () => {
  expectReason("Player AB12CD", "RESERVED");
  expectReason("player x", "RESERVED");
  assert.equal(normalizeDisplayName("Playerone"), "Playerone");
});

test("rejects blocked substrings and whole tokens after normalization", () => {
  for (const name of ["Fuck", "f.u.c.k", "FuCk3r", "j3bem", "Admin", "big_dick"]) {
    expectReason(name, "BLOCKED");
  }
});

test("does not reject harmless names containing blocked whole words", () => {
  for (const name of ["Cassandra", "Essex", "Dickens", "Peacock", "Pedometer", "Hitchcock"]) {
    assert.equal(normalizeDisplayName(name), name);
  }
});
