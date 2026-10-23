package com.maslarski.sudoku.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.maslarski.sudoku.domain.model.AppTheme

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E4FA3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001A42),
    secondary = Color(0xFF0D47A1),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE6F7),
    onSecondaryContainer = Color(0xFF0A2A5E),
    error = Color(0xFFB00020),
    background = Color(0xFFF4F4F1),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFAFAF8),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF424242),
    surfaceTint = Color(0xFF1E4FA3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F5),
    surfaceContainer = Color(0xFFF0F0EE),
    surfaceContainerHigh = Color(0xFFEAEAE8),
    surfaceContainerHighest = Color(0xFFE4E4E2),
    outline = Color(0xFF757575),
    outlineVariant = Color(0xFFBDBDBD),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBFC6DC),
    secondaryContainer = Color(0xFF3F4759),
    tertiary = Color(0xFFDEBCDF),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF1B1B1F),
    surface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFF44474F),
)

/** Colours specific to the board that Material 3 has no slot for. */
data class BoardColors(
    val cellBackground: Color,
    val givenText: Color,
    val enteredText: Color,
    val errorText: Color,
    val noteText: Color,
    val selected: Color,
    val related: Color,
    val sameValue: Color,
    val hintTarget: Color,
    val hintRelated: Color,
    val thinLine: Color,
    val thickLine: Color,
)

private val LightBoardColors = BoardColors(
    cellBackground = Color(0xFFFFFFFF),
    givenText = Color(0xFF000000),
    enteredText = Color(0xFF0D3A8A),
    errorText = Color(0xFFB00020),
    noteText = Color(0xFF424242),
    selected = Color(0xFFB3D4FF),
    related = Color(0xFFEDEDED),
    sameValue = Color(0xFFCFE0FF),
    hintTarget = Color(0xFFFFD54F),
    hintRelated = Color(0xFFFFF3C4),
    thinLine = Color(0xFF9E9E9E),
    thickLine = Color(0xFF000000),
)

private val DarkBoardColors = BoardColors(
    cellBackground = Color(0xFF23242A),
    givenText = Color(0xFFE4E2E6),
    enteredText = Color(0xFFADC6FF),
    errorText = Color(0xFFFFB4AB),
    noteText = Color(0xFF9AA0AE),
    selected = Color(0xFF365A9C),
    related = Color(0xFF2B3140),
    sameValue = Color(0xFF32456B),
    hintTarget = Color(0xFF7A5F00),
    hintRelated = Color(0xFF4A3F1A),
    thinLine = Color(0xFF3F4350),
    thickLine = Color(0xFFB8BCC8),
)

data class ThemeSwatchColors(val background: Color, val primary: Color, val secondary: Color)

private data class PremiumPalette(val scheme: ColorScheme, val board: BoardColors)

