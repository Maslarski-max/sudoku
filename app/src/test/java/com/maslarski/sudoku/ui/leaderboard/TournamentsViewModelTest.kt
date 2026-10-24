package com.maslarski.sudoku.ui.leaderboard

import androidx.lifecycle.ViewModelStore
import com.maslarski.sudoku.domain.model.TournamentBoard
import com.maslarski.sudoku.domain.model.TournamentEntry
import com.maslarski.sudoku.domain.model.TournamentSnapshot
import com.maslarski.sudoku.domain.repository.TournamentRepository
import com.maslarski.sudoku.domain.time.EpochClock
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TournamentsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `championship error is shown after selecting its tab`() = runTest(dispatcher) {
        val viewModel = TournamentsViewModel(
            repository = FakeTournamentRepository(),
            clock = EpochClock { Instant.parse("2024-03-15T12:00:00Z").toEpochMilli() },
        ).also { store.put("tournaments", it) }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.arenaError)
        assertTrue(viewModel.uiState.value.championshipError)
        assertFalse(viewModel.uiState.value.error)

        viewModel.select(TournamentBoard.CHAMPIONSHIP)

        assertTrue(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.championship == null)
    }

    @Test
    fun `boards load while pending uploads are suspended`() = runTest(dispatcher) {
        val flushStarted = CompletableDeferred<Unit>()
        val flushGate = CompletableDeferred<Unit>()
        val viewModel = TournamentsViewModel(
            repository = FakeTournamentRepository(
                flush = {
                    flushStarted.complete(Unit)
                    flushGate.await()
                    0
                },
                failChampionship = false,
            ),
            clock = EpochClock { Instant.parse("2024-03-15T12:00:00Z").toEpochMilli() },
        ).also { store.put("tournaments", it) }

        runCurrent()

        assertTrue(flushStarted.isCompleted)
        assertFalse(viewModel.uiState.value.loading)
        assertNotNull(viewModel.uiState.value.arena)
        assertNotNull(viewModel.uiState.value.championship)

        flushGate.complete(Unit)
        runCurrent()
    }

    @Test
    fun `successful pending flush reloads current board snapshots`() = runTest(dispatcher) {
        val flushStarted = CompletableDeferred<Unit>()
        val flushGate = CompletableDeferred<Int>()
        val repository = FakeTournamentRepository(
            flush = {
                flushStarted.complete(Unit)
                flushGate.await()
            },
            failChampionship = false,
        )
        val month = "2024-03"
        val year = "2024"
        repository.snapshots[TournamentBoard.ARENA to month] =
            TournamentSnapshot(TournamentBoard.ARENA, month, emptyList(), null)
        repository.snapshots[TournamentBoard.CHAMPIONSHIP to year] =
            TournamentSnapshot(TournamentBoard.CHAMPIONSHIP, year, emptyList(), null)
        val viewModel = TournamentsViewModel(
            repository = repository,
            clock = EpochClock { Instant.parse("2024-03-15T12:00:00Z").toEpochMilli() },
        ).also { store.put("tournaments", it) }

        runCurrent()

        assertTrue(flushStarted.isCompleted)
        assertFalse(viewModel.uiState.value.loading)
        assertEquals(4, repository.requests.size)
        assertTrue(viewModel.uiState.value.arena?.entries.isNullOrEmpty())
        assertTrue(viewModel.uiState.value.championship?.entries.isNullOrEmpty())

        val arenaEntry = TournamentEntry(1, "player", "Player", 1500, 1, true)
        val championshipEntry = TournamentEntry(1, "player", "Player", 2500, 1, true)
        repository.snapshots[TournamentBoard.ARENA to month] =
            TournamentSnapshot(TournamentBoard.ARENA, month, listOf(arenaEntry), arenaEntry)
        repository.snapshots[TournamentBoard.CHAMPIONSHIP to year] =
            TournamentSnapshot(TournamentBoard.CHAMPIONSHIP, year, listOf(championshipEntry), championshipEntry)
        flushGate.complete(1)
        runCurrent()

        assertEquals(2, repository.requests.count { it == TournamentBoard.ARENA to month })
        assertEquals(2, repository.requests.count { it == TournamentBoard.CHAMPIONSHIP to year })
        assertEquals(1500, viewModel.uiState.value.arena?.entries?.first()?.points)
        assertEquals(2500, viewModel.uiState.value.championship?.entries?.first()?.points)
        assertFalse(viewModel.uiState.value.loading)
    }
}

private class FakeTournamentRepository(
    private val flush: suspend () -> Int = { 0 },
    private val failChampionship: Boolean = true,
) : TournamentRepository {
    val requests = mutableListOf<Pair<TournamentBoard, String>>()
    val snapshots = mutableMapOf<Pair<TournamentBoard, String>, TournamentSnapshot>()

    override suspend fun submitCompletion(state: com.maslarski.sudoku.domain.model.GameState) =
        Result.success(Unit)

    override suspend fun flushPending() = flush()

    override suspend fun load(
        board: TournamentBoard,
        period: String,
        limit: Int,
        serverOnly: Boolean,
    ): Result<TournamentSnapshot> {
        requests += board to period
        if (board == TournamentBoard.CHAMPIONSHIP && failChampionship) {
            return Result.failure(IllegalStateException("offline"))
        }
        return Result.success(
            snapshots[board to period] ?: TournamentSnapshot(board, period, emptyList(), null),
        )
    }
}
