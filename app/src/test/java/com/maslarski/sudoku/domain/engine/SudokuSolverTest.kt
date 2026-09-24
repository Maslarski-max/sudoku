package com.maslarski.sudoku.domain.engine

import com.maslarski.sudoku.domain.model.GridSize
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuSolverTest {

    private val classic = intArrayOf(
        5, 3, 0, 0, 7, 0, 0, 0, 0,
        6, 0, 0, 1, 9, 5, 0, 0, 0,
        0, 9, 8, 0, 0, 0, 0, 6, 0,
        8, 0, 0, 0, 6, 0, 0, 0, 3,
        4, 0, 0, 8, 0, 3, 0, 0, 1,
        7, 0, 0, 0, 2, 0, 0, 0, 6,
        0, 6, 0, 0, 0, 0, 2, 8, 0,
        0, 0, 0, 4, 1, 9, 0, 0, 5,
        0, 0, 0, 0, 8, 0, 0, 7, 9,
    )

    private val classicSolution = intArrayOf(
        5, 3, 4, 6, 7, 8, 9, 1, 2,
        6, 7, 2, 1, 9, 5, 3, 4, 8,
        1, 9, 8, 3, 4, 2, 5, 6, 7,
        8, 5, 9, 7, 6, 1, 4, 2, 3,
        4, 2, 6, 8, 5, 3, 7, 9, 1,
        7, 1, 3, 9, 2, 4, 8, 5, 6,
        9, 6, 1, 5, 3, 7, 2, 8, 4,
        2, 8, 7, 4, 1, 9, 6, 3, 5,
        3, 4, 5, 2, 8, 6, 1, 7, 9,
    )

    @Test
    fun `solves the classic wikipedia puzzle`() {
        val solver = SudokuSolver(GridSize.NINE)
        assertArrayEquals(classicSolution, solver.solve(classic))
        assertTrue(solver.countSolutions(classic).isUnique)
    }

    @Test
    fun `empty grid has many solutions`() {
        val solver = SudokuSolver(GridSize.NINE)
        val result = solver.countSolutions(IntArray(81), limit = 2)
        assertEquals(2, result.count)
    }

    @Test
    fun `conflicting givens are unsolvable`() {
        val grid = IntArray(81).also { it[0] = 1; it[1] = 1 }
        val solver = SudokuSolver(GridSize.NINE)
        assertFalse(solver.isConsistent(grid))
        assertNull(solver.solve(grid))
    }

    @Test
    fun `candidates respect rectangular boxes`() {
        val size = GridSize.TWELVE
        val grid = IntArray(size.cellCount)
        grid[0] = 5 // row 0, col 0
        val solver = SudokuSolver(size)
        // Cell (2, 3) shares the 3x4 box with (0, 0) so 5 is excluded there...
        assertEquals(0, solver.candidates(grid, 2 * 12 + 3) and (1 shl 4))
        // ...but (3, 3) is in the next box and shares no row/column with (0, 0).
        assertTrue(solver.candidates(grid, 3 * 12 + 3) and (1 shl 4) != 0)
    }

    @Test
    fun `box index covers every box exactly once for all sizes`() {
        for (size in GridSize.entries) {
            val counts = IntArray(size.size)
            for (r in 0 until size.size) for (c in 0 until size.size) counts[size.boxIndex(r, c)]++
            assertTrue(counts.all { it == size.size })
        }
    }
}
