package com.maslarski.sudoku.domain.engine

import com.maslarski.sudoku.domain.model.Cell
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Hint
import com.maslarski.sudoku.domain.model.Puzzle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HintEngineTest {
    private val engine = HintEngine()
    private val base = GameState.fromPuzzle(
        SudokuGenerator().generate(GridSize.NINE, Difficulty.EASY, seed = 7L),
        nowEpochMillis = 0L,
    )

    @Test
    fun `hint for selected empty cell targets it instead of an earlier naked single`() {
        val earlierHint = engine.nextHint(base)
        assertTrue(earlierHint is Hint.NakedSingle)
        val index = base.cells.indices.first { base.cells[it].isEmpty && it != earlierHint.index }

        val hint = engine.hintFor(base, index)

        assertEquals(index, hint?.index)
        assertEquals(base.puzzle.solution[index], hint?.value)
    }

    @Test
    fun `wrong value produces fix mistake at selected cell`() {
        val index = base.cells.indexOfFirst { it.isEmpty }
        val wrongValue = (1..9).first { it != base.puzzle.solution[index] }
        val state = base.copy(cells = base.cells.toMutableList().also { it[index] = Cell(value = wrongValue) })

        assertEquals(Hint.FixMistake(index, base.puzzle.solution[index], wrongValue), engine.hintFor(state, index))
    }

    @Test
    fun `given cell has no targeted hint`() {
        val index = base.cells.indexOfFirst { it.isGiven }

        assertNull(engine.hintFor(base, index))
    }

    @Test
    fun `correct user entered cell has no targeted hint`() {
        val index = base.cells.indexOfFirst { it.isEmpty }
        val state = base.copy(cells = base.cells.toMutableList().also {
            it[index] = Cell(value = base.puzzle.solution[index])
        })

        assertNull(engine.hintFor(state, index))
    }

    @Test
    fun `wrong peer making one incorrect candidate still reveals selected solution`() {
        val solution = IntArray(GridSize.NINE.cellCount) { index ->
            ((index / 9 * 3 + index / 9 / 3 + index % 9) % 9) + 1
        }
        val puzzle = Puzzle(
            gridSize = GridSize.NINE,
            difficulty = Difficulty.EASY,
            givens = IntArray(GridSize.NINE.cellCount),
            solution = solution,
            seed = 1L,
        )
        val state = GameState.fromPuzzle(puzzle, nowEpochMillis = 0L).copy(
            cells = GameState.fromPuzzle(puzzle, nowEpochMillis = 0L).cells.toMutableList().also { cells ->
                (1..8).forEach { col -> cells[col] = Cell(value = col) }
            },
        )

        assertEquals(Hint.Reveal(0, solution[0]), engine.hintFor(state, 0))
    }
}
