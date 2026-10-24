import { createHash } from "node:crypto";
import { HttpsError } from "firebase-functions/v2/https";

export type CompletionKind = "CLASSIC" | "DAILY" | "VARIANT";
export type Variant = "SAMURAI" | "FLOWER" | "OUTSIDE";
export type Difficulty = "EASY" | "MEDIUM" | "HARD";

export interface OutsideClues {
  rowsFromLeft: number[][];
  rowsFromRight: number[][];
  columnsFromTop: number[][];
  columnsFromBottom: number[][];
}

export interface CompletionRequest {
  kind: CompletionKind;
  variant?: Variant;
  gridSize: 9 | 12 | 15 | 18;
  difficulty: Difficulty;
  dailyDate?: string;
  givens: number[];
  solutionCells: number[];
  comboCells: number[];
  outsideClues?: OutsideClues;
  elapsedMillis: number;
  moves: number;
  mistakes: number;
  hintsUsed: number;
}

export interface SudokuTopology {
  width: number;
  height: number;
  active: boolean[];
  activeIndices: number[];
  units: number[][];
}

export interface ValidatedCompletion {
  request: CompletionRequest;
  topology: SudokuTopology;
  emptyCells: number;
  effectiveDifficulty: Difficulty;
  points: number;
}

const GRID_BOXES: Record<number, [number, number]> = {
  9: [3, 3],
  12: [3, 4],
  15: [3, 5],
  18: [3, 6],
};
const DIFFICULTIES: Difficulty[] = ["EASY", "MEDIUM", "HARD"];
const VARIANTS: Variant[] = ["SAMURAI", "FLOWER", "OUTSIDE"];
const VARIANT_TARGET_GIVENS: Record<Variant, Record<Difficulty, number>> = {
  SAMURAI: { EASY: 190, MEDIUM: 165, HARD: 145 },
  FLOWER: { EASY: 105, MEDIUM: 92, HARD: 82 },
  OUTSIDE: { EASY: 30, MEDIUM: 25, HARD: 20 },
};
const ALLOWED_KEYS = new Set([
  "kind", "variant", "gridSize", "difficulty", "dailyDate", "givens",
  "solutionCells", "comboCells", "outsideClues", "elapsedMillis", "moves",
  "mistakes", "hintsUsed",
]);

function invalid(message: string): never {
  throw new HttpsError("invalid-argument", message);
}

function failed(message: string): never {
  throw new HttpsError("failed-precondition", message);
}

export function requireAuthenticated(auth: unknown): asserts auth {
  if (!auth) throw new HttpsError("unauthenticated", "Sign-in is required.");
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function isInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value);
}

function checkOutsideClues(value: unknown): OutsideClues {
  if (!isRecord(value) ||
      Object.keys(value).some((key) => ![
        "rowsFromLeft", "rowsFromRight", "columnsFromTop", "columnsFromBottom",
      ].includes(key))) {
    invalid("outsideClues has an invalid shape.");
  }
  const fields = ["rowsFromLeft", "rowsFromRight", "columnsFromTop", "columnsFromBottom"] as const;
  const clues = {} as OutsideClues;
  for (const field of fields) {
    const lines = value[field];
    if (!Array.isArray(lines) || lines.length !== 9) invalid(`outsideClues.${field} must have 9 lines.`);
    const normalized: number[][] = [];
    for (const line of lines) {
      if (!Array.isArray(line) || line.some((digit) => !isInteger(digit) || digit < 1 || digit > 9)) {
        invalid(`outsideClues.${field} contains an invalid clue.`);
      }
      if (new Set(line).size !== line.length) invalid(`outsideClues.${field} clues must be unique.`);
      normalized.push([...line] as number[]);
    }
    clues[field] = normalized;
  }
  return clues;
}

