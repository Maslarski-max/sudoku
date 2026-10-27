import { HttpsError } from "firebase-functions/v2/https";
import { BLOCKED_SUBSTRINGS, BLOCKED_WORDS } from "./blocklist";

export type DisplayNameErrorReason = "LENGTH" | "CHARACTERS" | "RESERVED" | "BLOCKED";

const DISPLAY_NAME_PATTERN = /^[\p{L}\p{N}][\p{L}\p{M}\p{N} _.\-]*$/u;
const LEET_MAP: Record<string, string> = {
  "0": "o",
  "1": "i",
  "3": "e",
  "4": "a",
  "5": "s",
  "7": "t",
  "8": "b",
  "@": "a",
  "$": "s",
};

function invalid(reason: DisplayNameErrorReason, message: string): never {
  throw new HttpsError("invalid-argument", message, { reason });
}

function mapBlockedCharacters(value: string): string {
  return value.toLowerCase()
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .replace(/đ/g, "d")
    .replace(/ß/g, "ss")
    .replace(/[0134578@$]/g, (character) => LEET_MAP[character]);
}

export function normalizeDisplayName(raw: unknown): string {
  if (typeof raw !== "string") invalid("CHARACTERS", "Display name must be text.");

  const name = raw.normalize("NFC").trim().replace(/\s+/gu, " ");
  const length = [...name].length;
  if (length < 3 || length > 20) {
    invalid("LENGTH", "Display name must be between 3 and 20 characters.");
  }
  if (!DISPLAY_NAME_PATTERN.test(name)) {
    invalid("CHARACTERS", "Display name contains unsupported characters.");
  }
  if (/^player\b/i.test(name)) {
    invalid("RESERVED", "Names starting with Player are reserved.");
  }

  const mapped = mapBlockedCharacters(name);
  const compact = mapped.replace(/[^a-z]/g, "");
  const tokens = mapped.split(/[ _.\-]+/).map((token) => token.replace(/[^a-z]/g, ""));
  if (BLOCKED_SUBSTRINGS.some((blocked) => compact.includes(blocked)) ||
      tokens.some((token) => BLOCKED_WORDS.includes(token))) {
    invalid("BLOCKED", "Display name is not allowed.");
  }
  return name;
}

export function defaultDisplayName(uid: string): string {
  return `Player ${uid.slice(0, 6).toUpperCase()}`;
}
