package com.maslarski.sudoku.data.repository

import com.maslarski.sudoku.data.local.GameStateSerializer
import com.maslarski.sudoku.data.local.SavedGameDao
import com.maslarski.sudoku.data.local.SavedGameEntity
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Variant
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
            val state = runCatching { serializer.decode(it.stateJson) }.getOrNull()
            val variant = state?.variant ?: Variant.fromSlotId(it.gridSize)
            SavedGameSummary(
                gridSize = state?.gridSize ?: GridSize.entries.firstOrNull { size -> size.size == it.gridSize } ?: GridSize.NINE,
                difficulty = state?.difficulty ?: Difficulty.valueOf(it.difficulty),
                elapsedMillis = it.elapsedMillis,
                filledFraction = it.filledFraction,
                lastPlayedEpochMillis = it.lastPlayedEpochMillis,
                variant = variant,
            )
        }
    }

    override suspend fun load(gridSize: GridSize): GameState? = loadSlot(gridSize.size)

    override suspend fun loadSlot(slotId: Int): GameState? =
        dao.get(slotId)?.let { runCatching { serializer.decode(it.stateJson) }.getOrNull() }

    override suspend fun save(state: GameState) {
        if (state.isComplete) {
            dao.delete(state.slotId)
            return
        }
        val active = state.topology.activeIndices
        val filled = active.count { state.cells[it].value != 0 }.toFloat() / active.size
        dao.upsert(
            SavedGameEntity(
                gridSize = state.slotId,
                difficulty = state.difficulty.name,
                elapsedMillis = state.elapsedMillis,
                filledFraction = filled,
                lastPlayedEpochMillis = state.lastPlayedEpochMillis,
                stateJson = serializer.encode(state),
            ),
        )
    }

    override suspend fun delete(gridSize: GridSize) = deleteSlot(gridSize.size)

    override suspend fun deleteSlot(slotId: Int) = dao.delete(slotId)
}
