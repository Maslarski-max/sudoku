package com.maslarski.sudoku.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maslarski.sudoku.R
import com.maslarski.sudoku.domain.engine.ScoreBreakdown
import com.maslarski.sudoku.domain.engine.ScoringRules
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Hint
import com.maslarski.sudoku.ui.components.difficultyLabel
import com.maslarski.sudoku.ui.components.formatDuration
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    onBack: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenLeaderboard: (GridSize, Difficulty) -> Unit,
    onNewGame: () -> Unit,
    viewModel: GameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val transform = remember { BoardTransform() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.missing) { if (state.missing) onBack() }

    val game = state.game
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (game != null) {
                        Text(stringResource(R.string.game_title, game.gridSize.size, difficultyLabel(game.difficulty)))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = transform::reset) {
                        Icon(Icons.Default.ZoomOutMap, contentDescription = stringResource(R.string.game_zoom_reset))
                    }
                    if (game != null && !game.isComplete) {
                        IconButton(onClick = { if (state.isPaused) viewModel.resume() else viewModel.pause() }) {
                            Icon(
                                if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = stringResource(if (state.isPaused) R.string.game_resume else R.string.game_pause),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (game == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusRow(game = game, state = state)

            SudokuBoard(
                game = game,
                selectedIndex = state.selectedIndex,
                activeHint = state.activeHint,
                transform = transform,
                onCellTap = viewModel::selectCell,
            )

            ActionRow(
                state = state,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onErase = viewModel::erase,
                onToggleNotes = viewModel::toggleNotesMode,
                onHint = viewModel::requestHint,
            )

            NumberPad(
                gridSize = game.gridSize,
                enabled = !state.isPaused && !game.isComplete,
                onNumber = viewModel::enterNumber,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }

    if (game == null) return

    when {
        game.isComplete -> CompletionDialog(
            game = game,
            score = ScoringRules.breakdown(game),
            submission = state.scoreSubmission,
            onNewGame = { viewModel.discardCompletedGame(); onNewGame() },
            onLeaderboard = { onOpenLeaderboard(game.gridSize, game.difficulty) },
            onHome = { viewModel.discardCompletedGame(); onBack() },
        )
        state.pauseReason == PauseReason.OUT_OF_LIVES -> OutOfLivesDialog(
            nextRegenAt = state.lives?.nextRegenAtEpochMillis,
            onOpenStore = onOpenStore,
            onQuit = onBack,
        )
        state.pauseReason == PauseReason.USER -> AlertDialog(
            onDismissRequest = viewModel::resume,
            title = { Text(stringResource(R.string.game_paused)) },
            text = { Text(stringResource(R.string.game_paused_message)) },
            confirmButton = { Button(onClick = viewModel::resume) { Text(stringResource(R.string.game_resume)) } },
            dismissButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.game_quit)) } },
        )
        state.activeHint != null -> HintDialog(
            hint = state.activeHint!!,
            gridSize = game.gridSize,
            onApply = viewModel::applyHint,
            onDismiss = viewModel::dismissHint,
        )
    }
}