private val CyberpunkPalette = PremiumPalette(
    scheme = darkColorScheme(
        primary = Color(0xFFFF2BD6),
        onPrimary = Color(0xFF2B0024),
        primaryContainer = Color(0xFF5C0F52),
        onPrimaryContainer = Color(0xFFFFD6F5),
        secondary = Color(0xFF00E5FF),
        onSecondary = Color(0xFF00363D),
        secondaryContainer = Color(0xFF004F58),
        onSecondaryContainer = Color(0xFFB8F6FF),
        tertiary = Color(0xFF00E5FF),
        onTertiary = Color(0xFF00363D),
        tertiaryContainer = Color(0xFF004F58),
        onTertiaryContainer = Color(0xFFB8F6FF),
        error = Color(0xFFFF6B8B),
        background = Color(0xFF14141B),
        onBackground = Color(0xFFECEAF4),
        surface = Color(0xFF1C1C26),
        onSurface = Color(0xFFECEAF4),
        surfaceVariant = Color(0xFF2E2E3D),
        onSurfaceVariant = Color(0xFFC2C0D0),
        surfaceTint = Color(0xFFFF2BD6),
        inverseSurface = Color(0xFFECEAF4),
        inverseOnSurface = Color(0xFF1C1C26),
        inversePrimary = Color(0xFF5C0F52),
        surfaceDim = Color(0xFF14141B),
        surfaceBright = Color(0xFF2E2E3D),
        surfaceContainerLowest = Color(0xFF14141B),
        surfaceContainerLow = Color(0xFF1C1C26),
        surfaceContainer = Color(0xFF1C1C26),
        surfaceContainerHigh = Color(0xFF2E2E3D),
        surfaceContainerHighest = Color(0xFF2E2E3D),
        outline = Color(0xFFC2C0D0),
        outlineVariant = Color(0xFF33334A),
    ),
    board = BoardColors(
        cellBackground = Color(0xFF1C1C26),
        givenText = Color(0xFFECEAF4),
        enteredText = Color(0xFF00E5FF),
        errorText = Color(0xFFFF6B8B),
        noteText = Color(0xFF9C9AB3),
        selected = Color(0xFF5C0F52),
        related = Color(0xFF24243A),
        sameValue = Color(0xFF3A1F48),
        hintTarget = Color(0xFF6B5A00),
        hintRelated = Color(0xFF2F2A10),
        thinLine = Color(0xFF33334A),
        thickLine = Color(0xFFFF2BD6),
    ),
)

private val RetroPalette = PremiumPalette(
    scheme = darkColorScheme(
        primary = Color(0xFF00FF41),
        onPrimary = Color(0xFF002108),
        primaryContainer = Color(0xFF00521A),
        onPrimaryContainer = Color(0xFFB6FFC6),
        secondary = Color(0xFF7CFF9B),
        onSecondary = Color(0xFF00210A),
        secondaryContainer = Color(0xFF0D3B18),
        onSecondaryContainer = Color(0xFFB6FFC6),
        tertiary = Color(0xFF7CFF9B),
        onTertiary = Color(0xFF00210A),
        tertiaryContainer = Color(0xFF0D3B18),
        onTertiaryContainer = Color(0xFFB6FFC6),
        error = Color(0xFFFF5C5C),
        background = Color(0xFF000000),
        onBackground = Color(0xFF00FF41),
        surface = Color(0xFF000000),
        onSurface = Color(0xFF00FF41),
        surfaceVariant = Color(0xFF0F1F12),
        onSurfaceVariant = Color(0xFF5FD27A),
        surfaceTint = Color(0xFF00FF41),
        inverseSurface = Color(0xFF00FF41),
        inverseOnSurface = Color(0xFF000000),
        inversePrimary = Color(0xFF00521A),
        surfaceDim = Color(0xFF000000),
        surfaceBright = Color(0xFF0F1F12),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF000000),
        surfaceContainer = Color(0xFF000000),
        surfaceContainerHigh = Color(0xFF0F1F12),
        surfaceContainerHighest = Color(0xFF0F1F12),
        outline = Color(0xFF5FD27A),
        outlineVariant = Color(0xFF0F3A1A),
    ),
    board = BoardColors(
        cellBackground = Color(0xFF000000),
        givenText = Color(0xFF00FF41),
        enteredText = Color(0xFFB6FFC6),
        errorText = Color(0xFFFF5C5C),
        noteText = Color(0xFF2FA84F),
        selected = Color(0xFF00521A),
        related = Color(0xFF071A0C),
        sameValue = Color(0xFF0D3B18),
        hintTarget = Color(0xFF4D4D00),
        hintRelated = Color(0xFF1A1A00),
        thinLine = Color(0xFF0F3A1A),
        thickLine = Color(0xFF00FF41),
    ),
)

