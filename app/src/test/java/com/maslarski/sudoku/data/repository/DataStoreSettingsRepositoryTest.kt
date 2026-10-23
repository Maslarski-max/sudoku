package com.maslarski.sudoku.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.maslarski.sudoku.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DataStoreSettingsRepositoryTest {
    private val store = SettingsTestPreferences()
    private val repo = DataStoreSettingsRepository(store)

    @Test
    fun `settings default to sound and vibration enabled`() = runTest {
        assertEquals(AppSettings(), repo.settings.first())
    }

    @Test
    fun `sound and vibration settings persist independently`() = runTest {
        repo.setSoundEnabled(false)
        assertEquals(AppSettings(soundEnabled = false), repo.settings.first())

        repo.setVibrationEnabled(false)
        assertEquals(AppSettings(soundEnabled = false, vibrationEnabled = false), repo.settings.first())

        repo.setSoundEnabled(true)
        assertEquals(AppSettings(soundEnabled = true, vibrationEnabled = false), repo.settings.first())

        repo.setShowCandidates(true)
        assertEquals(
            AppSettings(soundEnabled = true, vibrationEnabled = false, showCandidates = true),
            repo.settings.first(),
        )
    }

    @Test
    fun `candidate visibility persists independently`() = runTest {
        repo.setShowCandidates(true)

        assertEquals(AppSettings(showCandidates = true), repo.settings.first())
    }
}

private class SettingsTestPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
    override val data: Flow<Preferences> get() = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value.toMutablePreferences())
        state.value = next
        return next
    }
}
