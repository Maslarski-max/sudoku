package com.maslarski.sudoku.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.maslarski.sudoku.domain.model.Lives
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreLivesRepositoryTest {

    private val store = InMemoryPreferences()
    private val repo = DataStoreLivesRepository(store)

    @Test
    fun `lives only go down when consumed and up when bought`() = runTest {
        assertEquals(Lives.STARTING_LIVES, repo.lives.first().count)
        repeat(Lives.STARTING_LIVES) { assertTrue(repo.consumeLife()) }
        assertEquals(0, repo.lives.first().count)
        assertFalse(repo.consumeLife())
        assertEquals(0, repo.lives.first().count)

        repo.addLives(Lives.LIVES_PER_PURCHASE)
        assertEquals(Lives.LIVES_PER_PURCHASE, repo.lives.first().count)
    }

    @Test
    fun `a legacy regeneration anchor grants nothing and is cleared`() = runTest {
        val legacyAnchor = longPreferencesKey("lives_regen_anchor")
        store.state.value = mutablePreferencesOf(
            intPreferencesKey("lives_count") to 0,
            legacyAnchor to 0L, // long in the past: old builds would have regenerated to the cap
        )
        assertEquals(0, repo.lives.first().count)
        assertFalse(repo.consumeLife())
        assertEquals(0, repo.lives.first().count)
        assertNull(store.state.value[legacyAnchor])
    }

    @Test
    fun `unlimited lives are never decremented`() = runTest {
        repo.setUnlimited(true)
        assertTrue(repo.consumeLife())
        assertEquals(Lives.STARTING_LIVES, repo.lives.first().count)
        assertTrue(repo.lives.first().unlimited)
    }
}

private class InMemoryPreferences : DataStore<Preferences> {
    val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
    override val data: Flow<Preferences> get() = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value.toMutablePreferences())
        state.value = next
        return next
    }
}