private val ZenPalette = PremiumPalette(
    scheme = darkColorScheme(
        primary = Color(0xFF7FD8A6),
        onPrimary = Color(0xFF003921),
        primaryContainer = Color(0xFF1F5A40),
        onPrimaryContainer = Color(0xFFC8F2DA),
        secondary = Color(0xFFB8D8C2),
        onSecondary = Color(0xFF23362B),
        secondaryContainer = Color(0xFF2E4D3C),
        onSecondaryContainer = Color(0xFFD4F0DD),
        tertiary = Color(0xFFB8D8C2),
        onTertiary = Color(0xFF23362B),
        tertiaryContainer = Color(0xFF2E4D3C),
        onTertiaryContainer = Color(0xFFD4F0DD),
        error = Color(0xFFFFB4AB),
        background = Color(0xFF0B2A1F),
        onBackground = Color(0xFFE3F2E9),
        surface = Color(0xFF0F3326),
        onSurface = Color(0xFFE3F2E9),
        surfaceVariant = Color(0xFF1E4A3A),
        onSurfaceVariant = Color(0xFFB5CCBE),
        surfaceTint = Color(0xFF7FD8A6),
        inverseSurface = Color(0xFFE3F2E9),
        inverseOnSurface = Color(0xFF0F3326),
        inversePrimary = Color(0xFF1F5A40),
        surfaceDim = Color(0xFF0B2A1F),
        surfaceBright = Color(0xFF1E4A3A),
        surfaceContainerLowest = Color(0xFF0B2A1F),
        surfaceContainerLow = Color(0xFF0F3326),
        surfaceContainer = Color(0xFF0F3326),
        surfaceContainerHigh = Color(0xFF1E4A3A),
        surfaceContainerHighest = Color(0xFF1E4A3A),
        outline = Color(0xFFB5CCBE),
        outlineVariant = Color(0xFF2A5444),
    ),
    board = BoardColors(
        cellBackground = Color(0xFF0F3326),
        givenText = Color(0xFFE3F2E9),
        enteredText = Color(0xFF7FD8A6),
        errorText = Color(0xFFFFB4AB),
        noteText = Color(0xFF8FB3A0),
        selected = Color(0xFF1F5A40),
        related = Color(0xFF143D2E),
        sameValue = Color(0xFF245241),
        hintTarget = Color(0xFF5E5410),
        hintRelated = Color(0xFF2A3A1E),
        thinLine = Color(0xFF2A5444),
        thickLine = Color(0xFFB8D8C2),
    ),
)

fun themeSwatchColors(theme: AppTheme): ThemeSwatchColors? = when (theme) {
    AppTheme.DEFAULT -> null
    AppTheme.CYBERPUNK_NEON -> ThemeSwatchColors(Color(0xFF14141B), Color(0xFFFF2BD6), Color(0xFF00E5FF))
    AppTheme.RETRO_ARCADE -> ThemeSwatchColors(Color(0xFF000000), Color(0xFF00FF41), Color(0xFF7CFF9B))
    AppTheme.ZEN_FOREST -> ThemeSwatchColors(Color(0xFF0B2A1F), Color(0xFF7FD8A6), Color(0xFFB8D8C2))
}

val LocalBoardColors = staticCompositionLocalOf { LightBoardColors }

@Composable
fun SudokuTheme(
    appTheme: AppTheme = AppTheme.DEFAULT,
    darkMode: Boolean = true,
    content: @Composable () -> Unit,
) {
    val premiumPalette = when (appTheme) {
        AppTheme.DEFAULT -> null
        AppTheme.CYBERPUNK_NEON -> CyberpunkPalette
        AppTheme.RETRO_ARCADE -> RetroPalette
        AppTheme.ZEN_FOREST -> ZenPalette
    }
    val colorScheme = premiumPalette?.scheme ?: if (darkMode) DarkColors else LightColors
    val boardColors = premiumPalette?.board ?: if (darkMode) DarkBoardColors else LightBoardColors

    androidx.compose.runtime.CompositionLocalProvider(LocalBoardColors provides boardColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
