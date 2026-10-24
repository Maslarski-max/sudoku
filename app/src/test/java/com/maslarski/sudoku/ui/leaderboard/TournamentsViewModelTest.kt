package com.maslarski.sudoku.ui.leaderboard

import androidx.lifecycle.ViewModelStore
import com.maslarski.sudoku.domain.model.TournamentBoard
import com.maslarski.sudoku.domain.model.TournamentSnapshot
import com.maslarski.sudoku.domain.repository.TournamentRepository
import com.maslarski.sudoku.domain.time.EpochClock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
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
}

private class FakeTournamentRepository : TournamentRepository {
    override suspend fun record(board: TournamentBoard, completedAtEpochMillis: Long, points: Int) =
        Result.success(Unit)

    override suspend fun flushPending() = Unit

    override suspend fun load(
        board: TournamentBoard,
        period: String,
        limit: Int,
        serverOnly: Boolean,
    ): Result<TournamentSnapshot> =
        if (board == TournamentBoard.CHAMPIONSHIP) {
            Result.failure(IllegalStateException("offline"))
        } else {
            Result.success(TournamentSnapshot(board, period, emptyList(), null))
        }
}