function shapeRequest(data: unknown): { request: CompletionRequest; topology: SudokuTopology } {
  if (!isRecord(data)) invalid("Completion payload must be an object.");
  if (Object.keys(data).some((key) => !ALLOWED_KEYS.has(key))) invalid("Completion payload has unknown fields.");
  if (data.kind !== "CLASSIC" && data.kind !== "DAILY" && data.kind !== "VARIANT") {
    invalid("kind is invalid.");
  }
  if (!isInteger(data.gridSize) || !(data.gridSize in GRID_BOXES)) invalid("gridSize is invalid.");
  if (typeof data.difficulty !== "string" || !DIFFICULTIES.includes(data.difficulty as Difficulty)) {
    invalid("difficulty is invalid.");
  }
  const kind = data.kind as CompletionKind;
  const gridSize = data.gridSize as CompletionRequest["gridSize"];
  const difficulty = data.difficulty as Difficulty;
  let variant: Variant | undefined;
  if (kind === "VARIANT") {
    if (typeof data.variant !== "string" || !VARIANTS.includes(data.variant as Variant)) {
      invalid("variant is required and must be supported.");
    }
    variant = data.variant as Variant;
    if (gridSize !== 9) invalid("Variants use the 9×9 base grid.");
  } else if (data.variant !== undefined) {
    invalid("variant is only valid for VARIANT completions.");
  }
  if (kind === "DAILY" && (gridSize !== 9 || difficulty !== "MEDIUM")) {
    invalid("Daily completions must be 9×9 MEDIUM.");
  }
  if (kind === "DAILY") {
    const dailyDate = data.dailyDate;
    const parsedDate = typeof dailyDate === "string" && /^\d{4}-\d{2}-\d{2}$/.test(dailyDate)
      ? new Date(`${dailyDate}T00:00:00.000Z`)
      : undefined;
    if (typeof dailyDate !== "string" || !parsedDate || Number.isNaN(parsedDate.getTime()) ||
        parsedDate.toISOString().slice(0, 10) !== dailyDate) {
      invalid("dailyDate must be a valid yyyy-MM-dd date.");
    }
  } else if (data.dailyDate !== undefined) {
    invalid("dailyDate is only valid for DAILY completions.");
  }
  if (variant !== "OUTSIDE" && data.outsideClues !== undefined) {
    invalid("outsideClues is only valid for OUTSIDE.");
  }
  if (kind === "VARIANT" && variant === "OUTSIDE" && data.outsideClues === undefined) {
    invalid("OUTSIDE requires outsideClues.");
  }
  if (!Array.isArray(data.givens) || !Array.isArray(data.solutionCells) ||
      !Array.isArray(data.comboCells)) invalid("givens, solutionCells, and comboCells must be arrays.");
  if (!isInteger(data.elapsedMillis) || data.elapsedMillis < 1 || data.elapsedMillis > 86_400_000) {
    invalid("elapsedMillis is outside the supported range.");
  }
  for (const field of ["moves", "mistakes", "hintsUsed"] as const) {
    if (!isInteger(data[field]) || data[field] < 0) invalid(`${field} must be a non-negative integer.`);
  }

  const topology = createTopology(kind, variant, gridSize);
  const activeCellCount = topology.activeIndices.length;
  const moves = data.moves as number;
  const mistakes = data.mistakes as number;
  const hintsUsed = data.hintsUsed as number;
  if (moves > activeCellCount * 10) invalid("moves exceeds the supported range.");
  if (mistakes > activeCellCount * 10) invalid("mistakes exceeds the supported range.");
  if (hintsUsed > activeCellCount) invalid("hintsUsed exceeds the active cell count.");
  if (data.givens.length !== topology.width * topology.height ||
      data.solutionCells.length !== topology.width * topology.height) {
    invalid("givens and solutionCells must cover the full topology canvas.");
  }
  const n = gridSize;
  for (let index = 0; index < topology.active.length; index++) {
    const given = data.givens[index];
    const solution = data.solutionCells[index];
    if (!isInteger(given) || given < 0 || given > n ||
        (!topology.active[index] && given !== 0)) invalid("givens contains an invalid cell value.");
    if (!isInteger(solution) || solution < 0 || solution > n ||
        (!topology.active[index] && solution !== 0)) invalid("solutionCells contains an invalid cell value.");
  }
  if (data.comboCells.some((index) =>
    !isInteger(index) || index < 0 || index >= topology.active.length) ||
      new Set(data.comboCells).size !== data.comboCells.length) {
    invalid("comboCells contains an invalid or duplicate index.");
  }
  const outsideClues = variant === "OUTSIDE" ? checkOutsideClues(data.outsideClues) : undefined;
  const request: CompletionRequest = {
    kind,
    ...(variant ? { variant } : {}),
    gridSize,
    difficulty,
    ...(typeof data.dailyDate === "string" ? { dailyDate: data.dailyDate } : {}),
    givens: [...data.givens] as number[],
    solutionCells: [...data.solutionCells] as number[],
    comboCells: [...data.comboCells] as number[],
    ...(outsideClues ? { outsideClues } : {}),
    elapsedMillis: data.elapsedMillis as number,
    moves: data.moves as number,
    mistakes: data.mistakes as number,
    hintsUsed: data.hintsUsed as number,
  };
  return { request, topology };
}

