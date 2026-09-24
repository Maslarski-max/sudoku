package com.maslarski.sudoku.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maslarski.sudoku.R
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.repository.SavedGameSummary
import com.maslarski.sudoku.ui.components.difficultyLabel
import com.maslarski.sudoku.ui.components.formatDuration
import com.maslarski.sudoku.ui.components.gridSizeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenGame: (GridSize) -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenStore: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigateToGame.collect(onOpenGame)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onOpenLeaderboard) {
                        Icon(Icons.Default.Leaderboard, contentDescription = stringResource(R.string.nav_leaderboard))
                    }
                    IconButton(onClick = onOpenStore) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = stringResource(R.string.nav_store))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyLarge)
            }
            item {
                Text(stringResource(R.string.home_grid_size), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    GridSize.entries.forEachIndexed { i, g ->
                        SegmentedButton(
                            selected = state.gridSize == g,
                            onClick = { viewModel.selectGridSize(g) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = GridSize.entries.size),
                            label = { Text(gridSizeLabel(g)) },
                        )
                    }
                }
            }
            item {
                Text(stringResource(R.string.home_difficulty), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Difficulty.entries.forEach { d ->
                        FilterChip(
                            selected = state.difficulty == d,
                            onClick = { viewModel.selectDifficulty(d) },
                            label = { Text(difficultyLabel(d)) },
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = viewModel::onNewGameClicked,
                    enabled = !state.generating,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.generating) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.home_generating, state.gridSize.size))
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_new_game))
                    }
                }
                if (state.generating && state.gridSize.size > 9) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.home_generating_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                val lives = state.lives
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            lives == null -> ""
                            lives.unlimited -> stringResource(R.string.lives_unlimited)
                            else -> stringResource(R.string.lives_remaining, lives.count)
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.home_ad_free),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item {
                Text(stringResource(R.string.home_saved_games), style = MaterialTheme.typography.titleMedium)
            }
            if (state.savedGames.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.home_no_saved_games),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.savedGames, key = { it.gridSize.name }) { saved ->
                    SavedGameCard(saved, onClick = { viewModel.continueGame(saved.gridSize) })
                }
            }
        }
    }

    if (state.confirmReplace) {
        AlertDialog(
            onDismissRequest = viewModel::dismissReplace,
            title = { Text(stringResource(R.string.home_replace_saved_title)) },
            text = { Text(stringResource(R.string.home_replace_saved_message, state.gridSize.size)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmReplace) { Text(stringResource(R.string.home_replace_saved_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissReplace) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SavedGameCard(saved: SavedGameSummary, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            R.string.home_continue_description,
                            gridSizeLabel(saved.gridSize),
                            difficultyLabel(saved.difficulty),
                            formatDuration(saved.elapsedMillis),
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                TextButton(onClick = onClick) { Text(stringResource(R.string.home_continue)) }
            }
            LinearProgressIndicator(
                progress = { saved.filledFraction },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
