package com.maslarski.sudoku.domain.engine

import com.maslarski.sudoku.domain.model.Cell
import com.maslarski.sudoku.domain.model.GameRules
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.Hint
import com.maslarski.sudoku.domain.model.Move

/**
 * Pure state transitions for a game. Every function returns a new [GameState]; nothing here touches
 * persistence, timers, or lives so it can be unit-tested exhaustively.
 */
class GameEngine {

    /**
     * Outcome of entering a value. [wasMistake] is true for any wrong entry (scoring penalty);
     * [lostLife] is true only on every [GameRules.MISTAKES_PER_LIFE]th mistake.
     */
    data class EntryResult(val state: GameState, val wasMistake: Boolean, val lostLife: Boolean = false)

    fun enterValue(state: GameState, index: Int, value: Int): EntryResult {
        if (!state.topology.active[index]) return EntryResult(state, wasMistake = false)
        val cell = state.cells[index]
        if (cell.isGiven || state.isComplete) return EntryResult(state, wasMistake = false)
        if (cell.value == value) return EntryResult(state, wasMistake = false)

        val correct = state.puzzle.solution[index] == value
        val streak = if (correct) state.mistakeStreak else state.mistakeStreak + 1
        val lostLife = !correct && streak >= GameRules.MISTAKES_PER_LIFE
        val newlyCountedCorrect = correct && index !in state.countedCells
        val comboStreak = when {
            !correct -> 0
            !newlyCountedCorrect -> state.comboStreak
            state.lastCorrectElapsedMillis != null &&
                state.elapsedMillis - state.lastCorrectElapsedMillis <= GameRules.COMBO_WINDOW_MILLIS ->
                state.comboStreak + 1
            else -> 1
        }
        val after = cell.copy(
            value = value,
            notes = 0,
            isError = !correct,
            combo = newlyCountedCorrect && comboStreak >= GameRules.COMBO_MIN_STREAK,
        )
        val next = applyMove(state, Move(index, cell, after, costLife = lostLife))
            .let {
                when {
                    !correct -> it.copy(
                        mistakes = it.mistakes + 1,
                        mistakeStreak = if (lostLife) 0 else streak,
                        comboStreak = 0,
                        lastCorrectElapsedMillis = null,
                    )
                    newlyCountedCorrect -> it.copy(
                        comboStreak = comboStreak,
                        lastCorrectElapsedMillis = state.elapsedMillis,
                        countedCells = state.countedCells + index,
                    )
                    else -> it
                }
            }
            .let { clearPeerNotes(it, index, value) }
        return EntryResult(finalizeIfSolved(next), wasMistake = !correct, lostLife = lostLife)
    }

    fun toggleNote(state: GameState, index: Int, value: Int): GameState {
        if (!state.topology.active[index]) return state
        val cell = state.cells[index]
        if (cell.isGiven || state.isComplete) return state
        val base = if (cell.value != 0) cell.copy(value = 0, isError = false, combo = false) else cell
        return applyMove(state, Move(index, cell, base.toggleNote(value)))
    }

    fun erase(state: GameState, index: Int): GameState {
        if (!state.topology.active[index]) return state
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
        if (!state.topology.active[hint.index]) return state
        val cell = state.cells[hint.index]
        val after = cell.copy(value = hint.value, notes = 0, isError = false, combo = false)
        val next = applyMove(state, Move(hint.index, cell, after))
            .let { clearPeerNotes(it, hint.index, hint.value) }
            .let {
                it.copy(
                    hintsUsed = it.hintsUsed + 1,
                    countedCells = it.countedCells + hint.index,
                )
            }
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
        for (peer in state.topology.peers(index)) {
            val c = cells[peer]
            if (c.value == 0 && c.notes and bit != 0) {
                cells[peer] = c.copy(notes = c.notes and bit.inv())
                changed = true
            }
        }
        return if (changed) state.copy(cells = cells) else state
    }

    private fun finalizeIfSolved(state: GameState): GameState {
        val solved = state.topology.activeIndices.all { state.cells[it].value == state.puzzle.solution[it] }
        return if (solved) state.copy(isComplete = true, redoStack = emptyList()) else state
    }
}
