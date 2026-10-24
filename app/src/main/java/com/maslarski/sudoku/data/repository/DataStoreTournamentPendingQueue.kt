package com.maslarski.sudoku.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maslarski.sudoku.domain.repository.TournamentPendingQueue
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreTournamentPendingQueue @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : TournamentPendingQueue {

    override suspend fun entries(): List<String> {
        val preferences = dataStore.data.first()
        if (preferences[LEGACY_PENDING] != null) {
            dataStore.edit { it.remove(LEGACY_PENDING) }
        }
        return preferences[PENDING].orEmpty().lines().filter(String::isNotBlank)
    }

    override suspend fun add(payload: String) {
        dataStore.edit { preferences ->
            val entries = preferences[PENDING].orEmpty().lines().filter(String::isNotBlank)
            if (payload !in entries) preferences[PENDING] = (entries + payload).joinToString("\n")
        }
    }

    override suspend fun remove(payload: String) {
        dataStore.edit { preferences ->
            val values = preferences[PENDING].orEmpty().lines().toMutableList()
            values.remove(payload)
            preferences[PENDING] = values.filter(String::isNotBlank).joinToString("\n")
        }
    }

    private companion object {
        val PENDING = stringPreferencesKey("tournament_pending_completions")
        val LEGACY_PENDING = stringPreferencesKey("tournament_pending_scores")
    }
}
