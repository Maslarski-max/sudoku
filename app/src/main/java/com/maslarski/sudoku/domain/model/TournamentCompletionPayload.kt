package com.maslarski.sudoku.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TournamentCompletionPayload(
    val kind: String,
    val gridSize: Int,
    val difficulty: String,
    val givens: List<Int>,
    val solutionCells: List<Int>,
    val comboCells: List<Int>,
    val elapsedMillis: Long,
    val moves: Int,
    val mistakes: Int,
    val hintsUsed: Int,
    val variant: String? = null,
    val dailyDate: String? = null,
    val outsideClues: CompletionOutsideClues? = null,
)

@Serializable
data class CompletionOutsideClues(
    val rowsFromLeft: List<List<Int>>,
    val rowsFromRight: List<List<Int>>,
    val columnsFromTop: List<List<Int>>,
    val columnsFromBottom: List<List<Int>>,
)

fun GameState.toTournamentCompletionPayload(): TournamentCompletionPayload {
    val clues = puzzle.outsideClues?.let {
        fun List<List<Int>>.padded(): List<List<Int>> =
            List(9) { index -> getOrNull(index).orEmpty() }

        CompletionOutsideClues(
            rowsFromLeft = it.rowsFromLeft.padded(),
            rowsFromRight = it.rowsFromRight.padded(),
            columnsFromTop = it.columnsFromTop.padded(),
            columnsFromBottom = it.columnsFromBottom.padded(),
        )
    }
    return TournamentCompletionPayload(
        kind = when {
            dailyDate != null -> "DAILY"
            variant != null -> "VARIANT"
            else -> "CLASSIC"
        },
        gridSize = gridSize.size,
        difficulty = difficulty.name,
        givens = puzzle.givens.toList(),
        solutionCells = puzzle.solution.toList(),
        comboCells = topology.activeIndices.filter { index ->
            val cell = cells[index]
            cell.combo && !cell.isGiven && cell.value == puzzle.solution[index]
        },
        elapsedMillis = elapsedMillis,
        moves = moves,
        mistakes = mistakes,
        hintsUsed = hintsUsed,
        variant = variant?.name,
        dailyDate = dailyDate,
        outsideClues = clues,
    )
}
