package com.maslarski.sudoku.domain.model

/**
 * An immutable puzzle definition. [givens] holds 0 for empty cells; [solution] is the unique solution.
 * Both arrays are row-major with length gridSize.cellCount.
 */
data class Puzzle(
    val gridSize: GridSize,
    val difficulty: Difficulty,
    val givens: IntArray,
    val solution: IntArray,
    val seed: Long,
) {
    init {
        require(givens.size == gridSize.cellCount) { "givens must have ${gridSize.cellCount} cells" }
        require(solution.size == gridSize.cellCount) { "solution must have ${gridSize.cellCount} cells" }
    }

    val clueCount: Int get() = givens.count { it != 0 }

    fun isGiven(index: Int): Boolean = givens[index] != 0

    override fun equals(other: Any?): Boolean =
        other is Puzzle && other.gridSize == gridSize && other.difficulty == difficulty &&
            other.seed == seed && other.givens.contentEquals(givens) && other.solution.contentEquals(solution)

    override fun hashCode(): Int = 31 * givens.contentHashCode() + solution.contentHashCode()
}
