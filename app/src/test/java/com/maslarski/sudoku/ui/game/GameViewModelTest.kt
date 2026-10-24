package com.maslarski.sudoku.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.maslarski.sudoku.domain.engine.GameEngine
import com.maslarski.sudoku.domain.engine.HintEngine
import com.maslarski.sudoku.domain.engine.SudokuGenerator
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.DailyChallenge
import com.maslarski.sudoku.domain.model.Cell
import com.maslarski.sudoku.domain.model.AppSettings
import com.maslarski.sudoku.domain.model.AppTheme
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Variant
import com.maslarski.sudoku.domain.model.LeaderboardCategory
import com.maslarski.sudoku.domain.model.LeaderboardEntry
import com.maslarski.sudoku.domain.model.Lives
import com.maslarski.sudoku.domain.model.Score
import com.maslarski.sudoku.domain.model.TournamentBoard
import com.maslarski.sudoku.domain.model.TournamentEntry
import com.maslarski.sudoku.domain.model.TournamentSnapshot
import com.maslarski.sudoku.domain.model.Trophy
import com.maslarski.sudoku.domain.repository.AuthRepository
import com.maslarski.sudoku.domain.repository.GameRepository
import com.maslarski.sudoku.domain.repository.GameFeedback
import com.maslarski.sudoku.domain.repository.LeaderboardRepository
import com.maslarski.sudoku.domain.repository.LivesRepository
import com.maslarski.sudoku.domain.repository.ReviewPromptRepository
import com.maslarski.sudoku.domain.repository.SavedGameSummary
import com.maslarski.sudoku.domain.repository.SettingsRepository
import com.maslarski.sudoku.domain.repository.TournamentRepository
import com.maslarski.sudoku.domain.repository.TrophyRepository
import com.maslarski.sudoku.domain.time.EpochClock
import com.maslarski.sudoku.domain.usecase.SubmitScoreUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
    private val completionEvents = mutableListOf<String>()
    private val leaderboard = FakeLeaderboardRepository(completionEvents)
    private val tournaments = FakeTournamentRepository(completionEvents)
    private val trophies = FakeTrophyRepository(completionEvents)
    private val reviewPrompt = FakeReviewPromptRepository()
    private val feedback = RecordingGameFeedback()
    private val settings = FakeSettingsRepository()
    private var clockOffsetMillis = 0L
    private val clock = EpochClock {
        1_700_000_000_000L + dispatcher.scheduler.currentTime + clockOffsetMillis
    }
    private val store = ViewModelStore()
    private lateinit var applicationScope: CoroutineScope

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
        applicationScope = this
        try {
            block()
        } finally {
            store.clear()
        }
    }

    private fun viewModel(
        submitScore: SubmitScoreUseCase = SubmitScoreUseCase(FakeAuthRepository(), leaderboard),
        slotId: Int = GridSize.NINE.size,
    ) = GameViewModel(
        savedStateHandle = SavedStateHandle(mapOf("gridSize" to slotId)),
        gameRepository = games,
        livesRepository = lives,
        engine = GameEngine(),
        hintEngine = HintEngine(),
        submitScore = submitScore,
        reviewPrompt = reviewPrompt,
        feedback = feedback,
        settingsRepository = settings,
        clock = clock,
        tournamentRepository = tournaments,
        trophyRepository = trophies,
        applicationScope = applicationScope,
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
    fun `request hint targets selected cell and preserves selection`() = vmTest {
        val vm = viewModel()
        settle()
        val nextIndex = HintEngine().nextHint(initial).index
        val selected = initial.emptyCells().first { it != nextIndex }
        vm.selectCell(selected)
        vm.requestHint()
        assertEquals(selected, vm.uiState.value.selectedIndex)
        assertEquals(selected, vm.uiState.value.activeHint?.index)
        assertEquals(initial.puzzle.solution[selected], vm.uiState.value.activeHint?.value)
    }

    @Test
    fun `request hint without selection selects the next hint cell`() = vmTest {
        val vm = viewModel()
        settle()
        val expected = HintEngine().nextHint(initial).index
        vm.requestHint()
        assertEquals(expected, vm.uiState.value.selectedIndex)
        assertEquals(expected, vm.uiState.value.activeHint?.index)
    }

    @Test
    fun `selecting a cell records selection feedback`() = vmTest {
        val vm = viewModel()
        settle()

        vm.selectCell(initial.firstEmpty())

        assertEquals(listOf("select"), feedback.events)
    }

    @Test
    fun `wrong entry records mistake feedback and correct entry does not`() = vmTest {
        val vm = viewModel()
        settle()
        val index = initial.firstEmpty()
        vm.selectCell(index)
        feedback.events.clear()
        vm.enterNumber(initial.wrongValueFor(index))
        assertEquals(listOf("mistake"), feedback.events)

        val correctIndex = initial.emptyCells().drop(1).first()
        vm.selectCell(correctIndex)
        feedback.events.clear()
        vm.enterNumber(initial.puzzle.solution[correctIndex])
        assertTrue(feedback.events.isEmpty())
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
        assertNull(vm.uiState.value.game?.outOfLivesDeadlineEpochMillis)
        assertNull(games.saved[GridSize.NINE]?.outOfLivesDeadlineEpochMillis)
    }

    @Test
    fun `buying lives during countdown resumes without changing the board`() = vmTest {
        lives.state.value = Lives(count = 0, unlimited = false)
        val vm = viewModel()
        dispatcher.scheduler.runCurrent()
        assertEquals(60, vm.uiState.value.outOfLivesSecondsLeft)
        assertEquals(
            clock.now() + 60_000L,
            games.saved[GridSize.NINE]?.outOfLivesDeadlineEpochMillis,
        )
        val before = vm.uiState.value.game!!.cells

        dispatcher.scheduler.advanceTimeBy(30_000L)
        dispatcher.scheduler.runCurrent()
        assertEquals(30, vm.uiState.value.outOfLivesSecondsLeft)
        lives.addLives(5)
        settle()

        assertEquals(PauseReason.NONE, vm.uiState.value.pauseReason)
        assertNull(vm.uiState.value.outOfLivesSecondsLeft)
        assertEquals(before, vm.uiState.value.game?.cells)
    }

    @Test
    fun `countdown reset restores givens and preserves hint usage`() = vmTest {
        val comboIndices = initial.cells.indices.filter { initial.cells[it].isEmpty }.take(3)
        val engine = GameEngine()
        comboIndices.forEachIndexed { position, index ->
            initial = initial.copy(elapsedMillis = position * 1_000L)
            initial = engine.enterValue(initial, index, initial.puzzle.solution[index]).state
        }
        val previouslyComboIndex = comboIndices.last()
        initial = initial.copy(
            elapsedMillis = 123_456L,
            moves = 12,
            mistakes = 8,
            mistakeStreak = 2,
            hintsUsed = 2,
            startedAtEpochMillis = 42L,
        )
        games.saved[GridSize.NINE] = initial
        lives.state.value = Lives(count = 0, unlimited = false)
        val vm = viewModel()
        settle()
        dispatcher.scheduler.advanceTimeBy(61_000L)
        dispatcher.scheduler.runCurrent()

        val reset = vm.uiState.value.game!!
        assertEquals(initial.puzzle.givens.toList(), reset.cells.map { it.value })
        assertEquals(initial.elapsedMillis, reset.elapsedMillis)
        assertEquals(initial.moves, reset.moves)
        assertEquals(initial.mistakes, reset.mistakes)
        assertEquals(initial.mistakeStreak, reset.mistakeStreak)
        assertEquals(2, reset.hintsUsed)
        assertEquals(initial.startedAtEpochMillis, reset.startedAtEpochMillis)
        assertTrue(reset.undoStack.isEmpty())
        assertTrue(reset.redoStack.isEmpty())
        assertNull(reset.outOfLivesDeadlineEpochMillis)
        assertTrue(reset.outOfLivesExpired)
        assertEquals(0, reset.comboStreak)
        assertNull(reset.lastCorrectElapsedMillis)
        assertTrue(reset.countedCells.isEmpty())
        assertFalse(reset.cells[previouslyComboIndex].combo)
        assertEquals(reset.cells, games.saved[GridSize.NINE]?.cells)
        assertTrue(vm.uiState.value.progressReset)
        assertNull(vm.uiState.value.outOfLivesSecondsLeft)
        assertEquals(-1, vm.uiState.value.selectedIndex)
        assertNull(vm.uiState.value.activeHint)

        lives.addLives(5)
        settle()
        vm.selectCell(previouslyComboIndex)
        vm.enterNumber(initial.puzzle.solution[previouslyComboIndex])
        settle()

        val reentered = vm.uiState.value.game!!
        assertEquals(1, reentered.comboStreak)
        assertFalse(reentered.cells[previouslyComboIndex].combo)
        assertEquals(0, vm.uiState.value.score?.comboBonus)
    }

    @Test
    fun `reopened countdown uses persisted deadline`() = vmTest {
        lives.state.value = Lives(count = 0, unlimited = false)
        val deadline = clock.now() + 10_000L
        games.saved[GridSize.NINE] = initial.copy(outOfLivesDeadlineEpochMillis = deadline)
        val vm = viewModel()
        dispatcher.scheduler.runCurrent()

        assertTrue((vm.uiState.value.outOfLivesSecondsLeft ?: 60) <= 10)
        assertTrue((vm.uiState.value.outOfLivesSecondsLeft ?: 0) > 0)
        assertEquals(deadline, vm.uiState.value.game?.outOfLivesDeadlineEpochMillis)
    }

    @Test
    fun `reopened expired deadline resets immediately`() = vmTest {
        lives.state.value = Lives(count = 0, unlimited = false)
        val enteredIndex = initial.firstEmpty()
        games.saved[GridSize.NINE] = initial.copy(
            cells = initial.cells.toMutableList().also {
                it[enteredIndex] = Cell(value = initial.puzzle.solution[enteredIndex])
            },
            elapsedMillis = 90_000L,
            moves = 7,
            mistakes = 5,
            mistakeStreak = 2,
            hintsUsed = 1,
            outOfLivesDeadlineEpochMillis = clock.now() - 1L,
        )
        val vm = viewModel()
        dispatcher.scheduler.runCurrent()

        val reset = vm.uiState.value.game!!
        assertEquals(initial.puzzle.givens.toList(), reset.cells.map { it.value })
        assertEquals(90_000L, reset.elapsedMillis)
        assertEquals(7, reset.moves)
        assertEquals(5, reset.mistakes)
        assertEquals(2, reset.mistakeStreak)
        assertEquals(1, reset.hintsUsed)
        assertTrue(vm.uiState.value.progressReset)
        assertNull(reset.outOfLivesDeadlineEpochMillis)
        assertTrue(reset.outOfLivesExpired)
    }

    @Test
    fun `past deadline at load with lives available resets board and keeps stats`() = vmTest {
        lives.state.value = Lives(count = 5, unlimited = false)
        val enteredIndex = initial.firstEmpty()
        val saved = initial.copy(
            cells = initial.cells.toMutableList().also {
                it[enteredIndex] = Cell(value = initial.puzzle.solution[enteredIndex])
            },
            elapsedMillis = 72_000L,
            moves = 9,
            mistakes = 4,
            mistakeStreak = 1,
            hintsUsed = 2,
            startedAtEpochMillis = 123L,
            outOfLivesDeadlineEpochMillis = clock.now() - 1L,
        )
        games.saved[GridSize.NINE] = saved
        val loadGate = CompletableDeferred<Unit>()
        games.loadGate = loadGate
        val vm = viewModel()
        dispatcher.scheduler.runCurrent()
        assertEquals(Lives(count = 5, unlimited = false), vm.uiState.value.lives)
        loadGate.complete(Unit)
        dispatcher.scheduler.runCurrent()

        val settled = vm.uiState.value.game!!
        assertEquals(initial.puzzle.givens.toList(), settled.cells.map { it.value })
        assertEquals(saved.elapsedMillis, settled.elapsedMillis)
        assertEquals(saved.moves, settled.moves)
        assertEquals(saved.mistakes, settled.mistakes)
        assertEquals(saved.mistakeStreak, settled.mistakeStreak)
        assertEquals(saved.hintsUsed, settled.hintsUsed)
        assertEquals(saved.startedAtEpochMillis, settled.startedAtEpochMillis)
        assertNull(settled.outOfLivesDeadlineEpochMillis)
        assertFalse(settled.outOfLivesExpired)
        assertEquals(PauseReason.NONE, vm.uiState.value.pauseReason)
        assertFalse(vm.uiState.value.progressReset)
        assertEquals(settled.cells, games.saved[GridSize.NINE]?.cells)
    }

    @Test
    fun `lives restored after deadline passes settle board before countdown tick`() = vmTest {
        lives.state.value = Lives(count = 0, unlimited = false)
        val enteredIndex = initial.firstEmpty()
        val saved = initial.copy(
            cells = initial.cells.toMutableList().also {
                it[enteredIndex] = Cell(value = initial.puzzle.solution[enteredIndex])
            },
            elapsedMillis = 72_000L,
            moves = 9,
            mistakes = 4,
            mistakeStreak = 1,
            hintsUsed = 2,
            startedAtEpochMillis = 123L,
            outOfLivesDeadlineEpochMillis = clock.now() + 60_000L,
        )
        games.saved[GridSize.NINE] = saved
        val vm = viewModel()
        dispatcher.scheduler.runCurrent()
        assertEquals(60, vm.uiState.value.outOfLivesSecondsLeft)

        clockOffsetMillis = 60_001L
        lives.addLives(5)
        dispatcher.scheduler.runCurrent()

        val settled = vm.uiState.value.game!!
        assertEquals(initial.puzzle.givens.toList(), settled.cells.map { it.value })
        assertEquals(saved.elapsedMillis, settled.elapsedMillis)
        assertEquals(saved.moves, settled.moves)
        assertEquals(saved.mistakes, settled.mistakes)
        assertEquals(saved.mistakeStreak, settled.mistakeStreak)
        assertEquals(saved.hintsUsed, settled.hintsUsed)
        assertEquals(saved.startedAtEpochMillis, settled.startedAtEpochMillis)
        assertNull(settled.outOfLivesDeadlineEpochMillis)
        assertFalse(settled.outOfLivesExpired)
        assertEquals(PauseReason.NONE, vm.uiState.value.pauseReason)
        assertNull(vm.uiState.value.outOfLivesSecondsLeft)
        assertFalse(vm.uiState.value.progressReset)
        assertEquals(settled.cells, games.saved[GridSize.NINE]?.cells)
    }

    @Test
    fun `expired reset survives reopen and clears expired flag when lives return`() = vmTest {
        lives.state.value = Lives(count = 0, unlimited = false)
        val enteredIndex = initial.firstEmpty()
        initial = initial.copy(
            cells = initial.cells.toMutableList().also {
                it[enteredIndex] = Cell(value = initial.puzzle.solution[enteredIndex])
            },
            moves = 3,
            hintsUsed = 1,
        )
        games.saved[GridSize.NINE] = initial

        viewModel()
        dispatcher.scheduler.runCurrent()
        dispatcher.scheduler.advanceTimeBy(61_000L)
        dispatcher.scheduler.runCurrent()
        assertTrue(games.saved[GridSize.NINE]?.outOfLivesExpired == true)

        store.clear()
        val reopenedViewModel = viewModel()
        dispatcher.scheduler.runCurrent()

        assertTrue(reopenedViewModel.uiState.value.progressReset)
        assertNull(reopenedViewModel.uiState.value.outOfLivesSecondsLeft)
        assertNull(reopenedViewModel.uiState.value.game?.outOfLivesDeadlineEpochMillis)
        assertTrue(reopenedViewModel.uiState.value.game?.outOfLivesExpired == true)

        lives.addLives(5)
        dispatcher.scheduler.runCurrent()

        assertEquals(PauseReason.NONE, reopenedViewModel.uiState.value.pauseReason)
        assertFalse(reopenedViewModel.uiState.value.progressReset)
        assertFalse(reopenedViewModel.uiState.value.game!!.outOfLivesExpired)
        assertNull(reopenedViewModel.uiState.value.game?.outOfLivesDeadlineEpochMillis)
        assertEquals(
            initial.puzzle.givens.toList(),
            reopenedViewModel.uiState.value.game?.cells?.map { it.value },
        )
    }

    @Test
    fun `non-premium candidates toggle opens upsell without persisting`() = vmTest {
        val vm = viewModel()
        settle()
        assertFalse(vm.uiState.value.showCandidates)

        vm.toggleCandidates()
        settle()

        assertFalse(vm.uiState.value.showCandidates)
        assertTrue(vm.uiState.value.showCandidatesUpsell)
        assertFalse(settings.state.value.showCandidates)
        assertEquals(0, settings.candidateWrites)
    }

    @Test
    fun `candidates toggle is ignored while lives are loading`() = vmTest {
        lives.deferInitialEmission = true
        val vm = viewModel()
        dispatcher.scheduler.runCurrent()

        vm.toggleCandidates()

        assertFalse(vm.uiState.value.showCandidatesUpsell)
        assertFalse(vm.uiState.value.showCandidates)
        assertEquals(0, settings.candidateWrites)
    }

    @Test
    fun `premium candidates toggle persists and enables display`() = vmTest {
        lives.state.value = lives.state.value.copy(unlimited = true)
        val vm = viewModel()
        settle()

        vm.toggleCandidates()
        settle()

        assertTrue(vm.uiState.value.showCandidates)
        assertTrue(settings.state.value.showCandidates)
        assertFalse(vm.uiState.value.showCandidatesUpsell)
        assertEquals(1, settings.candidateWrites)
    }

    @Test
    fun `saved candidates preference is hidden without premium`() = vmTest {
        settings.state.value = settings.state.value.copy(showCandidates = true)
        val vm = viewModel()
        settle()

        assertFalse(vm.uiState.value.showCandidates)
        assertFalse(vm.uiState.value.showCandidatesUpsell)
    }

    @Test
    fun `dismissing candidates upsell clears the flag`() = vmTest {
        val vm = viewModel()
        settle()
        vm.toggleCandidates()
        settle()

        assertTrue(vm.uiState.value.showCandidatesUpsell)
        vm.dismissCandidatesUpsell()
        settle()

        assertFalse(vm.uiState.value.showCandidatesUpsell)
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
        assertEquals(TournamentBoard.CHAMPIONSHIP, tournaments.records.single().first)
    }

    @Test
    fun `classic submit precedes tournament write and survives view model clearing`() = vmTest {
        val recordStarted = CompletableDeferred<Unit>()
        val recordGate = CompletableDeferred<Unit>()
        tournaments.recordStarted = recordStarted
        tournaments.recordGate = recordGate

        val vm = viewModel()
        settle()
        completeBoard(vm)

        assertTrue(recordStarted.isCompleted)
        assertEquals(listOf("trophy", "classic", "tournament_CHAMPIONSHIP"), completionEvents)
        assertEquals(1, leaderboard.submitted.size)

        store.clear()
        recordGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, leaderboard.submitted.size)
        assertEquals(5, reviewPrompt.completedCount)
    }

    @Test
    fun `fifth completed puzzle shows review prompt`() = vmTest {
        val vm = viewModel()
        settle()
        completeBoard(vm)

        assertEquals(5, reviewPrompt.completedCount)
        assertTrue(vm.uiState.value.showReviewPrompt)
    }

    @Test
    fun `review prompt persistence failure does not block score submission`() = vmTest {
        reviewPrompt.failOnCompletion = true
        val vm = viewModel()
        settle()
        completeBoard(vm)

        assertEquals(ScoreSubmission.Submitted, vm.uiState.value.scoreSubmission)
        assertEquals(1, leaderboard.submitted.size)
        assertFalse(vm.uiState.value.showReviewPrompt)
    }

    @Test
    fun `sign-in failure still queues an anonymous score for upload`() = vmTest {
        leaderboard.result = Result.failure(IOException("queued"))
        val vm = viewModel(submitScore = SubmitScoreUseCase(FailingAuthRepository(), leaderboard))
        settle()
        completeBoard(vm)
        assertEquals(ScoreSubmission.Pending, vm.uiState.value.scoreSubmission)
        val submitted = leaderboard.submitted.single()
        assertTrue(submitted.isAnonymous)
        assertTrue(submitted.points > 0)
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

    @Test
    fun `variation completion remains local and does not submit a leaderboard score`() = vmTest {
        val index = initial.cells.indexOfFirst { it.isEmpty }
        val givens = initial.puzzle.solution.copyOf().also { it[index] = 0 }
        val puzzle = initial.puzzle.copy(givens = givens, variant = Variant.OUTSIDE)
        games.variantSaved[Variant.OUTSIDE.slotId] = GameState.fromPuzzle(puzzle, nowEpochMillis = 0L)
        val vm = viewModel(slotId = Variant.OUTSIDE.slotId)
        settle()

        vm.selectCell(index)
        vm.enterNumber(puzzle.solution[index])
        settle()

        assertTrue(vm.uiState.value.game?.isComplete == true)
        assertEquals(ScoreSubmission.LocalOnly, vm.uiState.value.scoreSubmission)
        assertTrue(leaderboard.submitted.isEmpty())
        assertEquals(listOf(TournamentBoard.CHAMPIONSHIP), tournaments.records.map { it.first })
    }

    @Test
    fun `samurai completion routes to arena and championship only`() = vmTest {
        val puzzle = SudokuGenerator().generateVariant(Variant.SAMURAI, Difficulty.EASY, seed = 19L)
        val emptyIndex = puzzle.givens.indexOfFirst { it == 0 }
        val givens = puzzle.solution.copyOf().also { it[emptyIndex] = 0 }
        val game = GameState.fromPuzzle(puzzle.copy(givens = givens), nowEpochMillis = 0L)
        games.variantSaved[Variant.SAMURAI.slotId] = game
        val vm = viewModel(slotId = Variant.SAMURAI.slotId)
        settle()

        vm.selectCell(emptyIndex)
        vm.enterNumber(puzzle.solution[emptyIndex])
        settle()

        assertTrue(vm.uiState.value.game?.isComplete == true)
        assertTrue(leaderboard.submitted.isEmpty())
        assertEquals(
            setOf(TournamentBoard.ARENA, TournamentBoard.CHAMPIONSHIP),
            tournaments.records.map { it.first }.toSet(),
        )
    }

    @Test
    fun `daily completion stamps its date and routes only to championship`() = vmTest {
        val emptyIndex = initial.puzzle.givens.indexOfFirst { it == 0 }
        val givens = initial.puzzle.solution.copyOf().also { it[emptyIndex] = 0 }
        val puzzle = initial.puzzle.copy(givens = givens)
        val date = "2024-02-29"
        games.variantSaved[DailyChallenge.SLOT_ID] =
            GameState.fromPuzzle(puzzle, nowEpochMillis = 0L, dailyDate = date)
        val vm = viewModel(slotId = DailyChallenge.SLOT_ID)
        settle()

        vm.selectCell(emptyIndex)
        vm.enterNumber(puzzle.solution[emptyIndex])
        settle()

        assertTrue(vm.uiState.value.game?.isComplete == true)
        assertEquals(ScoreSubmission.Championship, vm.uiState.value.scoreSubmission)
        assertTrue(leaderboard.submitted.isEmpty())
        assertEquals(date, trophies.completions.single().dailyDate)
        assertEquals(listOf(TournamentBoard.CHAMPIONSHIP), tournaments.records.map { it.first })
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
    val variantSaved = mutableMapOf<Int, GameState>()
    var loadGate: CompletableDeferred<Unit>? = null
    override fun observeSavedGames(): Flow<List<SavedGameSummary>> = flowOf(emptyList())
    override suspend fun load(gridSize: GridSize): GameState? {
        loadGate?.await()
        return saved[gridSize]
    }
    override suspend fun loadSlot(slotId: Int): GameState? {
        loadGate?.await()
        return if (slotId == DailyChallenge.SLOT_ID || Variant.fromSlotId(slotId) != null) variantSaved[slotId]
        else GridSize.entries.firstOrNull { it.size == slotId }?.let { saved[it] }
    }
    override suspend fun save(state: GameState) {
        if (state.dailyDate != null || state.variant != null) variantSaved[state.slotId] = state
        else saved[state.gridSize] = state
    }
    override suspend fun delete(gridSize: GridSize) { saved.remove(gridSize) }
    override suspend fun deleteSlot(slotId: Int) {
        if (slotId == DailyChallenge.SLOT_ID || Variant.fromSlotId(slotId) != null) variantSaved.remove(slotId)
        else GridSize.entries.firstOrNull { it.size == slotId }?.let(saved::remove)
    }
}

private class FakeLivesRepository : LivesRepository {
    val state = MutableStateFlow(Lives(count = Lives.STARTING_LIVES, unlimited = false))
    var deferInitialEmission = false
    private val delayedLives = MutableSharedFlow<Lives>(extraBufferCapacity = 1)
    var consumed = 0
    override val lives: Flow<Lives> get() = if (deferInitialEmission) delayedLives else state
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

private class FakeReviewPromptRepository(var completedCount: Int = 4) : ReviewPromptRepository {
    var failOnCompletion = false
    override suspend fun onPuzzleCompleted(): Boolean {
        if (failOnCompletion) error("review write failed")
        completedCount++
        return completedCount >= 5
    }
    override suspend fun rateNow() = Unit
    override suspend fun later() = Unit
    override suspend fun never() = Unit
}

private class FakeSettingsRepository : SettingsRepository {
    val state = MutableStateFlow(AppSettings())
    var candidateWrites = 0
    override val settings: Flow<AppSettings> get() = state
    override suspend fun setSoundEnabled(enabled: Boolean) {
        state.value = state.value.copy(soundEnabled = enabled)
    }
    override suspend fun setVibrationEnabled(enabled: Boolean) {
        state.value = state.value.copy(vibrationEnabled = enabled)
    }
    override suspend fun setShowCandidates(enabled: Boolean) {
        candidateWrites++
        state.value = state.value.copy(showCandidates = enabled)
    }
    override suspend fun setTheme(theme: AppTheme) {
        state.value = state.value.copy(theme = theme)
    }
    override suspend fun setDarkMode(darkMode: Boolean) {
        state.value = state.value.copy(darkMode = darkMode)
    }
    override suspend fun setIconMode(iconMode: Boolean) {
        state.value = state.value.copy(iconMode = iconMode)
    }
}

private class RecordingGameFeedback : GameFeedback {
    val events = mutableListOf<String>()
    override fun cellSelected() { events += "select" }
    override fun mistake() { events += "mistake" }
    override fun puzzleCompleted() { events += "completed" }
}

private class FakeAuthRepository : AuthRepository {
    override val currentUserId: String = "test-user-1234"
    override suspend fun ensureSignedIn(): String = currentUserId
}

private class FailingAuthRepository : AuthRepository {
    override val currentUserId: String? = null
    override suspend fun ensureSignedIn(): String = throw IOException("auth unreachable")
}

private class FakeLeaderboardRepository(
    private val completionEvents: MutableList<String>,
) : LeaderboardRepository {
    val submitted = mutableListOf<Score>()
    var result: Result<Boolean> = Result.success(true)
    override fun observeTop(category: LeaderboardCategory, limit: Int): Flow<List<LeaderboardEntry>> = flowOf(emptyList())
    override suspend fun refresh(category: LeaderboardCategory, limit: Int): Result<Unit> = Result.success(Unit)
    override suspend fun personalBest(category: LeaderboardCategory): Score? = null
    override suspend fun submit(score: Score): Result<Boolean> {
        completionEvents += "classic"
        submitted += score
        return result
    }
}

private class FakeTournamentRepository(
    private val completionEvents: MutableList<String>,
) : TournamentRepository {
    val records = mutableListOf<Triple<TournamentBoard, Long, Int>>()
    var recordStarted: CompletableDeferred<Unit>? = null
    var recordGate: CompletableDeferred<Unit>? = null
    override suspend fun record(board: TournamentBoard, completedAtEpochMillis: Long, points: Int): Result<Unit> {
        completionEvents += "tournament_${board.name}"
        records += Triple(board, completedAtEpochMillis, points)
        recordStarted?.complete(Unit)
        recordGate?.await()
        return Result.success(Unit)
    }
    override suspend fun flushPending() = Unit
    override suspend fun load(board: TournamentBoard, period: String, limit: Int, serverOnly: Boolean) =
        Result.success(TournamentSnapshot(board, period, emptyList<TournamentEntry>(), null))
}

private class FakeTrophyRepository(
    private val completionEvents: MutableList<String>,
) : TrophyRepository {
    val completions = mutableListOf<GameState>()
    override val trophies: Flow<List<Trophy>> = flowOf(emptyList())
    override suspend fun recordCompletion(state: GameState) {
        completionEvents += "trophy"
        completions += state
    }
    override suspend fun refreshPeriodAwards() = Unit
}
