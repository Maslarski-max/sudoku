package com.maslarski.sudoku.domain.model

data class AppSettings(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val showCandidates: Boolean = false,
    val theme: AppTheme = AppTheme.DEFAULT,
    val darkMode: Boolean = true,
)
