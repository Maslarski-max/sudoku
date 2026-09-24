package com.maslarski.sudoku.domain.usecase

import com.maslarski.sudoku.domain.engine.SudokuGenerator
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.LeaderboardCategory
import com.maslarski.sudoku.domain.model.Score
import com.maslarski.sudoku.domain.repository.AuthRepository
import com.maslarski.sudoku.domain.repository.GameRepository
import com.maslarski.sudoku.domain.repository.LeaderboardRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class StartNewGameUseCase @Inject constructor(
    private val generator: SudokuGenerator,
    private val gameRepository: GameRepository,
    private val defaultDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(gridSize: GridSize, difficulty: Difficulty): GameState {
        val puzzle = withContext(defaultDispatcher) { generator.generate(gridSize, difficulty) }
        val state = GameState.fromPuzzle(puzzle, nowEpochMillis = System.currentTimeMillis())
        gameRepository.save(state)
        return state
    }
}

class SubmitScoreUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val leaderboardRepository: LeaderboardRepository,
) {
    /** Returns true when the score reached the remote leaderboard. */
    suspend operator fun invoke(state: GameState): Result<Boolean> = runCatching {
        val uid = authRepository.ensureSignedIn()
        val score = Score(
            uid = uid,
            displayName = "Player ${uid.takeLast(4).uppercase()}",
            gridSize = state.gridSize,
            difficulty = state.difficulty,
            timeMillis = state.elapsedMillis.coerceAtLeast(1L),
            moves = state.moves,
            mistakes = state.mistakes,
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
