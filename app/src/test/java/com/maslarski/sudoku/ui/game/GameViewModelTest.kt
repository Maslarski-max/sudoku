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
import org.junit.Assert.assertNull
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

    private fun GameState.emptyCells(): List<Int> = cells.indices.filter { cells[it].isEmpty }

    /** Enters a wrong value into [count] distinct empty cells. */
    private fun makeMistakes(vm: GameViewModel, count: Int, skip: Int = 0) {
        initial.emptyCells().drop(skip).take(count).forEach { index ->
            vm.selectCell(index)
            vm.enterNumber(initial.wrongValueFor(index))
        }
        settle()
    }

    @Test
    fun `every third mistake costs exactly one life and resets the streak`() = vmTest {
        lives.state.value = Lives(count = 5, unlimited = false)
        val vm = viewModel()
        settle()

        makeMistakes(vm, 2)
        assertEquals(2, vm.uiState.value.game?.mistakes)
        assertEquals(2, vm.uiState.value.game?.mistakeStreak)
        assertEquals(0, lives.consumed)

        makeMistakes(vm, 1, skip = 2)
        assertEquals(3, vm.uiState.value.game?.mistakes)
        assertEquals(0, vm.uiState.value.game?.mistakeStreak)
        assertEquals(1, lives.consumed)
        assertEquals(4, vm.uiState.value.lives?.count)

        makeMistakes(vm, 3, skip = 3)
        assertEquals(6, vm.uiState.value.game?.mistakes)
        assertEquals(0, vm.uiState.value.game?.mistakeStreak)
        assertEquals(2, lives.consumed)
    }

    @Test
    fun `third mistake on the last life pauses the game with an out-of-lives dialog and blocks further input`() = vmTest {
        lives.state.value = Lives(count = 1, unlimited = false)
        val vm = viewModel()
        settle()

        makeMistakes(vm, 2)
        assertEquals(PauseReason.NONE, vm.uiState.value.pauseReason)
        assertEquals(1, vm.uiState.value.lives?.count)

        makeMistakes(vm, 1, skip = 2)
        val state = vm.uiState.value
        assertEquals(3, state.game?.mistakes)
        assertEquals(0, state.lives?.count)
        assertTrue(state.outOfLives)
        assertEquals(PauseReason.OUT_OF_LIVES, state.pauseReason)

        // Input is ignored while out of lives, and the user cannot force a resume.
        val index = initial.firstEmpty()
        vm.selectCell(index)
        vm.enterNumber(initial.puzzle.solution[index])
        vm.resume()
        settle()
        assertEquals(3, vm.uiState.value.game?.moves)
        assertEquals(PauseReason.OUT_OF_LIVES, vm.uiState.value.pauseReason)
    }

    @Test
    fun `free players get three hints per game and then the hint action is disabled`() = vmTest {
        val vm = viewModel()
        settle()
        assertEquals(3, vm.uiState.value.hintsRemaining)

        repeat(3) {
            vm.requestHint()
            vm.applyHint()
        }
        settle()
        assertEquals(3, vm.uiState.value.game?.hintsUsed)
        assertEquals(0, vm.uiState.value.hintsRemaining)
        assertFalse(vm.uiState.value.canUseHint)

        vm.requestHint()
        assertNull(vm.uiState.value.activeHint)
        vm.applyHint()
        settle()
        assertEquals(3, vm.uiState.value.game?.hintsUsed)
    }

    @Test
    fun `premium players are not limited to three hints`() = vmTest {
        lives.state.value = Lives(count = 0, unlimited = true)
        val vm = viewModel()
        settle()
        assertNull(vm.uiState.value.hintsRemaining)

        repeat(4) {
            vm.requestHint()
            vm.applyHint()
        }
        settle()
        assertEquals(4, vm.uiState.value.game?.hintsUsed)
        assertTrue(vm.uiState.value.canUseHint)
    }

    @Test
    fun `gaining a life resumes an out-of-lives game`() = vmTest {
        lives.state.value = Lives(count = 0, unlimited = false)
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
        lives.state.value = Lives(count = 0, unlimited = true)
        val vm = viewModel()
        settle()

        makeMistakes(vm, 6)

        assertEquals(PauseReason.NONE, vm.uiState.value.pauseReason)
        assertEquals(6, vm.uiState.value.game?.mistakes)
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
    val state = MutableStateFlow(Lives(count = Lives.STARTING_LIVES, unlimited = false))
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
