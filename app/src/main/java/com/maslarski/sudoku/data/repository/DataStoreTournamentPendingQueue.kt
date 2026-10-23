package com.maslarski.sudoku.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maslarski.sudoku.domain.model.PendingTournamentScore
import com.maslarski.sudoku.domain.model.TournamentBoard
import com.maslarski.sudoku.domain.repository.TournamentPendingQueue
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreTournamentPendingQueue @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : TournamentPendingQueue {

    override suspend fun entries(): List<PendingTournamentScore> =
        dataStore.data.first()[PENDING].orEmpty().lines().mapNotNull(::decode)

    override suspend fun add(entry: PendingTournamentScore) {
        dataStore.edit { preferences ->
            preferences[PENDING] = (preferences[PENDING].orEmpty().lines() + encode(entry))
                .filter(String::isNotBlank)
                .joinToString("\n")
        }
    }

    override suspend fun remove(entry: PendingTournamentScore) {
        dataStore.edit { preferences ->
            val values = preferences[PENDING].orEmpty().lines().toMutableList()
            values.remove(encode(entry))
            preferences[PENDING] = values.filter(String::isNotBlank).joinToString("\n")
        }
    }

    private fun encode(entry: PendingTournamentScore): String =
        "${entry.board.name}|${entry.period}|${entry.points}"

    private fun decode(value: String): PendingTournamentScore? {
        val parts = value.split('|')
        if (parts.size != 3) return null
        return runCatching {
            PendingTournamentScore(
                board = TournamentBoard.valueOf(parts[0]),
                period = parts[1],
                points = parts[2].toInt(),
            )
        }.getOrNull()
    }

    private companion object {
        val PENDING = stringPreferencesKey("tournament_pending_scores")
    }
}
