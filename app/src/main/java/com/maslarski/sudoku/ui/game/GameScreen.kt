package com.maslarski.sudoku.ui.game

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.LocalActivity
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import com.maslarski.sudoku.R
import com.maslarski.sudoku.domain.engine.ScoreBreakdown
import com.maslarski.sudoku.domain.engine.ScoringRules
import com.maslarski.sudoku.domain.engine.SudokuSolver
import com.maslarski.sudoku.domain.engine.UnitSolver
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Hint
import com.maslarski.sudoku.domain.model.Variant
import com.maslarski.sudoku.ui.components.difficultyLabel
import com.maslarski.sudoku.ui.components.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    onBack: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenLeaderboard: (GridSize, Difficulty) -> Unit,
    onOpenTournaments: () -> Unit,
    onNewGame: () -> Unit,
    viewModel: GameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val transform = remember { BoardTransform() }
    // Outlives ReviewDialog, which leaves composition as soon as the prompt is resolved.
    val reviewScope = rememberCoroutineScope()

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
                        val variant = game.variant
                        val title = when {
                            game.dailyDate != null -> stringResource(R.string.game_title_daily, game.dailyDate)
                            variant != null -> stringResource(
                                R.string.game_title_variant,
                                stringResource(variantName(variant)),
                                difficultyLabel(game.difficulty),
                            )
                            else -> stringResource(
                                R.string.game_title,
                                game.gridSize.size,
                                difficultyLabel(game.difficulty),
                            )
                        }
                        Text(title)
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
        val candidateMasks = remember(game.cells, state.showCandidates, game.variant, game.puzzle.outsideClues) {
            if (!state.showCandidates) {
                null
            } else {
                val grid = game.valuesArray()
                val topology = game.topology
                if (game.variant == null) {
                    val solver = SudokuSolver(game.gridSize)
                    IntArray(game.cells.size) { index ->
                        if (topology.active[index] && game.cells[index].isEmpty) solver.candidates(grid, index) else 0
                    }
                } else {
                    val solver = UnitSolver(topology, game.puzzle.outsideClues?.exclusionMasks(topology))
                    IntArray(game.cells.size) { index ->
                        if (topology.active[index] && game.cells[index].isEmpty) solver.candidates(grid, index) else 0
                    }
                }
            }
        }
        val boardGame = remember(game.cells, game.puzzle, game.iconMode) { game }

        BoxWithConstraints(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
        ) {
            val isLandscape = maxWidth > maxHeight
            if (!isLandscape) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatusRow(game = game, state = state)
                    Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            SudokuBoard(
                                game = boardGame,
                                selectedIndex = state.selectedIndex,
                                activeHint = state.activeHint,
                                candidateMasks = candidateMasks,
                                transform = transform,
                                onCellTap = viewModel::selectCell,
                                modifier = Modifier.size(minOf(maxWidth, maxHeight)),
                            )
                        }
                    }
                    ActionRow(
                        state = state,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                        onErase = viewModel::erase,
                        onToggleNotes = viewModel::toggleNotesMode,
                        onToggleCandidates = viewModel::toggleCandidates,
                        onHint = viewModel::requestHint,
                    )
                    NumberPad(
                        gridSize = game.gridSize,
                        variant = game.variant,
                        iconMode = game.iconMode,
                        enabled = !state.isPaused && !game.isComplete,
                        onNumber = viewModel::enterNumber,
                    )
                }
            } else {
                val boardSize = minOf(maxHeight, maxWidth * 0.6f)
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SudokuBoard(
                        game = boardGame,
                        selectedIndex = state.selectedIndex,
                        activeHint = state.activeHint,
                        candidateMasks = candidateMasks,
                        transform = transform,
                        onCellTap = viewModel::selectCell,
                        modifier = Modifier.size(boardSize),
                    )
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatusRow(game = game, state = state)
                        ActionRow(
                            state = state,
                            onUndo = viewModel::undo,
                            onRedo = viewModel::redo,
                            onErase = viewModel::erase,
                            onToggleNotes = viewModel::toggleNotesMode,
                            onToggleCandidates = viewModel::toggleCandidates,
                            onHint = viewModel::requestHint,
                        )
                        NumberPad(
                            gridSize = game.gridSize,
                            variant = game.variant,
                            iconMode = game.iconMode,
                            enabled = !state.isPaused && !game.isComplete,
                            onNumber = viewModel::enterNumber,
                        )
                    }
                }
            }
        }
    }

    if (game == null) return

    when {
        state.showReviewPrompt -> ReviewDialog(
            scope = reviewScope,
            onRateNow = viewModel::onReviewRateNow,
            onLater = viewModel::onReviewLater,
            onNever = viewModel::onReviewNever,
        )
        game.isComplete -> CompletionDialog(
            game = game,
            score = ScoringRules.breakdown(game),
            submission = state.scoreSubmission,
            onNewGame = { viewModel.discardCompletedGame(); onNewGame() },
            onLeaderboard = {
                if (game.dailyDate != null) onOpenTournaments()
                else onOpenLeaderboard(game.gridSize, game.difficulty)
            },
            onHome = { viewModel.discardCompletedGame(); onBack() },
        )
        state.pauseReason == PauseReason.OUT_OF_LIVES -> OutOfLivesDialog(
            secondsLeft = state.outOfLivesSecondsLeft,
            progressReset = state.progressReset,
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
            gridWidth = game.topology.width,
            iconMode = game.iconMode && game.variant == null &&
                (game.gridSize == GridSize.NINE || game.gridSize == GridSize.TWELVE),
            onApply = viewModel::applyHint,
            onDismiss = viewModel::dismissHint,
        )
    }

    if (state.showCandidatesUpsell) {
        CandidatesUpsellDialog(
            onUpgrade = {
                viewModel.dismissCandidatesUpsell()
                onOpenStore()
            },
            onDismiss = viewModel::dismissCandidatesUpsell,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusRow(game: GameState, state: GameUiState) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val comboColor = if (isLight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val glowColor = if (isLight) comboColor.copy(alpha = 0.45f) else comboColor
    val minBlur = if (isLight) 4f else 6f
    val maxBlur = if (isLight) 12f else 18f
    val glowTransition = rememberInfiniteTransition(label = "combo_glow")
    val blurRadius by glowTransition.animateFloat(
        initialValue = minBlur,
        targetValue = if (state.comboActive) maxBlur else minBlur,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "combo_glow_radius",
    )
    val comboStyle = MaterialTheme.typography.bodyMedium.copy(
        color = comboColor,
        shadow = Shadow(color = glowColor, blurRadius = blurRadius),
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(formatDuration(game.elapsedMillis), style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
        Text(stringResource(R.string.game_mistakes, game.mistakes), style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
        AnimatedContent(
            targetState = state.comboActive,
            modifier = Modifier.widthIn(min = 84.dp),
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith
                    (fadeOut() + scaleOut(targetScale = 0.9f))
            },
            label = "moves_combo",
        ) { comboActive ->
            if (comboActive) {
                Text(stringResource(R.string.game_combo), style = comboStyle, maxLines = 1, softWrap = false)
            } else {
                Text(stringResource(R.string.game_moves, game.moves), style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
            }
        }
        Text(
            stringResource(R.string.game_score, state.score?.total ?: 0),
            style = if (state.comboActive) comboStyle else MaterialTheme.typography.bodyMedium,
            color = if (state.comboActive) comboColor else MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false,
        )
        val lives = state.lives
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.game_lives), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(4.dp))
            Text(
                when {
                    lives == null -> ""
                    lives.unlimited -> stringResource(R.string.game_lives_unlimited)
                    else -> lives.count.toString()
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun ActionRow(
    state: GameUiState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onErase: () -> Unit,
    onToggleNotes: () -> Unit,
    onToggleCandidates: () -> Unit,
    onHint: () -> Unit,
) {
    val game = state.game ?: return
    val enabled = !state.isPaused && !game.isComplete
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        ActionButton(Icons.AutoMirrored.Filled.Undo, R.string.game_undo, enabled && game.undoStack.isNotEmpty(), onUndo, modifier = Modifier.weight(1f))
        ActionButton(Icons.AutoMirrored.Filled.Redo, R.string.game_redo, enabled && game.redoStack.isNotEmpty(), onRedo, modifier = Modifier.weight(1f))
        ActionButton(Icons.Default.Backspace, R.string.game_erase, enabled && state.selectedIndex >= 0, onErase, modifier = Modifier.weight(1f))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            FilledIconToggleButton(checked = state.notesMode, onCheckedChange = { onToggleNotes() }, enabled = enabled) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.game_notes))
            }
            ActionLabel(
                stringResource(if (state.notesMode) R.string.game_notes_on else R.string.game_notes),
            )
        }
        val hintsRemaining = state.hintsRemaining
        ActionButton(
            Icons.Default.Lightbulb,
            R.string.game_hint,
            enabled && state.canUseHint,
            onHint,
            labelText = if (hintsRemaining == null) stringResource(R.string.game_hint)
            else stringResource(R.string.game_hint_remaining, hintsRemaining),
            modifier = Modifier.weight(1f),
        )
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            FilledIconToggleButton(
                checked = state.showCandidates,
                onCheckedChange = { onToggleCandidates() },
                enabled = enabled,
            ) {
                Icon(Icons.Default.GridOn, contentDescription = stringResource(R.string.game_candidates))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ActionLabel(stringResource(R.string.game_candidates), Modifier.weight(1f))
                if (state.lives?.unlimited != true) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelText: String = stringResource(label),
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = stringResource(label))
        }
        ActionLabel(labelText, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ActionLabel(text: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelSmall.copy(
        color = LocalContentColor.current,
        textAlign = TextAlign.Center,
    )
    BasicText(
        text = text,
        modifier = modifier,
        style = style,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize),
    )
}

@Composable
private fun NumberPad(
    gridSize: GridSize,
    variant: Variant?,
    iconMode: Boolean,
    enabled: Boolean,
    onNumber: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showIcons = iconMode && variant == null &&
        (gridSize == GridSize.NINE || gridSize == GridSize.TWELVE)
    val columns = when (gridSize) {
        GridSize.NINE -> 9
        GridSize.TWELVE -> 6
        GridSize.FIFTEEN -> 8
        GridSize.EIGHTEEN -> 9
    }
    val rows = (gridSize.size + columns - 1) / columns
    val keypadHeight = 44.dp * rows + 4.dp * (rows - 1) + 8.dp
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxWidth().height(keypadHeight),
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
                if (showIcons && n <= HolidayIcons.size) {
                    Icon(
                        imageVector = HolidayIcons[n - 1],
                        contentDescription = stringResource(holidayIconNames[n - 1]),
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    Text(n.toString(), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun HintDialog(
    hint: Hint,
    gridWidth: Int,
    iconMode: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val row = hint.index / gridWidth + 1
    val col = hint.index % gridWidth + 1
    val text = when (hint) {
        is Hint.NakedSingle -> if (iconMode) {
            stringResource(R.string.hint_naked_single_icon, row, col, holidayName(hint.value))
        } else {
            stringResource(R.string.hint_naked_single, row, col, hint.value)
        }
        is Hint.HiddenSingle -> when (hint.unit) {
            Hint.HiddenSingle.Unit.ROW -> if (iconMode) {
                stringResource(R.string.hint_hidden_single_row_icon, row, col, holidayName(hint.value))
            } else {
                stringResource(R.string.hint_hidden_single_row, row, col, hint.value)
            }
            Hint.HiddenSingle.Unit.COLUMN -> if (iconMode) {
                stringResource(R.string.hint_hidden_single_column_icon, row, col, holidayName(hint.value))
            } else {
                stringResource(R.string.hint_hidden_single_column, row, col, hint.value)
            }
            Hint.HiddenSingle.Unit.BOX -> if (iconMode) {
                stringResource(R.string.hint_hidden_single_box_icon, row, col, holidayName(hint.value))
            } else {
                stringResource(R.string.hint_hidden_single_box, row, col, hint.value)
            }
        }
        is Hint.FixMistake -> if (iconMode) {
            stringResource(
                R.string.hint_fix_mistake_icon,
                row,
                col,
                holidayName(hint.wrongValue),
                holidayName(hint.value),
            )
        } else {
            stringResource(R.string.hint_fix_mistake, row, col, hint.wrongValue, hint.value)
        }
        is Hint.Reveal -> if (iconMode) {
            stringResource(R.string.hint_reveal_icon, row, col, holidayName(hint.value))
        } else {
            stringResource(R.string.hint_reveal, row, col, hint.value)
        }
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
private fun holidayName(value: Int): String =
    if (value in 1..holidayIconNames.size) stringResource(holidayIconNames[value - 1]) else value.toString()

@Composable
private fun CandidatesUpsellDialog(onUpgrade: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        title = { Text(stringResource(R.string.candidates_upsell_title)) },
        text = { Text(stringResource(R.string.candidates_upsell_body)) },
        confirmButton = {
            Button(onClick = onUpgrade) { Text(stringResource(R.string.candidates_upsell_upgrade)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.candidates_upsell_not_now)) }
        },
    )
}

@Composable
private fun OutOfLivesDialog(
    secondsLeft: Int?,
    progressReset: Boolean,
    onOpenStore: () -> Unit,
    onQuit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.lives_out_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    progressReset -> Text(stringResource(R.string.lives_progress_reset))
                    secondsLeft != null -> {
                        Text(stringResource(R.string.lives_save_progress, formatDuration(secondsLeft * 1_000L)))
                        val animated by animateFloatAsState(
                            targetValue = secondsLeft / 60f,
                            animationSpec = tween(durationMillis = 1_000, easing = LinearEasing),
                            label = "outOfLivesProgress",
                        )
                        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { animated },
                                strokeWidth = 8.dp,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Text(secondsLeft.toString(), style = MaterialTheme.typography.headlineLarge)
                        }
                    }
                    else -> Text(stringResource(R.string.lives_out_message))
                }
            }
        },
        confirmButton = { Button(onClick = onOpenStore) { Text(stringResource(R.string.lives_go_to_store)) } },
        dismissButton = { TextButton(onClick = onQuit) { Text(stringResource(R.string.game_quit)) } },
    )
}

@Composable
private fun ReviewDialog(scope: CoroutineScope, onRateNow: () -> Unit, onLater: () -> Unit, onNever: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.review_title)) },
        text = { Text(stringResource(R.string.review_message)) },
        confirmButton = {
            Button(onClick = {
                onRateNow()
                scope.launch {
                    try {
                        val host = activity ?: return@launch
                        val manager = ReviewManagerFactory.create(context)
                        val info = manager.requestReview()
                        manager.launchReview(host, info)
                    } catch (_: Exception) {
                        // Review flow is unavailable for sideloaded installs and some Play states.
                    }
                }
            }) { Text(stringResource(R.string.review_rate_now)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onLater) { Text(stringResource(R.string.review_later)) }
                TextButton(onClick = onNever) { Text(stringResource(R.string.review_never)) }
            }
        },
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
                    stringResource(R.string.complete_score_base, score.correctEntries, ScoringRules.basePoints(game), score.basePoints),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (score.comboBonus > 0) {
                    Text(
                        stringResource(R.string.complete_score_combo, score.comboBonus),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (score.mistakePenalty > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.complete_score_penalty,
                            game.mistakes,
                            game.mistakes,
                            ScoringRules.MISTAKE_PENALTY,
                            score.mistakePenalty,
                        ),
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
                    ScoreSubmission.NotPersonalBest -> Text(stringResource(R.string.complete_score_not_best))
                    ScoreSubmission.LocalOnly -> Text(stringResource(R.string.complete_local_only))
                    ScoreSubmission.Championship -> Text(stringResource(R.string.complete_score_championship))
                    ScoreSubmission.NotSubmitted -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onNewGame) { Text(stringResource(R.string.complete_new_game)) } },
        dismissButton = {
            Row {
                if (game.variant == null) {
                    OutlinedButton(onClick = onLeaderboard) { Text(stringResource(R.string.complete_view_leaderboard)) }
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onHome) { Text(stringResource(R.string.complete_home)) }
            }
        },
    )
}

@StringRes
private fun variantName(variant: Variant): Int = when (variant) {
    Variant.SAMURAI -> R.string.variant_samurai
    Variant.FLOWER -> R.string.variant_flower
    Variant.OUTSIDE -> R.string.variant_outside
}
