package com.maslarski.sudoku.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.maslarski.sudoku.domain.model.Lives
import com.maslarski.sudoku.domain.repository.LivesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lives are a global resource stored in DataStore. A free life regenerates every
 * [Lives.REGEN_INTERVAL_MILLIS] up to [Lives.REGEN_CAP]; purchased lives can exceed the cap.
 * Regeneration is computed lazily from [KEY_REGEN_ANCHOR] so no background work is needed.
 */
@Singleton
class DataStoreLivesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val clock: () -> Long,
) : LivesRepository {

    /** Re-resolves whenever preferences change and again each time a scheduled regeneration comes due. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override val lives: Flow<Lives> = dataStore.data.flatMapLatest { prefs ->
        flow {
            while (true) {
                val lives = prefs.resolve(clock()).toLives()
                emit(lives)
                val nextAt = lives.nextRegenAtEpochMillis ?: break
                delay((nextAt - clock()).coerceAtLeast(1_000L))
            }
        }
    }

    override suspend fun consumeLife(): Boolean {
        var consumed = false
        dataStore.edit { prefs ->
            val now = clock()
            val current = prefs.resolve(now)
            if (current.unlimited) {
                consumed = true
                return@edit
            }
            if (current.count <= 0) {
                prefs.write(current.count, current.anchor)
                return@edit
            }
            val next = current.count - 1
            // Regeneration starts counting from the moment we drop below the cap.
            val anchor = if (next < Lives.REGEN_CAP) current.anchor ?: now else null
            prefs.write(next, anchor)
            consumed = true
        }
        return consumed
    }

    override suspend fun addLives(count: Int) {
        dataStore.edit { prefs ->
            val now = clock()
            val current = prefs.resolve(now)
            val next = current.count + count
            prefs.write(next, if (next >= Lives.REGEN_CAP) null else current.anchor)
        }
    }

    override suspend fun setUnlimited(unlimited: Boolean) {
        dataStore.edit { it[KEY_UNLIMITED] = unlimited }
    }

    private fun MutablePreferences.write(count: Int, anchor: Long?) {
        this[KEY_COUNT] = count
        if (anchor == null) remove(KEY_REGEN_ANCHOR) else this[KEY_REGEN_ANCHOR] = anchor
    }

    private data class Resolved(val count: Int, val unlimited: Boolean, val anchor: Long?, val now: Long) {
        fun toLives() = Lives(
            count = count,
            unlimited = unlimited,
            nextRegenAtEpochMillis = if (unlimited || count >= Lives.REGEN_CAP || anchor == null) null else anchor + Lives.REGEN_INTERVAL_MILLIS,
        )
    }

    /** Applies any regeneration that has elapsed since the anchor without writing. */
    private fun Preferences.resolve(now: Long): Resolved {
        val unlimited = this[KEY_UNLIMITED] ?: false
        var count = this[KEY_COUNT] ?: Lives.STARTING_LIVES
        var anchor = this[KEY_REGEN_ANCHOR]
        if (!unlimited && count < Lives.REGEN_CAP && anchor != null) {
            val elapsed = now - anchor
            if (elapsed >= Lives.REGEN_INTERVAL_MILLIS) {
                val regained = (elapsed / Lives.REGEN_INTERVAL_MILLIS).toInt()
                val newCount = (count + regained).coerceAtMost(Lives.REGEN_CAP)
                anchor = if (newCount >= Lives.REGEN_CAP) null else anchor + regained * Lives.REGEN_INTERVAL_MILLIS
                count = newCount
            }
        }
        return Resolved(count, unlimited, anchor, now)
    }

    private companion object {
        val KEY_COUNT = intPreferencesKey("lives_count")
        val KEY_UNLIMITED = booleanPreferencesKey("lives_unlimited")
        val KEY_REGEN_ANCHOR = longPreferencesKey("lives_regen_anchor")
    }
}
