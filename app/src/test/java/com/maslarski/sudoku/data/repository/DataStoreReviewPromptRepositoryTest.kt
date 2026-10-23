package com.maslarski.sudoku.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreReviewPromptRepositoryTest {
    private val store = ReviewTestPreferences()
    private val repo = DataStoreReviewPromptRepository(store)

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
    fun `rate now only disables prompt`() = runTest {
        repo.rateNow()
        assertEquals(true, store.state.value[booleanPreferencesKey("review_disabled")])
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
