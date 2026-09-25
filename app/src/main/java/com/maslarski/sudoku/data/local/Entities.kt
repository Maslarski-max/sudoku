package com.maslarski.sudoku.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One auto-saved game per grid size; the full [GameState] is serialised as JSON in [stateJson]. */
@Entity(tableName = "saved_games")
data class SavedGameEntity(
    @PrimaryKey val gridSize: Int,
    val difficulty: String,
    val elapsedMillis: Long,
    val filledFraction: Float,
    val lastPlayedEpochMillis: Long,
    val stateJson: String,
)

/** Local cache of leaderboard rows so the screen renders instantly and offline. */
@Entity(tableName = "leaderboard_cache", primaryKeys = ["category", "uid"])
data class LeaderboardCacheEntity(
    val category: String,
    val uid: String,
    val displayName: String,
    val gridSize: Int,
    val difficulty: String,
    val timeMillis: Long,
    val moves: Int,
    val mistakes: Int,
    val points: Int,
    val completedAtEpochMillis: Long,
)

/** Scores awaiting upload (offline at completion time). */
@Entity(tableName = "pending_scores")
data class PendingScoreEntity(
    @PrimaryKey val category: String,
    val uid: String,
    val displayName: String,
    val gridSize: Int,
    val difficulty: String,
    val timeMillis: Long,
    val moves: Int,
    val mistakes: Int,
    val points: Int,
    val completedAtEpochMillis: Long,
)
