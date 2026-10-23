package com.maslarski.sudoku.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.maslarski.sudoku.domain.model.AppSettings
import com.maslarski.sudoku.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            soundEnabled = preferences[KEY_SOUND] ?: true,
            vibrationEnabled = preferences[KEY_VIBRATION] ?: true,
            showCandidates = preferences[KEY_SHOW_CANDIDATES] ?: false,
        )
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SOUND] = enabled }
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_VIBRATION] = enabled }
    }

    override suspend fun setShowCandidates(enabled: Boolean) {
        dataStore.edit { it[KEY_SHOW_CANDIDATES] = enabled }
    }

    private companion object {
        val KEY_SOUND = booleanPreferencesKey("settings_sound")
        val KEY_VIBRATION = booleanPreferencesKey("settings_vibration")
        val KEY_SHOW_CANDIDATES = booleanPreferencesKey("settings_show_candidates")
    }
}
