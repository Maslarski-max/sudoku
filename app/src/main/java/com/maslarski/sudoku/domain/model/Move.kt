package com.maslarski.sudoku.domain.model

/** A reversible board mutation recorded on the undo stack. */
data class Move(
    val index: Int,
    val before: Cell,
    val after: Cell,
    val costLife: Boolean = false,
)
