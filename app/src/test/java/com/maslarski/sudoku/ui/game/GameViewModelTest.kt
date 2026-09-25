package com.maslarski.sudoku.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.maslarski.sudoku.domain.engine.GameEngine
import com.maslarski.sudoku.domain.engine.HintEngine
import com.maslarski.sudoku.domain.engine.SudokuGenerator
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.LeaderboardCategory
import com.maslarski.sudoku.domain.model.LeaderboardEntry
import com.maslarski.sudoku.domain.model.Lives
import com.maslarski.sudoku.domain.model.Score
import com.maslarski.sudoku.domain.repository.AuthRepository
import com.maslarski.sudoku.domain.repository.GameRepository
import com.maslarski.sudoku.domain.repository.LeaderboardRepository
import com.maslarski.sudoku.domain.repository.LivesRepository
import com.maslarski.sudoku.domain.repository.SavedGameSummary
import com.maslarski.sudoku.domain.usecase.SubmitScoreUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var initial: GameState
    private val games = FakeGameRepository()
    private val lives = FakeLivesRepository()
    private val leaderboard = FakeLeaderboardRepository()
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val puzzle = SudokuGenerator().generate(GridSize.NINE, Difficulty.EASY, seed = 7L)
        initial = GameState.fromPuzzle(puzzle, nowEpochMillis = 0L)
        games.saved[GridSize.NINE] = initial
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Clears the ViewModel before runTest idles the scheduler, otherwise its timer loop would never let it idle. */
    private fun vmTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            block()
        } finally {
            store.clear()
        }
    }

    private fun viewModel() = GameViewModel(
        savedStateHandle = SavedStateHandle(mapOf("gridSize" to 9)),
        gameRepository = games,
        livesRepository = lives,
        engine = GameEngine(),
        hintEngine = HintEngine(),
        submitScore = SubmitScoreUseCase(FakeAuthRepository(), leaderboard),
    ).also { store.put("game", it) }

    /** The timer ticks forever, so advance a bounded amount of virtual time instead of idling. */
    private fun settle() {
        dispatcher.scheduler.advanceTimeBy(1_000L)
        dispatcher.scheduler.runCurrent()
    }

    private fun GameState.firstEmpty(): Int = cells.indexOfFirst { it.isEmpty }

    private fun GameState.wrongValueFor(index: Int): Int = (1..9).first { it != puzzle.solution[index] }

    @Test
    fun `last mistake pauses the game with an out-of-lives dialog and blocks further input`() = vmTest {
        lives.state.value = Lives(count = 1, unlimited = false, nextRegenAtEpochMillis = null)
        val vm = viewModel()
        settle()

        val index = initial.firstEmpty()
        vm.selectCell(index)
        vm.enterNumber(initial.wrongValueFor(index))
        settle()

        val state = vm.uiState.value
        assertEquals(1, state.game?.mistakes)
        assertEquals(0, state.lives?.count)
        assertTrue(state.outOfLives)
        assertEquals(PauseReason.OUT_OF_LIVES, state.pauseReason)

        // Input is ignored while out of lives, and the user cannot force a resume.
        vm.enterNumber(initial.puzzle.solution[index])
        vm.resume()
        settle()
        assertEquals(1, vm.uiState.value.game?.moves)
        assertEquals(PauseReason.OUT_OF_LIVES, vm.uiState.value.pauseReason)
    }

    @Test
    fun `gaining a life resumes an out-of-lives game`() = vmTest {
        lives.state.value = Lives(count = 0, unlimited = false, nextRegenAtEpochMillis = null)
        val vm = viewModel()
        settle()
        assertEquals(PauseReason.OUT_OF_LIVES, vm.uiState.value.pauseReason)

        lives.addLives(5)
        settle()
        assertEquals(PauseReason.NONE, vm.uiState.value.pauseReason)
        assertFalse(vm.uiState.value.outOfLives)
    }

    @Test
    fun `unlimited lives never consume a life on mistakes`() = vmTest {
        lives.state.value = Lives(count = 0, unlimited = true, nextRegenAtEpochMillis = null)
        val vm = viewModel()
        settle()

        val index = initial.firstEmpty()
        vm.selectCell(index)
        vm.enterNumber(initial.wrongValueFor(index))
        settle()

        assertEquals(PauseReason.NONE, vm.uiState.value.pauseReason)
        assertEquals(0, lives.consumed)
    }

    @Test
    fun `completing the grid submits the score with points and time`() = vmTest {
        val vm = viewModel()
        settle()
        completeBoard(vm)

        val state = vm.uiState.value
        assertTrue(state.game?.isComplete == true)
        assertEquals(ScoreSubmission.Submitted, state.scoreSubmission)
        val submitted = leaderboard.submitted.single()
        assertEquals(state.score?.total, submitted.points)
        assertTrue(submitted.points > 0)
        assertTrue(submitted.timeMillis >= 1L)
        assertEquals(GridSize.NINE, submitted.gridSize)
        assertEquals(Difficulty.EASY, submitted.difficulty)
    }

    @Test
    fun `upload failure marks the score pending instead of crashing`() = vmTest {
        leaderboard.result = Result.failure(IOException("offline"))
        val vm = viewModel()
        settle()
        completeBoard(vm)
        assertEquals(ScoreSubmission.Pending, vm.uiState.value.scoreSubmission)
    }

    @Test
    fun `a score that does not beat the personal best is reported as such`() = vmTest {
        leaderboard.result = Result.success(false)
        val vm = viewModel()
        settle()
        completeBoard(vm)
        assertEquals(ScoreSubmission.NotPersonalBest, vm.uiState.value.scoreSubmission)
    }

    private fun completeBoard(vm: GameViewModel) {
        initial.cells.forEachIndexed { index, cell ->
            if (cell.isEmpty) {
                vm.selectCell(index)
                vm.enterNumber(initial.puzzle.solution[index])
            }
        }
        settle()
    }
}

private class FakeGameRepository : GameRepository {
    val saved = mutableMapOf<GridSize, GameState>()
    override fun observeSavedGames(): Flow<List<SavedGameSummary>> = flowOf(emptyList())
    override suspend fun load(gridSize: GridSize): GameState? = saved[gridSize]
    override suspend fun save(state: GameState) { saved[state.gridSize] = state }
    override suspend fun delete(gridSize: GridSize) { saved.remove(gridSize) }
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

private class FakeAuthRepository : AuthRepository {
    override val currentUserId: String = "test-user-1234"
    override suspend fun ensureSignedIn(): String = currentUserId
}

private class FakeLeaderboardRepository : LeaderboardRepository {
    val submitted = mutableListOf<Score>()
    var result: Result<Boolean> = Result.success(true)
    override fun observeTop(category: LeaderboardCategory, limit: Int): Flow<List<LeaderboardEntry>> = flowOf(emptyList())
    override suspend fun refresh(category: LeaderboardCategory, limit: Int): Result<Unit> = Result.success(Unit)
    override suspend fun personalBest(category: LeaderboardCategory): Score? = null
    override suspend fun submit(score: Score): Result<Boolean> {
        submitted += score
        return result
    }
}
