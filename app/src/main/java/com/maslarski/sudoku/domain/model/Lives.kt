package com.maslarski.sudoku.domain.model

data class Lives(
    val count: Int,
    val unlimited: Boolean,
    /** Epoch millis at which the next free life regenerates, or null when at/above the cap or unlimited. */
    val nextRegenAtEpochMillis: Long?,
) {
    val canPlay: Boolean get() = unlimited || count > 0

    companion object {
        const val STARTING_LIVES = 5
        const val REGEN_CAP = 5
        const val REGEN_INTERVAL_MILLIS = 30L * 60L * 1000L
        const val LIVES_PER_PURCHASE = 5
    }
}
