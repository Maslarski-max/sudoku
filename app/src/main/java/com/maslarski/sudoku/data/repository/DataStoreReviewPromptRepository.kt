package com.maslarski.sudoku.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.maslarski.sudoku.domain.repository.LivesRepository
import com.maslarski.sudoku.domain.repository.ReviewPromptRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreReviewPromptRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val livesRepository: LivesRepository,
) : ReviewPromptRepository {

    override suspend fun onPuzzleCompleted(): Boolean {
        var shouldPrompt = false
        dataStore.edit { prefs ->
            val count = (prefs[KEY_COMPLETED_COUNT] ?: 0) + 1
            val nextPromptAt = prefs[KEY_NEXT_PROMPT_AT] ?: FIRST_PROMPT_AT
            prefs[KEY_COMPLETED_COUNT] = count
            shouldPrompt = prefs[KEY_DISABLED] != true && count >= nextPromptAt
        }
        return shouldPrompt
    }

    override suspend fun rateNow() {
        dataStore.edit { it[KEY_DISABLED] = true }
        livesRepository.addLives(1)
    }

    override suspend fun later() {
        dataStore.edit { prefs ->
            prefs[KEY_NEXT_PROMPT_AT] = (prefs[KEY_COMPLETED_COUNT] ?: 0) + PROMPT_INTERVAL
        }
    }

    override suspend fun never() {
        dataStore.edit { it[KEY_DISABLED] = true }
    }

    private companion object {
        const val FIRST_PROMPT_AT = 5
        const val PROMPT_INTERVAL = 10
        val KEY_COMPLETED_COUNT = intPreferencesKey("review_completed_count")
        val KEY_NEXT_PROMPT_AT = intPreferencesKey("review_next_prompt_at")
        val KEY_DISABLED = booleanPreferencesKey("review_disabled")
    }
}
