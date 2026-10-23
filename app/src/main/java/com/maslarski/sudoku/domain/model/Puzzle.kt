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
    val variant: Variant? = null,
    val outsideClues: OutsideClues? = null,
) {
    val topology: Topology by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Topology.forVariant(variant, gridSize)
    }

    init {
        val expectedCells = Topology.forVariant(variant, gridSize).cellCount
        require(givens.size == expectedCells) { "givens must have $expectedCells cells" }
        require(solution.size == expectedCells) { "solution must have $expectedCells cells" }
    }

    val clueCount: Int get() = topology.activeIndices.count { givens[it] != 0 }

    fun isGiven(index: Int): Boolean = givens[index] != 0

    override fun equals(other: Any?): Boolean =
        other is Puzzle && other.gridSize == gridSize && other.difficulty == difficulty &&
            other.seed == seed && other.variant == variant && other.outsideClues == outsideClues &&
            other.givens.contentEquals(givens) && other.solution.contentEquals(solution)

    override fun hashCode(): Int =
        31 * (31 * givens.contentHashCode() + solution.contentHashCode()) + (variant?.hashCode() ?: 0)
}
