package com.maslarski.sudoku.domain.engine

import com.maslarski.sudoku.domain.model.Cell
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.Hint
import com.maslarski.sudoku.domain.model.Move

/**
 * Pure state transitions for a game. Every function returns a new [GameState]; nothing here touches
 * persistence, timers, or lives so it can be unit-tested exhaustively.
 */
class GameEngine {

    /** Outcome of entering a value: the new state and whether a life should be deducted. */
    data class EntryResult(val state: GameState, val wasMistake: Boolean)

    fun enterValue(state: GameState, index: Int, value: Int): EntryResult {
        val cell = state.cells[index]
        if (cell.isGiven || state.isComplete) return EntryResult(state, wasMistake = false)
        if (cell.value == value) return EntryResult(state, wasMistake = false)

        val correct = state.puzzle.solution[index] == value
        val after = cell.copy(value = value, notes = 0, isError = !correct)
        val next = applyMove(state, Move(index, cell, after, costLife = !correct))
            .let { if (!correct) it.copy(mistakes = it.mistakes + 1) else it }
            .let { clearPeerNotes(it, index, value) }
        return EntryResult(finalizeIfSolved(next), wasMistake = !correct)
    }

    fun toggleNote(state: GameState, index: Int, value: Int): GameState {
        val cell = state.cells[index]
        if (cell.isGiven || state.isComplete) return state
        val base = if (cell.value != 0) cell.copy(value = 0, isError = false) else cell
        return applyMove(state, Move(index, cell, base.toggleNote(value)))
    }

    fun erase(state: GameState, index: Int): GameState {
        val cell = state.cells[index]
        if (cell.isGiven || state.isComplete || (cell.value == 0 && cell.notes == 0)) return state
        return applyMove(state, Move(index, cell, Cell()))
    }

    fun undo(state: GameState): GameState {
        val move = state.undoStack.lastOrNull() ?: return state
        if (state.isComplete) return state
        return state.copy(
            cells = state.cells.toMutableList().also { it[move.index] = move.before },
            undoStack = state.undoStack.dropLast(1),
            redoStack = state.redoStack + move,
            moves = state.moves + 1,
        )
    }

    fun redo(state: GameState): GameState {
        val move = state.redoStack.lastOrNull() ?: return state
        if (state.isComplete) return state
        val next = state.copy(
            cells = state.cells.toMutableList().also { it[move.index] = move.after },
            redoStack = state.redoStack.dropLast(1),
            undoStack = state.undoStack + move,
            moves = state.moves + 1,
        )
        return finalizeIfSolved(next)
    }

    fun applyHint(state: GameState, hint: Hint): GameState {
        if (hint is Hint.None || state.isComplete) return state
        val cell = state.cells[hint.index]
        val after = cell.copy(value = hint.value, notes = 0, isError = false)
        val next = applyMove(state, Move(hint.index, cell, after))
            .let { clearPeerNotes(it, hint.index, hint.value) }
            .let { it.copy(hintsUsed = it.hintsUsed + 1) }
        return finalizeIfSolved(next)
    }

    fun tick(state: GameState, deltaMillis: Long, nowEpochMillis: Long): GameState =
        if (state.isComplete) state
        else state.copy(elapsedMillis = state.elapsedMillis + deltaMillis, lastPlayedEpochMillis = nowEpochMillis)

    private fun applyMove(state: GameState, move: Move): GameState = state.copy(
        cells = state.cells.toMutableList().also { it[move.index] = move.after },
        undoStack = state.undoStack + move,
        redoStack = emptyList(),
        moves = state.moves + 1,
    )

    /** Placing a value removes that pencil mark from every peer, as a player would by hand. */
    private fun clearPeerNotes(state: GameState, index: Int, value: Int): GameState {
        val bit = 1 shl (value - 1)
        val cells = state.cells.toMutableList()
        var changed = false
        for (peer in HintEngine.peersOf(index, state.gridSize)) {
            val c = cells[peer]
            if (c.value == 0 && c.notes and bit != 0) {
                cells[peer] = c.copy(notes = c.notes and bit.inv())
                changed = true
            }
        }
        return if (changed) state.copy(cells = cells) else state
    }

    private fun finalizeIfSolved(state: GameState): GameState {
        val solved = state.cells.indices.all { state.cells[it].value == state.puzzle.solution[it] }
        return if (solved) state.copy(isComplete = true, redoStack = emptyList()) else state
    }
}
