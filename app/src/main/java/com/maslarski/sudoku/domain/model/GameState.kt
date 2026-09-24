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
    val hintsUsed: Int = 0,
    val isComplete: Boolean = false,
    val startedAtEpochMillis: Long,
    val lastPlayedEpochMillis: Long = startedAtEpochMillis,
) {
    val gridSize: GridSize get() = puzzle.gridSize
    val difficulty: Difficulty get() = puzzle.difficulty

    fun valuesArray(): IntArray = IntArray(cells.size) { cells[it].value }

    companion object {
        fun fromPuzzle(puzzle: Puzzle, nowEpochMillis: Long): GameState = GameState(
            puzzle = puzzle,
            cells = puzzle.givens.map { v -> Cell(value = v, isGiven = v != 0) },
            startedAtEpochMillis = nowEpochMillis,
        )
    }
}
