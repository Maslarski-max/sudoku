package com.maslarski.sudoku.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

val LocalBoardColors = staticCompositionLocalOf { LightBoardColors }

@Composable
fun SudokuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val boardColors = if (darkTheme) DarkBoardColors else LightBoardColors

    androidx.compose.runtime.CompositionLocalProvider(LocalBoardColors provides boardColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
