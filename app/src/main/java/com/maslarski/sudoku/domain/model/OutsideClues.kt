package com.maslarski.sudoku.domain.model

/** Clue digits shown just outside each edge of an Outside Sudoku. */
data class OutsideClues(
    val rowsFromLeft: List<List<Int>> = emptyList(),
    val rowsFromRight: List<List<Int>> = emptyList(),
    val columnsFromTop: List<List<Int>> = emptyList(),
    val columnsFromBottom: List<List<Int>> = emptyList(),
) {
    init {
        listOf(rowsFromLeft, rowsFromRight, columnsFromTop, columnsFromBottom)
            .flatten()
            .flatten()
            .forEach { require(it in 1..9) }
    }

    fun exclusionMasks(topology: Topology): IntArray {
        val masks = IntArray(topology.cellCount)
        val width = topology.width
        for (row in 0 until 9) {
            val left = rowsFromLeft.getOrNull(row).orEmpty()
            val right = rowsFromRight.getOrNull(row).orEmpty()
            for (offset in 3 until 9) {
                if (left.isNotEmpty()) masks[row * width + offset] = masks[row * width + offset] or left.toMask()
                if (right.isNotEmpty()) {
                    val index = row * width + (8 - offset)
                    masks[index] = masks[index] or right.toMask()
                }
            }
        }
        for (column in 0 until 9) {
            val top = columnsFromTop.getOrNull(column).orEmpty()
            val bottom = columnsFromBottom.getOrNull(column).orEmpty()
            for (offset in 3 until 9) {
                if (top.isNotEmpty()) {
                    val index = offset * width + column
                    masks[index] = masks[index] or top.toMask()
                }
                if (bottom.isNotEmpty()) {
                    val index = (8 - offset) * width + column
                    masks[index] = masks[index] or bottom.toMask()
                }
            }
        }
        return masks
    }

    private fun List<Int>.toMask() = fold(0) { mask, value -> mask or (1 shl (value - 1)) }
}
