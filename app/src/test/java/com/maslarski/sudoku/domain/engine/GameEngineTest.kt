package com.maslarski.sudoku.domain.engine

import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameRules
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Hint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GameEngineTest {

    private val engine = GameEngine()
    private val hints = HintEngine()
    private lateinit var state: GameState

    @Before
    fun setUp() {
        val puzzle = SudokuGenerator().generate(GridSize.NINE, Difficulty.EASY, seed = 1L)
        state = GameState.fromPuzzle(puzzle, nowEpochMillis = 0L)
    }

    private fun firstEmpty(): Int = state.cells.indexOfFirst { it.isEmpty }

    @Test
    fun `correct entry does not cost a life, wrong entry does`() {
        val index = firstEmpty()
        val correct = state.puzzle.solution[index]
        val wrong = (1..9).first { it != correct }

        val bad = engine.enterValue(state, index, wrong)
        assertTrue(bad.wasMistake)
        assertTrue(bad.state.cells[index].isError)
        assertEquals(1, bad.state.mistakes)

        val good = engine.enterValue(bad.state, index, correct)
        assertFalse(good.wasMistake)
        assertFalse(good.state.cells[index].isError)
        assertEquals(2, good.state.moves)
    }

    @Test
    fun `givens are immutable`() {
        val given = state.cells.indexOfFirst { it.isGiven }
        val result = engine.enterValue(state, given, 1)
        assertEquals(state, result.state)
        assertEquals(state, engine.erase(state, given))
    }

    @Test
    fun `undo and redo round trip`() {
        val index = firstEmpty()
        val value = state.puzzle.solution[index]
        val entered = engine.enterValue(state, index, value).state
        val undone = engine.undo(entered)
        assertTrue(undone.cells[index].isEmpty)
        assertEquals(1, undone.redoStack.size)
        val redone = engine.redo(undone)
        assertEquals(value, redone.cells[index].value)
        assertTrue(redone.redoStack.isEmpty())
    }

    @Test
    fun `notes toggle and are cleared by peer placement`() {
        val index = firstEmpty()
        val peer = HintEngine.peersOf(index, GridSize.NINE).first { state.cells[it].isEmpty }
        val value = state.puzzle.solution[peer]

        val noted = engine.toggleNote(state, index, value)
        assertTrue(noted.cells[index].hasNote(value))
        assertFalse(engine.toggleNote(noted, index, value).cells[index].hasNote(value))

        val placed = engine.enterValue(noted, peer, value).state
        assertFalse(placed.cells[index].hasNote(value))
    }

    @Test
    fun `applying hints eventually completes the puzzle`() {
        var s = state
        var guard = 0
        while (!s.isComplete && guard++ < 200) {
            val hint = hints.nextHint(s)
            assertTrue(hint !is Hint.None)
            s = engine.applyHint(s, hint)
        }
        assertTrue(s.isComplete)
        assertEquals(0, s.mistakes)
    }

    @Test
    fun `hint flags a wrong entry first`() {
        val index = firstEmpty()
        val wrong = (1..9).first { it != state.puzzle.solution[index] }
        val s = engine.enterValue(state, index, wrong).state
        val hint = hints.nextHint(s)
        assertTrue(hint is Hint.FixMistake)
        assertEquals(index, hint.index)
    }

    @Test
    fun `third correct entry within combo window is counted as combo`() {
        val indices = state.cells.indices.filter { state.cells[it].isEmpty }.take(3)
        var current = state
        indices.forEachIndexed { position, index ->
            current = current.copy(elapsedMillis = position * 5_000L)
            current = engine.enterValue(current, index, current.puzzle.solution[index]).state
        }

        assertEquals(3, current.comboStreak)
        assertEquals(10_000L, current.lastCorrectElapsedMillis)
        assertTrue(indices[2] in current.countedCells)
        assertTrue(indices[2] in current.comboCells)
        assertFalse(indices[0] in current.comboCells)
    }

    @Test
    fun `correct entry after combo window starts a new streak`() {
        val indices = state.cells.indices.filter { state.cells[it].isEmpty }.take(2)
        val first = engine.enterValue(state, indices[0], state.puzzle.solution[indices[0]]).state
        val second = engine.enterValue(
            first.copy(elapsedMillis = GameRules.COMBO_WINDOW_MILLIS + 1L),
            indices[1],
            state.puzzle.solution[indices[1]],
        ).state

        assertEquals(1, second.comboStreak)
        assertEquals(GameRules.COMBO_WINDOW_MILLIS + 1L, second.lastCorrectElapsedMillis)
        assertFalse(indices[1] in second.comboCells)
    }

    @Test
    fun `mistake resets combo streak and last correct time`() {
        val indices = state.cells.indices.filter { state.cells[it].isEmpty }.take(2)
        val first = engine.enterValue(state, indices[0], state.puzzle.solution[indices[0]]).state
        val wrong = (1..9).first { it != state.puzzle.solution[indices[1]] }
        val mistaken = engine.enterValue(
            first.copy(elapsedMillis = 1_000L),
            indices[1],
            wrong,
        ).state

        assertEquals(0, mistaken.comboStreak)
        assertNull(mistaken.lastCorrectElapsedMillis)
    }

    @Test
    fun `erasing and reentering counted cell does not extend combo streak`() {
        val indices = state.cells.indices.filter { state.cells[it].isEmpty }.take(2)
        val first = engine.enterValue(state, indices[0], state.puzzle.solution[indices[0]]).state
        val second = engine.enterValue(
            first.copy(elapsedMillis = 1_000L),
            indices[1],
            state.puzzle.solution[indices[1]],
        ).state
        val erased = engine.erase(second, indices[1])
        val reentered = engine.enterValue(
            erased.copy(elapsedMillis = 5_000L),
            indices[1],
            state.puzzle.solution[indices[1]],
        ).state

        assertEquals(2, reentered.comboStreak)
        assertEquals(1_000L, reentered.lastCorrectElapsedMillis)
        assertTrue(indices[1] in reentered.countedCells)
    }

    @Test
    fun `hinted cell is counted but never added to combo cells`() {
        val indices = state.cells.indices.filter { state.cells[it].isEmpty }.take(4)
        val hintIndex = indices[0]
        var current = engine.applyHint(
            state,
            Hint.Reveal(hintIndex, state.puzzle.solution[hintIndex]),
        )
        indices.drop(1).forEachIndexed { position, index ->
            current = current.copy(elapsedMillis = position * 1_000L)
            current = engine.enterValue(current, index, current.puzzle.solution[index]).state
        }

        assertTrue(hintIndex in current.countedCells)
        assertFalse(hintIndex in current.comboCells)
        assertEquals(3, current.comboStreak)
    }
}
