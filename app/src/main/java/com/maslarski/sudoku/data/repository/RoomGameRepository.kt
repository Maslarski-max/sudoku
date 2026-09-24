package com.maslarski.sudoku.data.repository

import com.maslarski.sudoku.data.local.GameStateSerializer
import com.maslarski.sudoku.data.local.SavedGameDao
import com.maslarski.sudoku.data.local.SavedGameEntity
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.repository.GameRepository
import com.maslarski.sudoku.domain.repository.SavedGameSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomGameRepository @Inject constructor(
    private val dao: SavedGameDao,
    private val serializer: GameStateSerializer,
) : GameRepository {

    override fun observeSavedGames(): Flow<List<SavedGameSummary>> = dao.observeAll().map { rows ->
        rows.map {
            SavedGameSummary(
                gridSize = GridSize.fromSize(it.gridSize),
                difficulty = Difficulty.valueOf(it.difficulty),
                elapsedMillis = it.elapsedMillis,
                filledFraction = it.filledFraction,
                lastPlayedEpochMillis = it.lastPlayedEpochMillis,
            )
        }
    }

    override suspend fun load(gridSize: GridSize): GameState? =
        dao.get(gridSize.size)?.let { runCatching { serializer.decode(it.stateJson) }.getOrNull() }

    override suspend fun save(state: GameState) {
        if (state.isComplete) {
            dao.delete(state.gridSize.size)
            return
        }
        val filled = state.cells.count { it.value != 0 }.toFloat() / state.cells.size
        dao.upsert(
            SavedGameEntity(
                gridSize = state.gridSize.size,
                difficulty = state.difficulty.name,
                elapsedMillis = state.elapsedMillis,
                filledFraction = filled,
                lastPlayedEpochMillis = state.lastPlayedEpochMillis,
                stateJson = serializer.encode(state),
            ),
        )
    }

    override suspend fun delete(gridSize: GridSize) = dao.delete(gridSize.size)
}
