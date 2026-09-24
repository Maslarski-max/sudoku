package com.maslarski.sudoku.domain.repository

import com.maslarski.sudoku.domain.model.BillingAvailability
import com.maslarski.sudoku.domain.model.BillingEvent
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.LeaderboardCategory
import com.maslarski.sudoku.domain.model.LeaderboardEntry
import com.maslarski.sudoku.domain.model.Lives
import com.maslarski.sudoku.domain.model.ProductId
import com.maslarski.sudoku.domain.model.Score
import com.maslarski.sudoku.domain.model.StoreProduct
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Persists one in-progress game per grid size. */
interface GameRepository {
    fun observeSavedGames(): Flow<List<SavedGameSummary>>
    suspend fun load(gridSize: GridSize): GameState?
    suspend fun save(state: GameState)
    suspend fun delete(gridSize: GridSize)
}

data class SavedGameSummary(
    val gridSize: GridSize,
    val difficulty: Difficulty,
    val elapsedMillis: Long,
    val filledFraction: Float,
    val lastPlayedEpochMillis: Long,
)

interface LivesRepository {
    val lives: Flow<Lives>
    suspend fun consumeLife(): Boolean
    suspend fun addLives(count: Int)
    suspend fun setUnlimited(unlimited: Boolean)
}

interface AuthRepository {
    /** Signs in anonymously if needed and returns the stable user id. */
    suspend fun ensureSignedIn(): String
    val currentUserId: String?
}

interface LeaderboardRepository {
    /** Cached-then-network stream of the top entries for a category. */
    fun observeTop(category: LeaderboardCategory, limit: Int = 50): Flow<List<LeaderboardEntry>>
    suspend fun refresh(category: LeaderboardCategory, limit: Int = 50): Result<Unit>
    suspend fun personalBest(category: LeaderboardCategory): Score?
    /** Writes the score if it beats the player's stored best; returns true when it was persisted remotely. */
    suspend fun submit(score: Score): Result<Boolean>
}

interface BillingRepository {
    val availability: StateFlow<BillingAvailability>
    val products: StateFlow<List<StoreProduct>>
    val events: Flow<BillingEvent>
    fun launchPurchase(productId: ProductId)
    suspend fun restorePurchases()
}
