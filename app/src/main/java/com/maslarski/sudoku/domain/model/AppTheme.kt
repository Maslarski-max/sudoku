package com.maslarski.sudoku.domain.model

enum class AppTheme {
    DEFAULT,
    CYBERPUNK_NEON,
    RETRO_ARCADE,
    ZEN_FOREST;

    val isPremium: Boolean get() = this != DEFAULT

    fun effective(unlimited: Boolean): AppTheme = if (isPremium && !unlimited) DEFAULT else this
}