function uniqueUnits(units: number[][]): number[][] {
  const seen = new Set<string>();
  return units.filter((unit) => {
    const key = [...unit].sort((a, b) => a - b).join(",");
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

export function createTopology(
  kind: CompletionKind,
  variant: Variant | undefined,
  gridSize: CompletionRequest["gridSize"],
): SudokuTopology {
  let width: number = gridSize;
  let height: number = gridSize;
  let offsets: [number, number][] = [[0, 0]];
  let size = gridSize;
  let [boxRows, boxCols] = GRID_BOXES[gridSize];
  if (kind === "VARIANT") {
    size = 9;
    boxRows = 3;
    boxCols = 3;
    if (variant === "SAMURAI") {
      width = 21;
      height = 21;
      offsets = [[0, 0], [0, 12], [6, 6], [12, 0], [12, 12]];
    } else if (variant === "FLOWER") {
      width = 15;
      height = 15;
      offsets = [[0, 3], [3, 0], [3, 3], [3, 6], [6, 3]];
    }
  }
  const active = Array<boolean>(width * height).fill(false);
  const rows: number[][] = [];
  const columns: number[][] = [];
  const boxes: number[][] = [];
  for (const [top, left] of offsets) {
    for (let row = 0; row < size; row++) {
      const rowCells: number[] = [];
      const columnCells: number[] = [];
      for (let col = 0; col < size; col++) {
        const rowIndex = (top + row) * width + left + col;
        const colIndex = (top + col) * width + left + row;
        rowCells.push(rowIndex);
        columnCells.push(colIndex);
        active[rowIndex] = true;
      }
      rows.push(rowCells);
      columns.push(columnCells);
    }
    for (let row = 0; row < size; row += boxRows) {
      for (let col = 0; col < size; col += boxCols) {
        const cells: number[] = [];
        for (let r = 0; r < boxRows; r++) {
          for (let c = 0; c < boxCols; c++) {
            cells.push((top + row + r) * width + left + col + c);
          }
        }
        boxes.push(cells);
      }
    }
  }
  return {
    width,
    height,
    active,
    activeIndices: active.flatMap((isActive, index) => isActive ? [index] : []),
    units: [...uniqueUnits(rows), ...uniqueUnits(columns), ...uniqueUnits(boxes)],
  };
}

function validateSolution(request: CompletionRequest, topology: SudokuTopology): void {
  for (const index of topology.activeIndices) {
    if (request.solutionCells[index] === 0) failed("Every active solution cell must be filled.");
  }
  for (const unit of topology.units) {
    const values = unit.map((index) => request.solutionCells[index]);
    if (new Set(values).size !== values.length) failed("The solution has duplicate values in a Sudoku unit.");
  }
  for (const index of topology.activeIndices) {
    if (request.givens[index] !== 0 && request.givens[index] !== request.solutionCells[index]) {
      failed("A given does not match the submitted solution.");
    }
  }
  if (request.variant === "OUTSIDE" && request.outsideClues) {
    const { rowsFromLeft, rowsFromRight, columnsFromTop, columnsFromBottom } = request.outsideClues;
    for (let line = 0; line < 9; line++) {
      const row = (col: number) => request.solutionCells[line * 9 + col];
      const column = (rowIndex: number) => request.solutionCells[rowIndex * 9 + line];
      if (rowsFromLeft[line].some((clue) => ![row(0), row(1), row(2)].includes(clue)) ||
          rowsFromRight[line].some((clue) => ![row(8), row(7), row(6)].includes(clue)) ||
          columnsFromTop[line].some((clue) => ![column(0), column(1), column(2)].includes(clue)) ||
          columnsFromBottom[line].some((clue) => ![column(8), column(7), column(6)].includes(clue))) {
        failed("An Outside clue is not present in the first three cells from its side.");
      }
    }
  }
}

export function minimumEmptyCells(
  request: CompletionRequest,
  topology = createTopology(request.kind, request.variant, request.gridSize),
): number {
  return Math.floor(topology.activeIndices.length * 0.25);
}

function emptyCellFloor(
  request: CompletionRequest,
  difficulty: Difficulty,
  activeCellCount: number,
): number {
  if (difficulty === "EASY") return 0;
  if (request.variant) {
    const targetDifficulty = difficulty === "MEDIUM" ? "EASY" : "MEDIUM";
    return activeCellCount - VARIANT_TARGET_GIVENS[request.variant][targetDifficulty];
  }
  return Math.floor(activeCellCount * (difficulty === "MEDIUM" ? 0.42 : 0.52));
}

export function effectiveDifficulty(
  request: CompletionRequest,
  emptyCells: number,
  topology = createTopology(request.kind, request.variant, request.gridSize),
): Difficulty {
  const activeCellCount = topology.activeIndices.length;
  const allowedDifficulties = DIFFICULTIES.slice(0, DIFFICULTIES.indexOf(request.difficulty) + 1);
  return allowedDifficulties.reverse().find((difficulty) =>
    emptyCells >= emptyCellFloor(request, difficulty, activeCellCount)) ?? "EASY";
}

function validatePlausibility(
  request: CompletionRequest,
  topology: SudokuTopology,
  nowMillis: number,
): number {
  const emptyCells = topology.activeIndices.filter((index) => request.givens[index] === 0).length;
  if (emptyCells < minimumEmptyCells(request, topology)) failed("The puzzle has too few empty cells.");
  if (request.elapsedMillis < emptyCells * 1_000) failed("Completion time is implausibly short.");
  if (request.comboCells.some((index) => !topology.active[index] || request.givens[index] !== 0)) {
    failed("Combo cells must be non-given active cells.");
  }
  if (request.kind === "DAILY") {
    const today = new Date(nowMillis).toISOString().slice(0, 10);
    const yesterday = new Date(Date.UTC(
      new Date(nowMillis).getUTCFullYear(),
      new Date(nowMillis).getUTCMonth(),
      new Date(nowMillis).getUTCDate() - 1,
    )).toISOString().slice(0, 10);
    if (request.dailyDate !== today && request.dailyDate !== yesterday) {
      failed("Daily completion date is outside the accepted UTC window.");
    }
  }
  return emptyCells;
}

export function computePoints(
  request: CompletionRequest,
  emptyCells: number,
  difficulty: Difficulty,
): number {
  const base = request.variant === "SAMURAI" ? 30
    : request.variant === "FLOWER" ? 25
      : request.variant === "OUTSIDE" ? 15
        : { 9: 10, 12: 15, 15: 20, 18: 25 }[request.gridSize];
  const correct = Math.max(0, emptyCells - request.hintsUsed);
  const comboBonus = request.comboCells.length * base;
  const mistakePenalty = request.mistakes * 20;
  const secondsPerCell = { EASY: 12, MEDIUM: 16, HARD: 20 }[difficulty];
  const targetTimeMillis = emptyCells * secondsPerCell * 1_000;
  const remaining = targetTimeMillis - request.elapsedMillis;
  const timeBonus = remaining <= 0 || targetTimeMillis <= 0 ? 0
    : Math.round(base * emptyCells * remaining / targetTimeMillis);
  const multiplier = { EASY: 1, MEDIUM: 1.5, HARD: 2 }[difficulty];
  return Math.max(0, Math.round(
    (correct * base + comboBonus - mistakePenalty + timeBonus) * multiplier,
  ));
}

export function validateCompletion(data: unknown, nowMillis = Date.now()): ValidatedCompletion {
  const { request, topology } = shapeRequest(data);
  validateSolution(request, topology);
  const emptyCells = validatePlausibility(request, topology, nowMillis);
  const difficulty = effectiveDifficulty(request, emptyCells, topology);
  return {
    request,
    topology,
    emptyCells,
    effectiveDifficulty: difficulty,
    points: computePoints(request, emptyCells, difficulty),
  };
}

export function completionDocumentId(request: CompletionRequest): string {
  const key = `${request.kind}|${request.variant ?? ""}|${request.gridSize}|${request.givens.join(",")}`;
  return createHash("sha256").update(key).digest("hex");
}

export function dailyCompletionDocumentId(request: CompletionRequest): string | undefined {
  return request.kind === "DAILY" ? request.dailyDate : undefined;
}
