package com.maslarski.sudoku.domain.model

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

enum class TournamentBoard(val collection: String) {
    ARENA("arena"),
    CHAMPIONSHIP("championship");

    fun periodAt(epochMillis: Long): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC)
        return when (this) {
            ARENA -> YearMonth.from(date).toString()
            CHAMPIONSHIP -> date.year.toString()
        }
    }

    fun currentPeriod(epochMillis: Long): String = periodAt(epochMillis)
}

data class PendingTournamentScore(
    val board: TournamentBoard,
    val period: String,
    val points: Int,
)

data class TournamentEntry(
    val rank: Int?,
    val uid: String,
    val displayName: String,
    val points: Int,
    val games: Int,
    val isCurrentUser: Boolean = false,
)

data class TournamentSnapshot(
    val board: TournamentBoard,
    val period: String,
    val entries: List<TournamentEntry>,
    val playerEntry: TournamentEntry?,
)

enum class TrophyCategory {
    DAILY,
    TOURNAMENT,
    MILESTONE,
    PREMIUM,
}

enum class TrophyTier {
    GOLD,
    SILVER,
    BRONZE,
    ACCENT,
}

data class Trophy(
    val id: String,
    val title: String,
    val description: String,
    val category: TrophyCategory,
    val tier: TrophyTier,
    val unlocked: Boolean,
    val premiumOnly: Boolean = false,
)
