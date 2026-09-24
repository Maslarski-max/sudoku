package com.maslarski.sudoku.domain.engine

import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GridSize
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SudokuGeneratorTest {

    private val generator = SudokuGenerator()

    @Test
    fun `filled grids are valid for every size`() {
        for (size in GridSize.entries) {
            repeat(5) { i ->
                val grid = generator.fillGrid(size, Random(i.toLong()))
                assertTrue("size ${size.size} seed $i", SudokuSolver(size).isSolved(grid))
            }
        }
    }

    @Test
    fun `generated puzzles have a unique solution matching the stored solution`() {
        for (size in GridSize.entries) {
            for (difficulty in Difficulty.entries) {
                val puzzle = generator.generate(size, difficulty, seed = 42L)
                val solver = SudokuSolver(size)
                val count = solver.countSolutions(puzzle.givens, limit = 2, nodeBudget = Long.MAX_VALUE)
                assertEquals("size ${size.size} $difficulty should be unique", 1, count.count)
                assertTrue(count.exhausted)
                assertArrayEquals(puzzle.solution, solver.solve(puzzle.givens))
                assertTrue(puzzle.clueCount < size.cellCount)
                for (i in puzzle.givens.indices) {
                    if (puzzle.givens[i] != 0) assertEquals(puzzle.solution[i], puzzle.givens[i])
                }
            }
        }
    }

    @Test
    fun `harder difficulties remove at least as many cells on 9x9`() {
        val easy = generator.generate(GridSize.NINE, Difficulty.EASY, seed = 7L)
        val hard = generator.generate(GridSize.NINE, Difficulty.HARD, seed = 7L)
        assertTrue(hard.clueCount <= easy.clueCount)
    }

    @Test
    fun `generation is deterministic for a seed`() {
        val a = generator.generate(GridSize.TWELVE, Difficulty.MEDIUM, seed = 99L)
        val b = generator.generate(GridSize.TWELVE, Difficulty.MEDIUM, seed = 99L)
        assertArrayEquals(a.givens, b.givens)
        assertArrayEquals(a.solution, b.solution)
    }
}
