package com.maslarski.sudoku.ui.home

import com.maslarski.sudoku.domain.engine.SudokuGenerator
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Lives
import com.maslarski.sudoku.domain.repository.GameRepository
import com.maslarski.sudoku.domain.repository.LivesRepository
import com.maslarski.sudoku.domain.repository.SavedGameSummary
import com.maslarski.sudoku.domain.usecase.StartNewGameUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val games = FakeGameRepository()
    private val lives = FakeLivesRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = HomeViewModel(
        gameRepository = games,
        livesRepository = lives,
        startNewGame = StartNewGameUseCase(SudokuGenerator(), games, dispatcher),
    )

    private fun saveInProgress(gridSize: GridSize) {
        val puzzle = SudokuGenerator().generate(gridSize, Difficulty.EASY, seed = 3L)
        games.saved.update { it + (gridSize to GameState.fromPuzzle(puzzle, nowEpochMillis = 0L)) }
    }

    @Test
    fun `replacing an in-progress game of the selected size costs one life`() = runTest(dispatcher) {
        saveInProgress(GridSize.NINE)
        val vm = viewModel()
        val collector = backgroundScope.launchCollect(vm)
        runCurrent()

        vm.onNewGameClicked()
        runCurrent()
        assertTrue(vm.uiState.value.confirmReplace)
        assertEquals(0, lives.consumed)

        vm.confirmReplace()
        runCurrent()
        assertEquals(1, lives.consumed)
        assertEquals(Lives.STARTING_LIVES - 1, vm.uiState.value.lives?.count)
        assertFalse(vm.uiState.value.confirmReplace)
        collector.cancel()
    }

    @Test
    fun `starting a game when no saved game of that size exists is free`() = runTest(dispatcher) {
        saveInProgress(GridSize.TWELVE)
        val vm = viewModel()
        val collector = backgroundScope.launchCollect(vm)
        runCurrent()

        vm.onNewGameClicked()
        runCurrent()
        assertFalse(vm.uiState.value.confirmReplace)
        assertEquals(0, lives.consumed)
        assertTrue(games.saved.value.containsKey(GridSize.NINE))
        collector.cancel()
    }

    @Test
    fun `dismissing the replace dialog charges nothing`() = runTest(dispatcher) {
        saveInProgress(GridSize.NINE)
        val vm = viewModel()
        val collector = backgroundScope.launchCollect(vm)
        runCurrent()

        vm.onNewGameClicked()
        vm.dismissReplace()
        runCurrent()
        assertEquals(0, lives.consumed)
        collector.cancel()
    }

    @Test
    fun `unlimited lives are never charged for replacing a game`() = runTest(dispatcher) {
        lives.state.value = Lives(count = 0, unlimited = true, nextRegenAtEpochMillis = null)
        saveInProgress(GridSize.NINE)
        val vm = viewModel()
        val collector = backgroundScope.launchCollect(vm)
        runCurrent()

        vm.onNewGameClicked()
        vm.confirmReplace()
        runCurrent()
        assertEquals(0, lives.consumed)
        collector.cancel()
    }

    private fun CoroutineScope.launchCollect(vm: HomeViewModel) = launch { vm.uiState.collect {} }
}

private class FakeGameRepository : GameRepository {
    val saved = MutableStateFlow<Map<GridSize, GameState>>(emptyMap())
    override fun observeSavedGames(): Flow<List<SavedGameSummary>> = saved.map { m ->
        m.values.map {
            SavedGameSummary(it.gridSize, it.difficulty, it.elapsedMillis, 0f, it.lastPlayedEpochMillis)
        }
    }
    override suspend fun load(gridSize: GridSize): GameState? = saved.value[gridSize]
    override suspend fun save(state: GameState) = saved.update { it + (state.gridSize to state) }
    override suspend fun delete(gridSize: GridSize) = saved.update { it - gridSize }
}

private class FakeLivesRepository : LivesRepository {
    val state = MutableStateFlow(Lives(count = Lives.STARTING_LIVES, unlimited = false, nextRegenAtEpochMillis = null))
    var consumed = 0
    override val lives: Flow<Lives> get() = state
    override suspend fun consumeLife(): Boolean {
        val current = state.value
        if (current.unlimited) return true
        if (current.count <= 0) return false
        consumed++
        state.update { it.copy(count = it.count - 1) }
        return true
    }
    override suspend fun addLives(count: Int) = state.update { it.copy(count = it.count + count) }
    override suspend fun setUnlimited(unlimited: Boolean) = state.update { it.copy(unlimited = unlimited) }
}
