package com.maslarski.sudoku.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maslarski.sudoku.domain.repository.DailyChallengeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreDailyChallengeRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : DailyChallengeRepository {

    override val stampedDates: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[STAMPED_DATES].orEmpty().split(',').filter(String::isNotBlank).toSet()
    }

    override suspend fun recordStamp(date: String) {
        dataStore.edit { preferences ->
            val dates = preferences[STAMPED_DATES].orEmpty().split(',').filter(String::isNotBlank).toSet()
            preferences[STAMPED_DATES] = (dates + date).sorted().joinToString(",")
        }
    }

    private companion object {
        val STAMPED_DATES = stringPreferencesKey("daily_challenge_stamped_dates")
    }
}
