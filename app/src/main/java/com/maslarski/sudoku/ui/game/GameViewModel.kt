package com.maslarski.sudoku.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maslarski.sudoku.domain.engine.GameEngine
import com.maslarski.sudoku.domain.engine.HintEngine
import com.maslarski.sudoku.domain.engine.ScoreBreakdown
import com.maslarski.sudoku.domain.engine.ScoringRules
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Hint
import com.maslarski.sudoku.domain.model.Lives
import com.maslarski.sudoku.domain.repository.GameRepository
import com.maslarski.sudoku.domain.repository.LivesRepository
import com.maslarski.sudoku.domain.usecase.SubmitScoreUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PauseReason { NONE, USER, OUT_OF_LIVES, BACKGROUND }

sealed interface ScoreSubmission {
    data object NotSubmitted : ScoreSubmission
    data object Submitted : ScoreSubmission
    data object Pending : ScoreSubmission
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
) {
    val isPaused: Boolean get() = pauseReason != PauseReason.NONE
    val score: ScoreBreakdown? get() = game?.let(ScoringRules::breakdown)
    val outOfLives: Boolean get() = lives?.canPlay == false
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
) : ViewModel() {

    private val gridSize: GridSize = GridSize.entries.first { it.size == checkNotNull(savedStateHandle.get<Int>("gridSize")) }

    private val local = MutableStateFlow(GameUiState())

    val uiState: StateFlow<GameUiState> = combine(local, livesRepository.lives) { s, lives -> s.copy(lives = lives) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GameUiState())

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            val loaded = gameRepository.load(gridSize)
            if (loaded == null) {
                local.update { it.copy(missing = true) }
            } else {
                local.update { it.copy(game = loaded) }
                if (loaded.isComplete) {
                    local.update { it.copy(scoreSubmission = ScoreSubmission.Submitted) }
                } else {
                    startTimer()
                }
            }
        }

        // Autosave: persist shortly after any change to the board state.
        viewModelScope.launch {
            local.map { it.game }.filterNotNull().distinctUntilChanged().debounce(400L)
                .collect { gameRepository.save(it) }
        }

        // Lives gate: pause when out of lives, resume automatically when a life is restored/purchased.
        viewModelScope.launch {
            livesRepository.lives.map { it.canPlay }.distinctUntilChanged().collect { canPlay ->
                if (!canPlay) {
                    pause(PauseReason.OUT_OF_LIVES)
                } else if (local.value.pauseReason == PauseReason.OUT_OF_LIVES) {
                    resume()
                }
            }
        }
    }

    // region timer

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var last = System.currentTimeMillis()
            while (isActive) {
                delay(TICK_MILLIS)
                val now = System.currentTimeMillis()
                val state = local.value
                val game = state.game ?: continue
                if (!state.isPaused && !game.isComplete) {
                    local.update { it.copy(game = engine.tick(game, now - last, now)) }
                }
                last = now
            }
        }
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
        val outOfLives = uiState.value.outOfLives
        local.update { s ->
            if (outOfLives) s.copy(pauseReason = PauseReason.OUT_OF_LIVES) else s.copy(pauseReason = PauseReason.NONE)
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

    fun selectCell(index: Int) = local.update { it.copy(selectedIndex = index, activeHint = null) }

    fun toggleNotesMode() = local.update { it.copy(notesMode = !it.notesMode) }

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
        if (result.wasMistake && uiState.value.lives?.unlimited != true) {
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
        if (state.isPaused || game.isComplete) return
        val hint = hintEngine.nextHint(game)
        local.update {
            it.copy(activeHint = hint, selectedIndex = if (hint.index >= 0) hint.index else it.selectedIndex)
        }
    }

    fun applyHint() {
        val state = local.value
        val game = state.game ?: return
        val hint = state.activeHint ?: return
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
        timerJob?.cancel()
        viewModelScope.launch {
            gameRepository.save(state)
            val remote = submitScore(state).getOrDefault(false)
            local.update {
                it.copy(scoreSubmission = if (remote) ScoreSubmission.Submitted else ScoreSubmission.Pending)
            }
        }
    }

    /** Removes the finished game so the home screen no longer offers to continue it. */
    fun discardCompletedGame() {
        val game = local.value.game ?: return
        if (game.isComplete) viewModelScope.launch { gameRepository.delete(game.gridSize) }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val TICK_MILLIS = 250L
    }
}
