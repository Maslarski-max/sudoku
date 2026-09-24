package com.maslarski.sudoku.domain.model

/**
 * Supported board dimensions. Each grid is divided into [boxRows] x [boxCols] boxes so that
 * boxRows * boxCols == size. Non-square boxes are the standard construction for non-perfect-square sizes.
 */
enum class GridSize(val size: Int, val boxRows: Int, val boxCols: Int) {
    NINE(9, 3, 3),
    TWELVE(12, 3, 4),
    FIFTEEN(15, 3, 5),
    EIGHTEEN(18, 3, 6);

    val cellCount: Int get() = size * size

    fun boxIndex(row: Int, col: Int): Int = (row / boxRows) * (size / boxCols) + (col / boxCols)

    companion object {
        fun fromSize(size: Int): GridSize = entries.first { it.size == size }
    }
}
