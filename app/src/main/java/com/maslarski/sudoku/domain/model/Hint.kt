package com.maslarski.sudoku.domain.model

/** A suggested move plus the logical reasoning behind it. */
sealed interface Hint {
    val index: Int
    val value: Int
    /** Cells that support the reasoning and should be highlighted alongside the target. */
    val relatedIndices: List<Int>

    /** The cell has exactly one candidate left. */
    data class NakedSingle(
        override val index: Int,
        override val value: Int,
        override val relatedIndices: List<Int>,
    ) : Hint

    /** The value fits in only one cell of a row/column/box. */
    data class HiddenSingle(
        override val index: Int,
        override val value: Int,
        val unit: Unit,
        override val relatedIndices: List<Int>,
    ) : Hint {
        enum class Unit { ROW, COLUMN, BOX }
    }

    /** A previously entered value is wrong and must be corrected first. */
    data class FixMistake(
        override val index: Int,
        override val value: Int,
        val wrongValue: Int,
    ) : Hint {
        override val relatedIndices: List<Int> get() = emptyList()
    }

    /** No single-step logic applies; reveal a cell from the solution. */
    data class Reveal(
        override val index: Int,
        override val value: Int,
    ) : Hint {
        override val relatedIndices: List<Int> get() = emptyList()
    }

    /** Puzzle already complete. */
    data object None : Hint {
        override val index: Int get() = -1
        override val value: Int get() = 0
        override val relatedIndices: List<Int> get() = emptyList()
    }
}
