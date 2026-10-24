package com.maslarski.sudoku.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maslarski.sudoku.domain.engine.GameEngine
import com.maslarski.sudoku.domain.engine.HintEngine
import com.maslarski.sudoku.domain.engine.ScoreBreakdown
import com.maslarski.sudoku.domain.engine.ScoringRules
import com.maslarski.sudoku.di.ApplicationScope
import com.maslarski.sudoku.domain.model.GameRules
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.Hint
import com.maslarski.sudoku.domain.model.Lives
import com.maslarski.sudoku.domain.model.TournamentBoard
import com.maslarski.sudoku.domain.model.Variant
import com.maslarski.sudoku.domain.repository.GameFeedback
import com.maslarski.sudoku.domain.repository.GameRepository
import com.maslarski.sudoku.domain.repository.LivesRepository
import com.maslarski.sudoku.domain.repository.ReviewPromptRepository
import com.maslarski.sudoku.domain.repository.SettingsRepository
import com.maslarski.sudoku.domain.repository.TournamentRepository
import com.maslarski.sudoku.domain.repository.TrophyRepository
import com.maslarski.sudoku.domain.time.EpochClock
import com.maslarski.sudoku.domain.usecase.SubmitScoreUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PauseReason { NONE, USER, OUT_OF_LIVES, BACKGROUND }

sealed interface ScoreSubmission {
    data object NotSubmitted : ScoreSubmission
    data object Submitted : ScoreSubmission
    /** Upload failed (offline etc.); the score is queued and retried on the next leaderboard refresh. */
    data object Pending : ScoreSubmission
    /** Completed, but did not beat the player's existing best in this category. */
    data object NotPersonalBest : ScoreSubmission
    data object LocalOnly : ScoreSubmission
    data object Championship : ScoreSubmission
}

data class GameUiState(
    val game: GameState? = null,
    val selectedIndex: Int = -1,
    val notesMode: Boolean = false,
    val pauseReason: PauseReason = PauseReason.NONE,
    val activeHint: Hint? = null,
    val lives: Lives? = null,
    val scoreSubmission: ScoreSubmission = ScoreSubmission.NotSubmitted,
    val missing: Boolean = false,
    val outOfLivesSecondsLeft: Int? = null,
    val progressReset: Boolean = false,
    val showReviewPrompt: Boolean = false,
    val showCandidates: Boolean = false,
    val showCandidatesUpsell: Boolean = false,
) {
    val isPaused: Boolean get() = pauseReason != PauseReason.NONE
    val score: ScoreBreakdown? get() = game?.let(ScoringRules::breakdown)
    val comboActive: Boolean
        get() = game?.let { game ->
            val last = game.lastCorrectElapsedMillis
            game.comboStreak >= GameRules.COMBO_MIN_STREAK &&
                last != null &&
                game.elapsedMillis - last <= GameRules.COMBO_WINDOW_MILLIS
        } == true
    val outOfLives: Boolean get() = lives?.canPlay == false
    /** Null means unlimited (premium). */
    val hintsRemaining: Int? get() = game?.let { GameRules.hintsRemaining(it, lives) }
    val canUseHint: Boolean get() = game?.let { GameRules.canUseHint(it, lives) } == true
}

