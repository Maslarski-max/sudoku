package com.maslarski.sudoku.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.maslarski.sudoku.domain.model.Lives
import com.maslarski.sudoku.domain.repository.LivesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lives are a global resource stored in DataStore. They never regenerate over time: once spent, the
 * only way to get more is the 5-lives consumable or the Unlimited Lives unlock in the store.
 */
@Singleton
class DataStoreLivesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : LivesRepository {

    override val lives: Flow<Lives> = dataStore.data.map { it.toLives() }

    override suspend fun consumeLife(): Boolean {
        var consumed = false
        dataStore.edit { prefs ->
            prefs.remove(LEGACY_KEY_REGEN_ANCHOR)
            val current = prefs.toLives()
            if (current.unlimited) {
                consumed = true
                return@edit
            }
            if (current.count <= 0) return@edit
            prefs[KEY_COUNT] = current.count - 1
            consumed = true
        }
        return consumed
    }

    override suspend fun addLives(count: Int) {
        dataStore.edit { prefs ->
            prefs.remove(LEGACY_KEY_REGEN_ANCHOR)
            prefs[KEY_COUNT] = prefs.toLives().count + count
        }
    }

    override suspend fun setUnlimited(unlimited: Boolean) {
        dataStore.edit { it[KEY_UNLIMITED] = unlimited }
    }

    private fun Preferences.toLives() = Lives(
        count = this[KEY_COUNT] ?: Lives.STARTING_LIVES,
        unlimited = this[KEY_UNLIMITED] ?: false,
    )

    private companion object {
        val KEY_COUNT = intPreferencesKey("lives_count")
        val KEY_UNLIMITED = booleanPreferencesKey("lives_unlimited")
        /** Written by versions that regenerated lives on a timer; cleared on the next write. */
        val LEGACY_KEY_REGEN_ANCHOR = longPreferencesKey("lives_regen_anchor")
    }
}
