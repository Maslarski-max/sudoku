package com.maslarski.sudoku.domain.model

/**
 * Player-visible state of one cell.
 *
 * @param value entered or given value, 0 when empty.
 * @param notes bitmask of pencil marks; bit (n - 1) set means candidate n is noted.
 */
data class Cell(
    val value: Int = 0,
    val isGiven: Boolean = false,
    val notes: Int = 0,
    val isError: Boolean = false,
) {
    val isEmpty: Boolean get() = value == 0

    fun hasNote(n: Int): Boolean = notes and (1 shl (n - 1)) != 0

    fun toggleNote(n: Int): Cell = copy(notes = notes xor (1 shl (n - 1)))
}
