package com.maslarski.sudoku.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.DailyChallenge
import com.maslarski.sudoku.domain.model.GameRules
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Lives
import com.maslarski.sudoku.domain.model.Variant
import com.maslarski.sudoku.domain.repository.GameRepository
import com.maslarski.sudoku.domain.repository.DailyChallengeRepository
import com.maslarski.sudoku.domain.repository.LivesRepository
import com.maslarski.sudoku.domain.repository.SavedGameSummary
import com.maslarski.sudoku.domain.repository.SettingsRepository
import com.maslarski.sudoku.domain.repository.TournamentRepository
import com.maslarski.sudoku.domain.repository.TrophyRepository
import com.maslarski.sudoku.domain.usecase.StartNewGameUseCase
import com.maslarski.sudoku.domain.time.EpochClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val gridSize: GridSize = GridSize.NINE,
    val difficulty: Difficulty = Difficulty.EASY,
    val variant: Variant? = null,
    val savedGames: List<SavedGameSummary> = emptyList(),
    val lives: Lives? = null,
    val iconMode: Boolean = false,
    val generating: Boolean = false,
    val confirmReplace: Boolean = false,
    /** Set when a replacement was refused because the abort penalty could not be paid. */
    val replaceBlocked: Boolean = false,
    /** Set when generating or saving a new game threw; any charge has been refunded. */
    val startFailed: Boolean = false,
    val todayDate: String = "",
    val todayStamped: Boolean = false,
) {
    val selectedSlotId: Int get() = variant?.slotId ?: gridSize.size
    val savedForSelection: SavedGameSummary? get() = savedGames.firstOrNull { it.slotId == selectedSlotId }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val livesRepository: LivesRepository,
    private val settingsRepository: SettingsRepository,
    private val startNewGame: StartNewGameUseCase,
    private val dailyChallengeRepository: DailyChallengeRepository,
    private val clock: EpochClock,
    private val tournamentRepository: TournamentRepository,
    private val trophyRepository: TrophyRepository,
) : ViewModel() {

    private val selection = MutableStateFlow(HomeUiState())
    private val iconModeOverride = MutableStateFlow<Boolean?>(null)

    init {
        viewModelScope.launch { tournamentRepository.flushPending() }
        viewModelScope.launch { trophyRepository.refreshPeriodAwards() }
    }

    private val baseUiState = combine(
        selection,
        gameRepository.observeSavedGames(),
        livesRepository.lives,
        settingsRepository.settings,
        iconModeOverride,
    ) { sel, saved, lives, settings, iconMode ->
        sel.copy(
            savedGames = saved.sortedByDescending { it.lastPlayedEpochMillis },
            lives = lives,
            iconMode = iconMode ?: settings.iconMode,
        )
    }

    private val todayDate = flow {
        while (true) {
            emit(DailyChallenge.dateStringAt(clock.now()))
            delay(60_000L)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        baseUiState,
        dailyChallengeRepository.stampedDates,
        todayDate,
    ) { state, stamps, date ->
        state.copy(todayDate = date, todayStamped = date in stamps)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _navigateToGame = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val navigateToGame: SharedFlow<Int> = _navigateToGame.asSharedFlow()

    fun selectGridSize(gridSize: GridSize) = selection.update { it.copy(gridSize = gridSize, variant = null) }
    fun selectVariant(variant: Variant) =
        selection.update { it.copy(gridSize = GridSize.NINE, variant = variant) }
    fun selectDifficulty(difficulty: Difficulty) = selection.update { it.copy(difficulty = difficulty) }
    fun setIconMode(enabled: Boolean) {
        iconModeOverride.value = enabled
        viewModelScope.launch { settingsRepository.setIconMode(enabled) }
    }

    fun onDailyChallengeClicked() {
        val date = DailyChallenge.dateStringAt(clock.now())
        val current = uiState.value
        if (current.todayStamped || current.generating) return
        val saved = current.savedGames.firstOrNull { it.slotId == DailyChallenge.SLOT_ID }
        if (saved?.dailyDate == date) {
            _navigateToGame.tryEmit(DailyChallenge.SLOT_ID)
            return
        }
        selection.update { it.copy(generating = true) }
        viewModelScope.launch {
            try {
                startNewGame(
                    gridSize = GridSize.NINE,
                    difficulty = Difficulty.MEDIUM,
                    iconMode = false,
                    dailyDate = date,
                )
                _navigateToGame.emit(DailyChallenge.SLOT_ID)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                selection.update { it.copy(startFailed = true) }
            } finally {
                selection.update { it.copy(generating = false) }
            }
        }
    }

    fun onNewGameClicked() {
        if (uiState.value.savedForSelection != null) {
            selection.update { it.copy(confirmReplace = true) }
        } else {
            startGame()
        }
    }

    fun dismissReplace() = selection.update { it.copy(confirmReplace = false) }

    /**
     * Abandoning an in-progress game of the same size costs [GameRules.ABORT_PENALTY_LIVES]. The life is
     * charged only once the replacement puzzle exists; if it cannot be charged the old game is kept.
     */
    fun confirmReplace() {
        selection.update { it.copy(confirmReplace = false) }
        val lives = uiState.value.lives
        if (lives?.canPlay == false) {
            selection.update { it.copy(replaceBlocked = true) }
            return
        }
        // consumeLife() is atomic and a no-op for unlimited lives, so it is the source of truth at save time.
        startGame(
            beforeSave = { (1..GameRules.ABORT_PENALTY_LIVES).all { livesRepository.consumeLife() } },
            onSaveFailed = {
                if (livesRepository.lives.first().unlimited.not()) livesRepository.addLives(GameRules.ABORT_PENALTY_LIVES)
            },
        )
    }

    fun dismissReplaceBlocked() = selection.update { it.copy(replaceBlocked = false) }
    fun dismissStartFailed() = selection.update { it.copy(startFailed = false) }

    fun continueGame(gridSize: GridSize) {
        _navigateToGame.tryEmit(gridSize.size)
    }

    fun continueGame(saved: SavedGameSummary) {
        _navigateToGame.tryEmit(saved.slotId)
    }

    private fun startGame(
        beforeSave: suspend () -> Boolean = { true },
        onSaveFailed: suspend () -> Unit = {},
    ) {
        if (selection.value.generating) return
        val (gridSize, difficulty, variant) = selection.value.let {
            Triple(it.gridSize, it.difficulty, it.variant)
        }
        val iconMode = uiState.value.iconMode
        selection.update { it.copy(generating = true) }
        viewModelScope.launch {
            try {
                if (
                    startNewGame(
                        gridSize = gridSize,
                        difficulty = difficulty,
                        beforeSave = beforeSave,
                        onSaveFailed = onSaveFailed,
                        variant = variant,
                        iconMode = iconMode,
                    ) == null
                ) {
                    selection.update { it.copy(replaceBlocked = true) }
                    return@launch
                }
                _navigateToGame.emit(variant?.slotId ?: gridSize.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                selection.update { it.copy(startFailed = true) }
            } finally {
                selection.update { it.copy(generating = false) }
            }
        }
    }
}