@OptIn(FlowPreview::class)
@HiltViewModel
class GameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    private val livesRepository: LivesRepository,
    private val engine: GameEngine,
    private val hintEngine: HintEngine,
    private val submitScore: SubmitScoreUseCase,
    private val reviewPrompt: ReviewPromptRepository,
    private val feedback: GameFeedback,
    private val settingsRepository: SettingsRepository,
    private val clock: EpochClock,
    private val tournamentRepository: TournamentRepository,
    private val trophyRepository: TrophyRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val slotId: Int = checkNotNull(savedStateHandle.get<Int>("gridSize"))

    private val local = MutableStateFlow(GameUiState())

    val uiState: StateFlow<GameUiState> = local.asStateFlow()

    private var timerJob: Job? = null
    private var outOfLivesCountdownJob: Job? = null
    private var completionHandled = false

    init {
        viewModelScope.launch {
            val loaded = gameRepository.loadSlot(slotId)
            if (loaded == null) {
                local.update { it.copy(missing = true) }
            } else {
                val livesRestored = local.value.lives?.canPlay == true
                val game = if (livesRestored) {
                    settleDeadline(loaded, clock.now())
                } else {
                    loaded
                }
                local.update {
                    it.copy(
                        game = game,
                        progressReset = !livesRestored && game.outOfLivesExpired,
                        outOfLivesSecondsLeft = null,
                    )
                }
                if (game != loaded) gameRepository.save(game)
                if (game.isComplete) {
                    local.update {
                        it.copy(
                            scoreSubmission = when {
                                game.dailyDate != null -> ScoreSubmission.Championship
                                game.variant != null -> ScoreSubmission.LocalOnly
                                else -> ScoreSubmission.Submitted
                            },
                        )
                    }
                } else {
                    startTimer()
                    startOutOfLivesCountdownIfNeeded()
                }
            }
        }

        // Autosave: persist shortly after any change to the board state.
        viewModelScope.launch {
            local.map { it.game }.filterNotNull().distinctUntilChanged().debounce(400L)
                .collect { gameRepository.save(it) }
        }

        viewModelScope.launch {
            combine(settingsRepository.settings, livesRepository.lives) { settings, lives ->
                settings.showCandidates && lives.unlimited
            }.collect { enabled ->
                local.update { it.copy(showCandidates = enabled) }
            }
        }

        // Lives gate: pause when out of lives, resume automatically when a life is restored/purchased.
        // Lives are written into the same state as the pause reason so the two can never disagree.
        viewModelScope.launch {
            livesRepository.lives.collect { lives ->
                var becameOutOfLives = false
                var gameToSave: GameState? = null
                val canPlayAgain = lives.canPlay
                local.update { s ->
                    var game = s.game
                    if (canPlayAgain && game != null) {
                        val settled = settleDeadline(game, clock.now())
                        if (settled != game) gameToSave = settled
                        game = settled
                    }
                    val next = s.copy(lives = lives, game = game)
                    val updated = when {
                        !lives.canPlay && s.game?.isComplete != true -> next.copy(pauseReason = PauseReason.OUT_OF_LIVES)
                        lives.canPlay && s.pauseReason == PauseReason.OUT_OF_LIVES ->
                            next.copy(pauseReason = PauseReason.NONE, outOfLivesSecondsLeft = null, progressReset = false)
                        else -> next
                    }
                    val expired = !canPlayAgain && updated.game?.outOfLivesExpired == true
                    val result = if (expired) {
                        updated.copy(progressReset = true, outOfLivesSecondsLeft = null)
                    } else {
                        updated
                    }
                    becameOutOfLives = s.pauseReason != PauseReason.OUT_OF_LIVES &&
                        result.pauseReason == PauseReason.OUT_OF_LIVES
                    result
                }
                gameToSave?.let { game -> gameRepository.save(game) }
                if (canPlayAgain) {
                    outOfLivesCountdownJob?.cancel()
                    outOfLivesCountdownJob = null
                    local.update { it.copy(outOfLivesSecondsLeft = null, progressReset = false) }
                } else if (becameOutOfLives || local.value.pauseReason == PauseReason.OUT_OF_LIVES) {
                    startOutOfLivesCountdownIfNeeded()
                }
            }
        }
    }

    // region timer

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var last = clock.now()
            while (isActive) {
                delay(TICK_MILLIS)
                val now = clock.now()
                val state = local.value
                val game = state.game ?: continue
                if (!state.isPaused && !game.isComplete) {
                    local.update { it.copy(game = engine.tick(game, now - last, now)) }
                }
                last = now
            }
        }
    }

    private fun startOutOfLivesCountdownIfNeeded() {
        val state = local.value
        if (state.pauseReason != PauseReason.OUT_OF_LIVES) return
        val game = state.game ?: return
        if (game.isComplete) return
        if (game.outOfLivesExpired) {
            local.update { it.copy(outOfLivesSecondsLeft = null, progressReset = true) }
            return
        }
        if (state.progressReset) return
        if (outOfLivesCountdownJob?.isActive == true) return
        val now = clock.now()
        val deadline = game.outOfLivesDeadlineEpochMillis ?: now + GameRules.OUT_OF_LIVES_COUNTDOWN_SECONDS * 1_000L
        val timedGame = if (game.outOfLivesDeadlineEpochMillis == null) {
            game.copy(outOfLivesDeadlineEpochMillis = deadline)
        } else {
            game
        }
        if (timedGame !== game) {
            local.update { it.copy(game = timedGame) }
            viewModelScope.launch { gameRepository.save(timedGame) }
        }
        val initialSecondsLeft = secondsUntil(deadline, now)
        if (initialSecondsLeft <= 0) {
            viewModelScope.launch { expireOutOfLivesGame(timedGame) }
            return
        }
        local.update {
            it.copy(game = timedGame, outOfLivesSecondsLeft = initialSecondsLeft, progressReset = false)
        }
        outOfLivesCountdownJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                val current = local.value
                if (current.pauseReason != PauseReason.OUT_OF_LIVES || current.game?.isComplete != false) return@launch
                val currentGame = current.game ?: return@launch
                val currentDeadline = currentGame.outOfLivesDeadlineEpochMillis ?: return@launch
                val secondsLeft = secondsUntil(currentDeadline, clock.now())
                if (secondsLeft <= 0) {
                    expireOutOfLivesGame(currentGame)
                    return@launch
                }
                local.update { it.copy(outOfLivesSecondsLeft = secondsLeft) }
            }
        }
    }

    private fun secondsUntil(deadline: Long, now: Long): Int {
        val millisLeft = deadline - now
        if (millisLeft <= 0L) return 0
        return ((millisLeft + 999L) / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun settleDeadline(game: GameState, now: Long): GameState {
        val deadline = game.outOfLivesDeadlineEpochMillis
        return if (deadline != null && deadline <= now) {
            resetOutOfLivesGame(game, expired = false, nowEpochMillis = now)
        } else {
            game.copy(outOfLivesDeadlineEpochMillis = null, outOfLivesExpired = false)
        }
    }

    private fun resetOutOfLivesGame(game: GameState, expired: Boolean, nowEpochMillis: Long): GameState {
        val resetCells = GameState.fromPuzzle(game.puzzle, nowEpochMillis).cells
        return game.copy(
            cells = resetCells,
            undoStack = emptyList(),
            redoStack = emptyList(),
            outOfLivesDeadlineEpochMillis = null,
            outOfLivesExpired = expired,
            comboStreak = 0,
            lastCorrectElapsedMillis = null,
            countedCells = emptySet(),
        )
    }

    private suspend fun expireOutOfLivesGame(game: GameState) {
        val reset = resetOutOfLivesGame(game, expired = true, nowEpochMillis = clock.now())
        var didReset = false
        local.update { state ->
            if (state.pauseReason != PauseReason.OUT_OF_LIVES ||
                state.game?.outOfLivesDeadlineEpochMillis != game.outOfLivesDeadlineEpochMillis
            ) {
                state
            } else {
                didReset = true
                state.copy(
                    game = reset,
                    selectedIndex = -1,
                    activeHint = null,
                    outOfLivesSecondsLeft = null,
                    progressReset = true,
                )
            }
        }
        outOfLivesCountdownJob = null
        if (didReset) gameRepository.save(reset)
    }

    fun pause(reason: PauseReason = PauseReason.USER) {
        local.update { s ->
            if (s.game?.isComplete == true) s
            else if (s.pauseReason == PauseReason.OUT_OF_LIVES && reason != PauseReason.OUT_OF_LIVES) s
            else s.copy(pauseReason = reason)
        }
        local.value.game?.let { g -> viewModelScope.launch { gameRepository.save(g) } }
    }

    fun resume() {
        local.update { s ->
            if (s.outOfLives) s.copy(pauseReason = PauseReason.OUT_OF_LIVES) else s.copy(pauseReason = PauseReason.NONE)
        }
    }

    /** Called from the lifecycle observer; pauses only if the user hasn't already paused for another reason. */
    fun onAppBackgrounded() {
        if (local.value.pauseReason == PauseReason.NONE) pause(PauseReason.BACKGROUND)
    }

    fun onAppForegrounded() {
        if (local.value.pauseReason == PauseReason.BACKGROUND) resume()
    }

    // endregion

    // region input

    fun selectCell(index: Int) {
        feedback.cellSelected()
        local.update { it.copy(selectedIndex = index, activeHint = null) }
    }

    fun toggleNotesMode() = local.update { it.copy(notesMode = !it.notesMode) }

    fun toggleCandidates() {
        val lives = local.value.lives ?: return
        if (!lives.unlimited) {
            local.update { it.copy(showCandidatesUpsell = true) }
            return
        }
        val enabled = !local.value.showCandidates
        local.update { it.copy(showCandidates = enabled) }
        viewModelScope.launch { settingsRepository.setShowCandidates(enabled) }
    }

    fun dismissCandidatesUpsell() = local.update { it.copy(showCandidatesUpsell = false) }

    fun enterNumber(value: Int) {
        val state = local.value
        val game = state.game ?: return
        val index = state.selectedIndex
        if (state.isPaused || index < 0 || game.isComplete || game.cells[index].isGiven) return

        if (state.notesMode) {
            local.update { it.copy(game = engine.toggleNote(game, index, value)) }
            return
        }

        val result = engine.enterValue(game, index, value)
        local.update { it.copy(game = result.state) }
        if (result.state.mistakes > game.mistakes) feedback.mistake()
        if (result.lostLife && state.lives?.unlimited != true) {
            viewModelScope.launch { livesRepository.consumeLife() }
        }
        if (result.state.isComplete) onCompleted(result.state)
    }

    fun erase() = mutateBoard { game, index -> engine.erase(game, index) }

    fun undo() = mutateBoardNoSelection { engine.undo(it) }

    fun redo() = mutateBoardNoSelection { engine.redo(it) }

    fun requestHint() {
        val state = local.value
        val game = state.game ?: return
        if (state.isPaused || game.isComplete || !state.canUseHint) return
        val targeted = if (state.selectedIndex >= 0) hintEngine.hintFor(game, state.selectedIndex) else null
        val hint = targeted ?: hintEngine.nextHint(game)
        local.update {
            it.copy(
                activeHint = hint,
                selectedIndex = if (targeted != null) it.selectedIndex else if (hint.index >= 0) hint.index else it.selectedIndex,
            )
        }
    }

    fun applyHint() {
        val state = local.value
        val game = state.game ?: return
        val hint = state.activeHint ?: return
        if (!state.canUseHint) return
        val next = engine.applyHint(game, hint)
        local.update { it.copy(game = next, activeHint = null) }
        if (next.isComplete) onCompleted(next)
    }

    fun dismissHint() = local.update { it.copy(activeHint = null) }

    private fun mutateBoard(block: (GameState, Int) -> GameState) {
        val state = local.value
        val game = state.game ?: return
        if (state.isPaused || state.selectedIndex < 0 || game.isComplete) return
        local.update { it.copy(game = block(game, state.selectedIndex)) }
    }

    private fun mutateBoardNoSelection(block: (GameState) -> GameState) {
        val state = local.value
        val game = state.game ?: return
        if (state.isPaused || game.isComplete) return
        local.update { it.copy(game = block(game)) }
    }

    // endregion

    private fun onCompleted(state: GameState) {
        if (completionHandled) return
        completionHandled = true
        feedback.puzzleCompleted()
        timerJob?.cancel()
        applicationScope.launch {
            gameRepository.save(state)
            val completedAt = clock.now()
            val points = ScoringRules.points(state)
            runCatching { trophyRepository.recordCompletion(state) }
            val submission = when {
                state.dailyDate != null -> ScoreSubmission.Championship
                state.variant != null -> ScoreSubmission.LocalOnly
                else -> submitScore(state).fold(
                    onSuccess = { uploaded -> if (uploaded) ScoreSubmission.Submitted else ScoreSubmission.NotPersonalBest },
                    onFailure = { ScoreSubmission.Pending },
                )
            }
            local.update { it.copy(scoreSubmission = submission) }
            if (state.dailyDate != null) {
                runCatching { tournamentRepository.record(TournamentBoard.CHAMPIONSHIP, completedAt, points) }
            } else if (state.variant != null) {
                if (state.variant == Variant.SAMURAI || state.variant == Variant.FLOWER) {
                    runCatching { tournamentRepository.record(TournamentBoard.ARENA, completedAt, points) }
                }
                runCatching { tournamentRepository.record(TournamentBoard.CHAMPIONSHIP, completedAt, points) }
            } else {
                runCatching { tournamentRepository.record(TournamentBoard.CHAMPIONSHIP, completedAt, points) }
            }
            if (runCatching { reviewPrompt.onPuzzleCompleted() }.getOrDefault(false)) {
                local.update { it.copy(showReviewPrompt = true) }
            }
        }
    }

    fun onReviewRateNow() = resolveReviewPrompt { reviewPrompt.rateNow() }

    fun onReviewLater() = resolveReviewPrompt { reviewPrompt.later() }

    fun onReviewNever() = resolveReviewPrompt { reviewPrompt.never() }

    private fun resolveReviewPrompt(action: suspend () -> Unit) {
        viewModelScope.launch {
            action()
            local.update { it.copy(showReviewPrompt = false) }
        }
    }

    /** Removes the finished game so the home screen no longer offers to continue it. */
    fun discardCompletedGame() {
        val game = local.value.game ?: return
        if (game.isComplete) viewModelScope.launch { gameRepository.deleteSlot(game.slotId) }
    }

    override fun onCleared() {
        timerJob?.cancel()
        outOfLivesCountdownJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val TICK_MILLIS = 250L
    }
}
