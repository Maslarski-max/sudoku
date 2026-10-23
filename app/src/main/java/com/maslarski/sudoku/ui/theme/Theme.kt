package com.maslarski.sudoku.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.maslarski.sudoku.domain.model.AppTheme

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E4FA3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A42),
    secondary = Color(0xFF575E71),
    secondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFF715573),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFFEFBFF),
    surface = Color(0xFFFEFBFF),
    surfaceVariant = Color(0xFFE1E2EC),
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
    cellBackground = Color.White,
    givenText = Color(0xFF1B1B1F),
    enteredText = Color(0xFF1E4FA3),
    errorText = Color(0xFFBA1A1A),
    noteText = Color(0xFF6B7280),
    selected = Color(0xFFBBD0FF),
    related = Color(0xFFEAF0FF),
    sameValue = Color(0xFFD5E0FF),
    hintTarget = Color(0xFFFFE08A),
    hintRelated = Color(0xFFFFF4D0),
    thinLine = Color(0xFFC5C9D6),
    thickLine = Color(0xFF2E3440),
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
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val premiumPalette = when (appTheme) {
        AppTheme.DEFAULT -> null
        AppTheme.CYBERPUNK_NEON -> CyberpunkPalette
        AppTheme.RETRO_ARCADE -> RetroPalette
        AppTheme.ZEN_FOREST -> ZenPalette
    }
    val colorScheme = premiumPalette?.scheme ?: when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val boardColors = premiumPalette?.board ?: if (darkTheme) DarkBoardColors else LightBoardColors

    androidx.compose.runtime.CompositionLocalProvider(LocalBoardColors provides boardColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
