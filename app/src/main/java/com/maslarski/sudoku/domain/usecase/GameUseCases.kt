package com.maslarski.sudoku.domain.usecase

import com.maslarski.sudoku.domain.engine.ScoringRules
import com.maslarski.sudoku.domain.engine.SudokuGenerator
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.DailyChallenge
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.LeaderboardCategory
import com.maslarski.sudoku.domain.model.Score
import com.maslarski.sudoku.domain.model.Variant
import com.maslarski.sudoku.domain.repository.AuthRepository
import com.maslarski.sudoku.domain.repository.GameRepository
import com.maslarski.sudoku.domain.repository.LeaderboardRepository
import com.maslarski.sudoku.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

class StartNewGameUseCase @Inject constructor(
    private val generator: SudokuGenerator,
    private val gameRepository: GameRepository,
    private val defaultDispatcher: CoroutineDispatcher,
) {
    /**
     * Generates a puzzle, then runs [beforeSave] and persists the new game only if it returns true.
     * [beforeSave] and the save run together non-cancellably, so a charge taken in [beforeSave] can
     * never be stranded without its game (and a cancelled generation costs nothing). If the save itself
     * throws, [onSaveFailed] runs (e.g. to refund the charge) before the exception propagates.
     */
    suspend operator fun invoke(
        gridSize: GridSize,
        difficulty: Difficulty,
        beforeSave: suspend () -> Boolean = { true },
        onSaveFailed: suspend () -> Unit = {},
        variant: Variant? = null,
        iconMode: Boolean = false,
        dailyDate: String? = null,
    ): GameState? {
        val puzzle = withContext(defaultDispatcher) {
            if (dailyDate != null) {
                generator.generate(
                    GridSize.NINE,
                    Difficulty.MEDIUM,
                    seed = DailyChallenge.seed(dailyDate).toLong(),
                    deterministic = true,
                )
            } else if (variant == null) generator.generate(gridSize, difficulty)
            else generator.generateVariant(variant, difficulty)
        }
        val state = GameState.fromPuzzle(
            puzzle,
            nowEpochMillis = System.currentTimeMillis(),
            iconMode = dailyDate == null && iconMode && variant == null &&
                (gridSize == GridSize.NINE || gridSize == GridSize.TWELVE),
            dailyDate = dailyDate,
        )
        return withContext(NonCancellable) {
            if (!beforeSave()) return@withContext null
            try {
                gameRepository.save(state)
            } catch (e: Exception) {
                onSaveFailed()
                throw e
            }
            state
        }
    }
}

class SubmitScoreUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val leaderboardRepository: LeaderboardRepository,
    private val profileRepository: ProfileRepository,
) {
    /** Returns true when the score reached the remote leaderboard. */
    suspend operator fun invoke(state: GameState): Result<Boolean> = runCatching {
        // Sign-in may be unreachable right now; the score is still queued (uid-less) and uploaded on the next refresh.
        val uid = runCatching { authRepository.ensureSignedIn() }.getOrDefault("")
        val score = Score(
            uid = uid,
            displayName = if (uid.isEmpty()) Score.displayNameFor(uid) else profileRepository.displayNameFor(uid),
            gridSize = state.gridSize,
            difficulty = state.difficulty,
            timeMillis = state.elapsedMillis.coerceAtLeast(1L),
            moves = state.moves,
            mistakes = state.mistakes,
            points = ScoringRules.points(state),
            completedAtEpochMillis = System.currentTimeMillis(),
        )
        leaderboardRepository.submit(score).getOrThrow()
    }
}

class PersonalBestUseCase @Inject constructor(
    private val leaderboardRepository: LeaderboardRepository,
) {
    suspend operator fun invoke(category: LeaderboardCategory): Score? = leaderboardRepository.personalBest(category)
}
