package com.maslarski.sudoku.data.local

import com.maslarski.sudoku.domain.model.Cell
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Move
import com.maslarski.sudoku.domain.model.Puzzle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** JSON snapshot format for auto-save. Kept separate from domain models so they can evolve independently. */
@Singleton
class GameStateSerializer @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(state: GameState): String = json.encodeToString(Snapshot.from(state))

    fun decode(raw: String): GameState = json.decodeFromString<Snapshot>(raw).toState()

    @Serializable
    private data class CellDto(val v: Int, val g: Boolean, val n: Int, val e: Boolean) {
        fun toCell() = Cell(value = v, isGiven = g, notes = n, isError = e)
        companion object {
            fun from(c: Cell) = CellDto(c.value, c.isGiven, c.notes, c.isError)
        }
    }

    @Serializable
    private data class MoveDto(val i: Int, val b: CellDto, val a: CellDto, val l: Boolean) {
        fun toMove() = Move(i, b.toCell(), a.toCell(), l)
        companion object {
            fun from(m: Move) = MoveDto(m.index, CellDto.from(m.before), CellDto.from(m.after), m.costLife)
        }
    }

    @Serializable
    private data class Snapshot(
        val gridSize: Int,
        val difficulty: String,
        val givens: List<Int>,
        val solution: List<Int>,
        val seed: Long,
        val cells: List<CellDto>,
        val undo: List<MoveDto>,
        val redo: List<MoveDto>,
        val elapsedMillis: Long,
        val moves: Int,
        val mistakes: Int,
        val hintsUsed: Int,
        val isComplete: Boolean,
        val startedAt: Long,
        val lastPlayedAt: Long,
    ) {
        fun toState(): GameState = GameState(
            puzzle = Puzzle(
                gridSize = GridSize.fromSize(gridSize),
                difficulty = Difficulty.valueOf(difficulty),
                givens = givens.toIntArray(),
                solution = solution.toIntArray(),
                seed = seed,
            ),
            cells = cells.map { it.toCell() },
            undoStack = undo.map { it.toMove() },
            redoStack = redo.map { it.toMove() },
            elapsedMillis = elapsedMillis,
            moves = moves,
            mistakes = mistakes,
            hintsUsed = hintsUsed,
            isComplete = isComplete,
            startedAtEpochMillis = startedAt,
            lastPlayedEpochMillis = lastPlayedAt,
        )

        companion object {
            fun from(s: GameState) = Snapshot(
                gridSize = s.gridSize.size,
                difficulty = s.difficulty.name,
                givens = s.puzzle.givens.toList(),
                solution = s.puzzle.solution.toList(),
                seed = s.puzzle.seed,
                cells = s.cells.map { CellDto.from(it) },
                undo = s.undoStack.map { MoveDto.from(it) },
                redo = s.redoStack.map { MoveDto.from(it) },
                elapsedMillis = s.elapsedMillis,
                moves = s.moves,
                mistakes = s.mistakes,
                hintsUsed = s.hintsUsed,
                isComplete = s.isComplete,
                startedAt = s.startedAtEpochMillis,
                lastPlayedAt = s.lastPlayedEpochMillis,
            )
        }
    }
}
