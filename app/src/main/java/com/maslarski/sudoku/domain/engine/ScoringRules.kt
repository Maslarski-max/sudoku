package com.maslarski.sudoku.domain.engine

import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import kotlin.math.roundToInt

/** Itemised score for a game; [total] is what gets ranked on the leaderboard. */
data class ScoreBreakdown(
    val correctEntries: Int,
    val basePoints: Int,
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
 * penalty   = mistakes × MISTAKE_PENALTY
 * bonus     = only when complete and under targetTime: basePoints × emptyCells × timeLeft / targetTime
 * total     = max(0, (entries − penalty + bonus) × multiplier(difficulty))
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

    fun multiplier(difficulty: Difficulty): Double = when (difficulty) {
        Difficulty.EASY -> 1.0
        Difficulty.MEDIUM -> 1.5
        Difficulty.HARD -> 2.0
    }

    /** Seconds budgeted per empty cell; the target time scales with the amount of work in the puzzle. */
    private fun secondsPerCell(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 12
        Difficulty.MEDIUM -> 16
        Difficulty.HARD -> 20
    }

    fun targetTimeMillis(state: GameState): Long =
        emptyCells(state).toLong() * secondsPerCell(state.difficulty) * 1_000L

    fun breakdown(state: GameState): ScoreBreakdown {
        val base = basePoints(state.gridSize)
        val correct = (correctPlayerCells(state) - state.hintsUsed).coerceAtLeast(0)
        val penalty = state.mistakes * MISTAKE_PENALTY
        val bonus = if (state.isComplete) timeBonus(state, base) else 0
        val multiplier = multiplier(state.difficulty)
        val raw = correct * base - penalty + bonus
        return ScoreBreakdown(
            correctEntries = correct,
            basePoints = correct * base,
            mistakePenalty = penalty,
            timeBonus = bonus,
            multiplier = multiplier,
            total = (raw * multiplier).roundToInt().coerceAtLeast(0),
        )
    }

    /** Score as it stands right now; shown live during play and equal to the final score on completion. */
    fun points(state: GameState): Int = breakdown(state).total

    private fun timeBonus(state: GameState, base: Int): Int {
        val target = targetTimeMillis(state)
        val remaining = target - state.elapsedMillis
        if (target <= 0L || remaining <= 0L) return 0
        val maxBonus = base * emptyCells(state)
        return (maxBonus * remaining.toDouble() / target).roundToInt()
    }

    private fun emptyCells(state: GameState): Int = state.puzzle.givens.count { it == 0 }

    private fun correctPlayerCells(state: GameState): Int =
        state.cells.indices.count { i ->
            val c = state.cells[i]
            !c.isGiven && c.value != 0 && c.value == state.puzzle.solution[i]
        }
}
