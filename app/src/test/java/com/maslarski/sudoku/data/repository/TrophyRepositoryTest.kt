package com.maslarski.sudoku.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.maslarski.sudoku.domain.engine.SudokuGenerator
import com.maslarski.sudoku.domain.model.DailyChallenge
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Lives
import com.maslarski.sudoku.domain.model.TournamentBoard
import com.maslarski.sudoku.domain.model.TournamentSnapshot
import com.maslarski.sudoku.domain.model.TrophyCategory
import com.maslarski.sudoku.domain.repository.DailyChallengeRepository
import com.maslarski.sudoku.domain.repository.LivesRepository
import com.maslarski.sudoku.domain.repository.TournamentRepository
import com.maslarski.sudoku.domain.time.EpochClock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrophyRepositoryTest {

    @Test
    fun `trophy computation unlocks stamps and milestones and keeps current month locked`() = runTest {
        val february = (1..29).map { "2024-02-%02d".format(it) }.toSet()
        val daily = FakeDailyRepository(february)
        val lives = FakeLivesRepository()
        val repository = DataStoreTrophyRepository(
            MemoryPreferencesStore(),
            daily,
            EmptyTournamentRepository(),
            lives,
            EpochClock { Instant.parse("2024-03-01T12:00:00Z").toEpochMilli() },
        )
        val puzzle = SudokuGenerator().generate(GridSize.NINE, Difficulty.EASY, seed = 17L)
        repository.recordCompletion(GameState.fromPuzzle(puzzle, 0L).copy(iconMode = true))

        val trophies = repository.trophies.first()
        assertTrue(trophies.first { it.id == "daily_first_stamp" }.unlocked)
        assertTrue(trophies.first { it.id == "daily_week_streak" }.unlocked)
        assertTrue(trophies.first { it.id == "daily_master_2024-02" }.unlocked)
        assertFalse(trophies.first { it.id == "daily_master_2024-03" }.unlocked)
        assertTrue(trophies.first { it.id == "milestone_holiday" }.unlocked)
        assertTrue(trophies.filter { it.category == TrophyCategory.PREMIUM }.none { it.unlocked })
    }
}

private class MemoryPreferencesStore : DataStore<Preferences> {
    private val values = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = values
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(values.value)
        values.value = updated
        return updated
    }
}

private class FakeDailyRepository(initial: Set<String>) : DailyChallengeRepository {
    override val stampedDates = MutableStateFlow(initial)
    override suspend fun recordStamp(date: String) {
        stampedDates.value = stampedDates.value + date
    }
}

private class FakeLivesRepository : LivesRepository {
    override val lives = MutableStateFlow(Lives(count = Lives.STARTING_LIVES, unlimited = false))
    override suspend fun consumeLife() = true
    override suspend fun addLives(count: Int) = Unit
    override suspend fun setUnlimited(unlimited: Boolean) = Unit
}

private class EmptyTournamentRepository : TournamentRepository {
    override suspend fun record(board: TournamentBoard, completedAtEpochMillis: Long, points: Int) = Result.success(Unit)
    override suspend fun flushPending() = Unit
    override suspend fun load(board: TournamentBoard, period: String, limit: Int) =
        Result.success(TournamentSnapshot(board, period, emptyList(), null))
}
