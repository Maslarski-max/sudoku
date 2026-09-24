package com.maslarski.sudoku.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedGameDao {
    @Query("SELECT * FROM saved_games ORDER BY lastPlayedEpochMillis DESC")
    fun observeAll(): Flow<List<SavedGameEntity>>

    @Query("SELECT * FROM saved_games WHERE gridSize = :gridSize")
    suspend fun get(gridSize: Int): SavedGameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavedGameEntity)

    @Query("DELETE FROM saved_games WHERE gridSize = :gridSize")
    suspend fun delete(gridSize: Int)
}

@Dao
interface LeaderboardDao {
    @Query("SELECT * FROM leaderboard_cache WHERE category = :category ORDER BY timeMillis ASC, moves ASC LIMIT :limit")
    fun observeTop(category: String, limit: Int): Flow<List<LeaderboardCacheEntity>>

    @Query("SELECT * FROM leaderboard_cache WHERE category = :category AND uid = :uid")
    suspend fun get(category: String, uid: String): LeaderboardCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<LeaderboardCacheEntity>)

    @Query("DELETE FROM leaderboard_cache WHERE category = :category")
    suspend fun clear(category: String)

    @Transaction
    suspend fun replace(category: String, entities: List<LeaderboardCacheEntity>) {
        clear(category)
        upsertAll(entities)
    }

    @Query("SELECT * FROM pending_scores")
    suspend fun pending(): List<PendingScoreEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPending(entity: PendingScoreEntity)

    @Query("DELETE FROM pending_scores WHERE category = :category")
    suspend fun deletePending(category: String)
}
