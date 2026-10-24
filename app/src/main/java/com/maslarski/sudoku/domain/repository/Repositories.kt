package com.maslarski.sudoku.domain.repository

import com.maslarski.sudoku.domain.model.BillingAvailability
import com.maslarski.sudoku.domain.model.BillingEvent
import com.maslarski.sudoku.domain.model.AppSettings
import com.maslarski.sudoku.domain.model.AppTheme
import com.maslarski.sudoku.domain.model.DailyChallenge
import com.maslarski.sudoku.domain.model.PendingTournamentScore
import com.maslarski.sudoku.domain.model.TournamentBoard
import com.maslarski.sudoku.domain.model.TournamentSnapshot
import com.maslarski.sudoku.domain.model.Trophy
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.LeaderboardCategory
import com.maslarski.sudoku.domain.model.LeaderboardEntry
import com.maslarski.sudoku.domain.model.Lives
import com.maslarski.sudoku.domain.model.ProductId
import com.maslarski.sudoku.domain.model.Score
import com.maslarski.sudoku.domain.model.StoreProduct
import com.maslarski.sudoku.domain.model.Variant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Persists one in-progress game per grid size. */
interface GameRepository {
    fun observeSavedGames(): Flow<List<SavedGameSummary>>
    suspend fun load(gridSize: GridSize): GameState?
    suspend fun loadSlot(slotId: Int): GameState? =
        GridSize.entries.firstOrNull { it.size == slotId }?.let { load(it) }
    suspend fun save(state: GameState)
    suspend fun delete(gridSize: GridSize)
    suspend fun deleteSlot(slotId: Int) {
        GridSize.entries.firstOrNull { it.size == slotId }?.let { delete(it) }
    }
}

interface DailyChallengeRepository {
    val stampedDates: Flow<Set<String>>
    suspend fun recordStamp(date: String)
}

interface TournamentPendingQueue {
    suspend fun entries(): List<PendingTournamentScore>
    suspend fun add(entry: PendingTournamentScore)
    suspend fun remove(entry: PendingTournamentScore)
}

interface TournamentRepository {
    suspend fun record(board: TournamentBoard, completedAtEpochMillis: Long, points: Int): Result<Unit>
    suspend fun flushPending()
    suspend fun load(
        board: TournamentBoard,
        period: String,
        limit: Int = 50,
        serverOnly: Boolean = false,
    ): Result<TournamentSnapshot>
}

interface TrophyRepository {
    val trophies: Flow<List<Trophy>>
    suspend fun recordCompletion(state: GameState)
    suspend fun refreshPeriodAwards()
}

data class SavedGameSummary(
    val gridSize: GridSize,
    val difficulty: Difficulty,
    val elapsedMillis: Long,
    val filledFraction: Float,
    val lastPlayedEpochMillis: Long,
    val variant: Variant? = null,
    val iconMode: Boolean = false,
    val dailyDate: String? = null,
) {
    val slotId: Int get() = if (dailyDate != null) DailyChallenge.SLOT_ID
    else variant?.slotId ?: gridSize.size
}

interface LivesRepository {
    val lives: Flow<Lives>
    suspend fun consumeLife(): Boolean
    suspend fun addLives(count: Int)
    suspend fun setUnlimited(unlimited: Boolean)
}

interface ReviewPromptRepository {
    suspend fun onPuzzleCompleted(): Boolean
    suspend fun rateNow()
    suspend fun later()
    suspend fun never()
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setVibrationEnabled(enabled: Boolean)
    suspend fun setShowCandidates(enabled: Boolean)
    suspend fun setTheme(theme: AppTheme)
    suspend fun setDarkMode(darkMode: Boolean)
    suspend fun setIconMode(iconMode: Boolean)
}

interface GameFeedback {
    fun cellSelected()
    fun mistake()
    fun puzzleCompleted()
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
