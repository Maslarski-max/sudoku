package com.maslarski.sudoku.domain.model

object GameRules {
    const val OUT_OF_LIVES_COUNTDOWN_SECONDS = 60
    const val COMBO_WINDOW_MILLIS = 10_000L
    const val COMBO_MIN_STREAK = 3

    /** Free hints per game; players with unlimited lives (premium) are not limited. */
    const val MAX_FREE_HINTS = 3

    /** Every this many mistakes costs one life. */
    const val MISTAKES_PER_LIFE = 3

    /** Lives deducted for starting a new game while one of the same grid size is still in progress. */
    const val ABORT_PENALTY_LIVES = 1

    fun hintsRemaining(state: GameState, lives: Lives?): Int? =
        if (lives?.unlimited == true) null else (MAX_FREE_HINTS - state.hintsUsed).coerceAtLeast(0)

    fun canUseHint(state: GameState, lives: Lives?): Boolean = hintsRemaining(state, lives) != 0
}
