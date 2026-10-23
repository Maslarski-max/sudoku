package com.maslarski.sudoku.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeTest {
    @Test
    fun `premium themes require unlimited lives`() {
        AppTheme.entries.filter { it.isPremium }.forEach { theme ->
            assertEquals(AppTheme.DEFAULT, theme.effective(unlimited = false))
            assertEquals(theme, theme.effective(unlimited = true))
        }
    }

    @Test
    fun `default theme is never gated`() {
        assertEquals(AppTheme.DEFAULT, AppTheme.DEFAULT.effective(unlimited = false))
        assertEquals(AppTheme.DEFAULT, AppTheme.DEFAULT.effective(unlimited = true))
    }
}
