package com.maslarski.sudoku.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.maslarski.sudoku.domain.model.Lives
import com.maslarski.sudoku.domain.repository.LivesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreReviewPromptRepositoryTest {
    private val store = ReviewTestPreferences()
    private val lives = ReviewTestLivesRepository()
    private val repo = DataStoreReviewPromptRepository(store, lives)

    @Test
    fun `prompt appears on fifth completion`() = runTest {
        repeat(4) { assertFalse(repo.onPuzzleCompleted()) }
        assertTrue(repo.onPuzzleCompleted())
    }

    @Test
    fun `later schedules next prompt ten completions later`() = runTest {
        repeat(5) { repo.onPuzzleCompleted() }
        repo.later()
        repeat(9) { assertFalse(repo.onPuzzleCompleted()) }
        assertTrue(repo.onPuzzleCompleted())
    }

    @Test
    fun `never permanently disables prompt`() = runTest {
        repeat(4) { repo.onPuzzleCompleted() }
        repo.never()
        repeat(20) { assertFalse(repo.onPuzzleCompleted()) }
    }

    @Test
    fun `rate now disables prompt and grants one life`() = runTest {
        repo.rateNow()
        assertEquals(1, lives.count)
        repeat(10) { assertFalse(repo.onPuzzleCompleted()) }
    }
}

private class ReviewTestPreferences : DataStore<Preferences> {
    val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
    override val data: Flow<Preferences> get() = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value.toMutablePreferences())
        state.value = next
        return next
    }
}

private class ReviewTestLivesRepository : LivesRepository {
    var count = 0
    override val lives: Flow<Lives> = MutableStateFlow(Lives(count = 0, unlimited = false))
    override suspend fun consumeLife(): Boolean = false
    override suspend fun addLives(count: Int) { this.count += count }
    override suspend fun setUnlimited(unlimited: Boolean) = Unit
}
