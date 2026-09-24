package com.maslarski.sudoku.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SavedGameEntity::class, LeaderboardCacheEntity::class, PendingScoreEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SudokuDatabase : RoomDatabase() {
    abstract fun savedGameDao(): SavedGameDao
    abstract fun leaderboardDao(): LeaderboardDao
}