@Composable
private fun StatusRow(game: GameState, state: GameUiState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(formatDuration(game.elapsedMillis), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(R.string.game_mistakes, game.mistakes), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(R.string.game_moves, game.moves), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(16.dp))
        Text(
            stringResource(R.string.game_score, state.score?.total ?: 0),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.game_lives), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(4.dp))
        val lives = state.lives
        Text(
            when {
                lives == null -> ""
                lives.unlimited -> stringResource(R.string.game_lives_unlimited)
                else -> lives.count.toString()
            },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ActionRow(
    state: GameUiState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onErase: () -> Unit,
    onToggleNotes: () -> Unit,
    onHint: () -> Unit,
) {
    val game = state.game ?: return
    val enabled = !state.isPaused && !game.isComplete
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        ActionButton(Icons.AutoMirrored.Filled.Undo, R.string.game_undo, enabled && game.undoStack.isNotEmpty(), onUndo)
        ActionButton(Icons.AutoMirrored.Filled.Redo, R.string.game_redo, enabled && game.redoStack.isNotEmpty(), onRedo)
        ActionButton(Icons.Default.Backspace, R.string.game_erase, enabled && state.selectedIndex >= 0, onErase)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledIconToggleButton(checked = state.notesMode, onCheckedChange = { onToggleNotes() }, enabled = enabled) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.game_notes))
            }
            Text(
                stringResource(if (state.notesMode) R.string.game_notes_on else R.string.game_notes),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        ActionButton(Icons.Default.Lightbulb, R.string.game_hint, enabled, onHint)
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = stringResource(label))
        }
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun NumberPad(
    gridSize: GridSize,
    enabled: Boolean,
    onNumber: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = when (gridSize) {
        GridSize.NINE -> 9
        GridSize.TWELVE -> 6
        GridSize.FIFTEEN -> 8
        GridSize.EIGHTEEN -> 9
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        items((1..gridSize.size).toList()) { n ->
            FilledTonalButton(
                onClick = { onNumber(n) },
                enabled = enabled,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(44.dp),
            ) {
                Text(n.toString(), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun HintDialog(hint: Hint, gridSize: GridSize, onApply: () -> Unit, onDismiss: () -> Unit) {
    val row = hint.index / gridSize.size + 1
    val col = hint.index % gridSize.size + 1
    val text = when (hint) {
        is Hint.NakedSingle -> stringResource(R.string.hint_naked_single, row, col, hint.value)
        is Hint.HiddenSingle -> when (hint.unit) {
            Hint.HiddenSingle.Unit.ROW -> stringResource(R.string.hint_hidden_single_row, row, col, hint.value)
            Hint.HiddenSingle.Unit.COLUMN -> stringResource(R.string.hint_hidden_single_column, row, col, hint.value)
            Hint.HiddenSingle.Unit.BOX -> stringResource(R.string.hint_hidden_single_box, row, col, hint.value)
        }
        is Hint.FixMistake -> stringResource(R.string.hint_fix_mistake, row, col, hint.wrongValue, hint.value)
        is Hint.Reveal -> stringResource(R.string.hint_reveal, row, col, hint.value)
        Hint.None -> stringResource(R.string.hint_none)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_hint_title)) },
        text = { Text(text) },
        confirmButton = {
            if (hint != Hint.None) {
                Button(onClick = onApply) { Text(stringResource(R.string.game_hint_apply)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_hint_dismiss)) } },
    )
}

@Composable
private fun OutOfLivesDialog(nextRegenAt: Long?, onOpenStore: () -> Unit, onQuit: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.lives_out_title)) },
        text = {
            Column {
                Text(stringResource(R.string.lives_out_message))
                if (nextRegenAt != null) {
                    Spacer(Modifier.height(8.dp))
                    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(nextRegenAt) {
                        while (true) {
                            now = System.currentTimeMillis()
                            delay(1_000L)
                        }
                    }
                    val remaining = (nextRegenAt - now).coerceAtLeast(0L)
                    Text(stringResource(R.string.lives_next_free, formatDuration(remaining)))
                }
            }
        },
        confirmButton = { Button(onClick = onOpenStore) { Text(stringResource(R.string.lives_go_to_store)) } },
        dismissButton = { TextButton(onClick = onQuit) { Text(stringResource(R.string.game_quit)) } },
    )
}

@Composable
private fun CompletionDialog(
    game: GameState,
    score: ScoreBreakdown,
    submission: ScoreSubmission,
    onNewGame: () -> Unit,
    onLeaderboard: () -> Unit,
    onHome: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.complete_title), textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.complete_time, formatDuration(game.elapsedMillis)))
                Text(stringResource(R.string.complete_moves, game.moves))
                Text(stringResource(R.string.complete_mistakes, game.mistakes))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.complete_score_total, score.total), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.complete_score_base, score.correctEntries, ScoringRules.basePoints(game.gridSize), score.basePoints),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (score.mistakePenalty > 0) {
                    Text(
                        stringResource(R.string.complete_score_penalty, game.mistakes, ScoringRules.MISTAKE_PENALTY, score.mistakePenalty),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    if (score.timeBonus > 0) stringResource(R.string.complete_score_bonus, score.timeBonus, formatDuration(ScoringRules.targetTimeMillis(game)))
                    else stringResource(R.string.complete_score_no_bonus, formatDuration(ScoringRules.targetTimeMillis(game))),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.complete_score_multiplier, difficultyLabel(game.difficulty), score.multiplier),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                when (submission) {
                    ScoreSubmission.Submitted -> Text(stringResource(R.string.complete_score_submitted))
                    ScoreSubmission.Pending -> Text(stringResource(R.string.complete_score_pending))
                    ScoreSubmission.NotSubmitted -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onNewGame) { Text(stringResource(R.string.complete_new_game)) } },
        dismissButton = {
            Row {
                OutlinedButton(onClick = onLeaderboard) { Text(stringResource(R.string.complete_view_leaderboard)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onHome) { Text(stringResource(R.string.complete_home)) }
            }
        },
    )
}
