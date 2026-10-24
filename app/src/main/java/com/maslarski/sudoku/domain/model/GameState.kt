package com.maslarski.sudoku.domain.model

/**
 * Complete state of a game in progress. Pure data so it can be persisted and restored verbatim.
 */
data class GameState(
    val puzzle: Puzzle,
    val cells: List<Cell>,
    val undoStack: List<Move> = emptyList(),
    val redoStack: List<Move> = emptyList(),
    val elapsedMillis: Long = 0L,
    val moves: Int = 0,
    val mistakes: Int = 0,
    /** Mistakes since the last life was lost; wraps back to 0 every [GameRules.MISTAKES_PER_LIFE]. */
    val mistakeStreak: Int = 0,
    val hintsUsed: Int = 0,
    val isComplete: Boolean = false,
    val startedAtEpochMillis: Long,
    val lastPlayedEpochMillis: Long = startedAtEpochMillis,
    val outOfLivesDeadlineEpochMillis: Long? = null,
    val outOfLivesExpired: Boolean = false,
    val comboStreak: Int = 0,
    val lastCorrectElapsedMillis: Long? = null,
    val countedCells: Set<Int> = emptySet(),
    val iconMode: Boolean = false,
    val dailyDate: String? = null,
) {
    val gridSize: GridSize get() = puzzle.gridSize
    val difficulty: Difficulty get() = puzzle.difficulty
    val variant: Variant? get() = puzzle.variant
    val topology: Topology get() = puzzle.topology
    val slotId: Int get() = if (dailyDate != null) DailyChallenge.SLOT_ID else variant?.slotId ?: gridSize.size

    fun valuesArray(): IntArray = IntArray(cells.size) { cells[it].value }

    companion object {
        fun fromPuzzle(
            puzzle: Puzzle,
            nowEpochMillis: Long,
            iconMode: Boolean = false,
            dailyDate: String? = null,
        ): GameState = GameState(
            puzzle = puzzle,
            cells = puzzle.givens.map { v -> Cell(value = v, isGiven = v != 0) },
            startedAtEpochMillis = nowEpochMillis,
            iconMode = dailyDate == null && iconMode && puzzle.variant == null &&
                (puzzle.gridSize == GridSize.NINE || puzzle.gridSize == GridSize.TWELVE),
            dailyDate = dailyDate,
        )
    }
}
