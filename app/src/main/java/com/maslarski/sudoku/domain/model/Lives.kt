package com.maslarski.sudoku.domain.model

/** Lives never regenerate; more can only be obtained through the store. */
data class Lives(
    val count: Int,
    val unlimited: Boolean,
) {
    val canPlay: Boolean get() = unlimited || count > 0

    companion object {
        const val STARTING_LIVES = 5
        const val LIVES_PER_PURCHASE = 5
    }
}
