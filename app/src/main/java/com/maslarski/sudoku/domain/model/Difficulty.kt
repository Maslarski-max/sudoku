package com.maslarski.sudoku.domain.model

/** Difficulty controls the fraction of cells removed from the solved grid. */
enum class Difficulty(val emptyFraction: Double) {
    EASY(0.42),
    MEDIUM(0.52),
    HARD(0.62),
}
