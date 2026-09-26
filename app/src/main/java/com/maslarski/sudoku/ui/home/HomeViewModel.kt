package com.maslarski.sudoku.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameRules
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Lives
import com.maslarski.sudoku.domain.repository.GameRepository
import com.maslarski.sudoku.domain.repository.LivesRepository
import com.maslarski.sudoku.domain.repository.SavedGameSummary
import com.maslarski.sudoku.domain.usecase.StartNewGameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    val savedGames: List<SavedGameSummary> = emptyList(),
    val lives: Lives? = null,
    val generating: Boolean = false,
    val confirmReplace: Boolean = false,
    /** Set when a replacement was refused because the abort penalty could not be paid. */
    val replaceBlocked: Boolean = false,
    /** Set when generating or saving a new game threw; any charge has been refunded. */
    val startFailed: Boolean = false,
) {
    val savedForSelection: SavedGameSummary? get() = savedGames.firstOrNull { it.gridSize == gridSize }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val livesRepository: LivesRepository,
    private val startNewGame: StartNewGameUseCase,
) : ViewModel() {

    private val selection = MutableStateFlow(HomeUiState())

    val uiState: StateFlow<HomeUiState> = combine(
        selection,
        gameRepository.observeSavedGames(),
        livesRepository.lives,
    ) { sel, saved, lives ->
        sel.copy(savedGames = saved.sortedByDescending { it.lastPlayedEpochMillis }, lives = lives)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _navigateToGame = MutableSharedFlow<GridSize>(extraBufferCapacity = 1)
    val navigateToGame: SharedFlow<GridSize> = _navigateToGame.asSharedFlow()

    fun selectGridSize(gridSize: GridSize) = selection.update { it.copy(gridSize = gridSize) }
    fun selectDifficulty(difficulty: Difficulty) = selection.update { it.copy(difficulty = difficulty) }

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
        _navigateToGame.tryEmit(gridSize)
    }

    private fun startGame(
        beforeSave: suspend () -> Boolean = { true },
        onSaveFailed: suspend () -> Unit = {},
    ) {
        if (selection.value.generating) return
        val (gridSize, difficulty) = selection.value.let { it.gridSize to it.difficulty }
        selection.update { it.copy(generating = true) }
        viewModelScope.launch {
            try {
                if (startNewGame(gridSize, difficulty, beforeSave, onSaveFailed) == null) {
                    selection.update { it.copy(replaceBlocked = true) }
                    return@launch
                }
                _navigateToGame.emit(gridSize)
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
