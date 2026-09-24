package com.maslarski.sudoku.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.maslarski.sudoku.R
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GridSize

@Composable
fun difficultyLabel(difficulty: Difficulty): String = when (difficulty) {
    Difficulty.EASY -> stringResource(R.string.difficulty_easy)
    Difficulty.MEDIUM -> stringResource(R.string.difficulty_medium)
    Difficulty.HARD -> stringResource(R.string.difficulty_hard)
}

@Composable
fun gridSizeLabel(gridSize: GridSize): String = stringResource(R.string.grid_size_label, gridSize.size)

@Composable
fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) stringResource(R.string.time_format_hours, hours, minutes, seconds)
    else stringResource(R.string.time_format, minutes, seconds)
}

@Composable
fun DurationText(millis: Long, modifier: Modifier = Modifier) {
    Text(text = formatDuration(millis), modifier = modifier)
}
