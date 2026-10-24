package com.maslarski.sudoku.domain.engine

import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Variant
import kotlin.math.floor
import kotlin.math.roundToInt

/** Itemised score for a game; [total] is what gets ranked on the leaderboard. */
data class ScoreBreakdown(
    val correctEntries: Int,
    val basePoints: Int,
    val comboBonus: Int,
    val mistakePenalty: Int,
    val timeBonus: Int,
    val multiplier: Double,
    val total: Int,
)

/**
 * Pure scoring rules:
 *
 * ```
 * entries   = correct, player-entered (non-given, non-hint) cells × basePoints(gridSize)
 * combo     = currently correct cells marked combo × basePoints(gridSize)
 * penalty   = mistakes × MISTAKE_PENALTY
 * bonus     = only when complete and under targetTime: basePoints × emptyCells × timeLeft / targetTime
 * total     = max(0, (entries + combo − penalty + bonus) × multiplier(difficulty))
 * ```
 *
 * Everything is derived from [GameState] rather than accumulated, so erasing and re-entering a number,
 * undo/redo, or restoring an autosave can never farm or lose points.
 */
object ScoringRules {
    const val MISTAKE_PENALTY = 20

    fun basePoints(gridSize: GridSize): Int = when (gridSize) {
        GridSize.NINE -> 10
        GridSize.TWELVE -> 15
        GridSize.FIFTEEN -> 20
        GridSize.EIGHTEEN -> 25
    }

    fun basePoints(state: GameState): Int = when (state.variant) {
        Variant.SAMURAI -> 30
        Variant.FLOWER -> 25
        Variant.OUTSIDE -> 15
        null -> basePoints(state.gridSize)
    }

    fun multiplier(difficulty: Difficulty): Double = when (difficulty) {
        Difficulty.EASY -> 1.0
        Difficulty.MEDIUM -> 1.5
        Difficulty.HARD -> 2.0
    }

    fun effectiveDifficulty(
        claimed: Difficulty,
        variant: Variant?,
        activeCellCount: Int,
        emptyCells: Int,
    ): Difficulty {
        val mediumFloor = if (variant == null) {
            floor(activeCellCount * 0.42).toInt()
        } else {
            activeCellCount - targetGivens(variant, Difficulty.EASY)
        }
        val hardFloor = if (variant == null) {
            floor(activeCellCount * 0.52).toInt()
        } else {
            activeCellCount - targetGivens(variant, Difficulty.MEDIUM)
        }
        return when {
            claimed == Difficulty.HARD && emptyCells >= hardFloor -> Difficulty.HARD
            claimed != Difficulty.EASY && emptyCells >= mediumFloor -> Difficulty.MEDIUM
            else -> Difficulty.EASY
        }
    }

    private fun targetGivens(variant: Variant, difficulty: Difficulty): Int = when (variant) {
        Variant.SAMURAI -> when (difficulty) {
            Difficulty.EASY -> 190
            Difficulty.MEDIUM -> 165
            Difficulty.HARD -> 145
        }
        Variant.FLOWER -> when (difficulty) {
            Difficulty.EASY -> 105
            Difficulty.MEDIUM -> 92
            Difficulty.HARD -> 82
        }
        Variant.OUTSIDE -> when (difficulty) {
            Difficulty.EASY -> 30
            Difficulty.MEDIUM -> 25
            Difficulty.HARD -> 20
        }
    }

    /** Seconds budgeted per empty cell; the target time scales with the amount of work in the puzzle. */
    private fun secondsPerCell(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 12
        Difficulty.MEDIUM -> 16
        Difficulty.HARD -> 20
    }

    fun targetTimeMillis(state: GameState): Long {
        val emptyCells = emptyCells(state)
        return targetTimeMillis(emptyCells, effectiveDifficulty(state, emptyCells))
    }

    fun breakdown(state: GameState): ScoreBreakdown {
        val emptyCells = emptyCells(state)
        val scoredDifficulty = effectiveDifficulty(state, emptyCells)
        val base = basePoints(state)
        val correct = (correctPlayerCells(state) - state.hintsUsed).coerceAtLeast(0)
        val comboBonus = state.topology.activeIndices.count { index ->
            val cell = state.cells[index]
            cell.combo && !cell.isGiven && cell.value == state.puzzle.solution[index]
        } * base
        val penalty = state.mistakes * MISTAKE_PENALTY
        val bonus = if (state.isComplete) timeBonus(state, base, emptyCells, scoredDifficulty) else 0
        val multiplier = multiplier(scoredDifficulty)
        val raw = correct * base + comboBonus - penalty + bonus
        return ScoreBreakdown(
            correctEntries = correct,
            basePoints = correct * base,
            comboBonus = comboBonus,
            mistakePenalty = penalty,
            timeBonus = bonus,
            multiplier = multiplier,
            total = (raw * multiplier).roundToInt().coerceAtLeast(0),
        )
    }

    private fun effectiveDifficulty(state: GameState, emptyCells: Int): Difficulty =
        effectiveDifficulty(
            claimed = state.difficulty,
            variant = state.variant,
            activeCellCount = state.topology.activeIndices.size,
            emptyCells = emptyCells,
        )

    private fun targetTimeMillis(emptyCells: Int, difficulty: Difficulty): Long =
        emptyCells.toLong() * secondsPerCell(difficulty) * 1_000L

    /** Score as it stands right now; shown live during play and equal to the final score on completion. */
    fun points(state: GameState): Int = breakdown(state).total

    private fun timeBonus(
        state: GameState,
        base: Int,
        emptyCells: Int,
        difficulty: Difficulty,
    ): Int {
        val target = targetTimeMillis(emptyCells, difficulty)
        val remaining = target - state.elapsedMillis
        if (target <= 0L || remaining <= 0L) return 0
        val maxBonus = base * emptyCells
        return (maxBonus * remaining.toDouble() / target).roundToInt()
    }

    private fun emptyCells(state: GameState): Int =
        state.topology.activeIndices.count { state.puzzle.givens[it] == 0 }

    private fun correctPlayerCells(state: GameState): Int =
        state.topology.activeIndices.count { i ->
            val c = state.cells[i]
            !c.isGiven && c.value != 0 && c.value == state.puzzle.solution[i]
        }
}
