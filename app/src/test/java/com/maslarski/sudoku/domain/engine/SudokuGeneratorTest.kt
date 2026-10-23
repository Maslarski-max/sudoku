package com.maslarski.sudoku.domain.engine

import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Topology
import com.maslarski.sudoku.domain.model.Variant
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

    @Test
    fun `variant solutions satisfy every unit and puzzles are unique`() {
        for (variant in Variant.entries) {
            for (difficulty in Difficulty.entries) {
                val started = System.nanoTime()
                val seed = 42L + variant.ordinal * 100L + difficulty.ordinal
                val puzzle = generator.generateVariant(variant, difficulty, seed = seed)
                val topology = puzzle.topology
                val masks = puzzle.outsideClues?.exclusionMasks(topology)
                val solver = UnitSolver(topology, masks)
                val elapsedMillis = (System.nanoTime() - started) / 1_000_000L
                println("VARIANT_GENERATION_MS ${variant.name} ${difficulty.name} $elapsedMillis givens=${puzzle.clueCount}")

                assertTrue("$variant solution is valid", solver.isSolved(puzzle.solution))
                assertEquals(
                    "$variant $difficulty puzzle should have one solution",
                    1,
                    solver.countSolutions(puzzle.givens, nodeBudget = Long.MAX_VALUE).count,
                )
                topology.units.forEach { unit ->
                    assertEquals((1..9).toSet(), unit.map { puzzle.solution[it] }.toSet())
                }
                assertEquals(
                    when (variant) {
                        Variant.SAMURAI -> 369
                        Variant.FLOWER -> 189
                        Variant.OUTSIDE -> 81
                    },
                    topology.active.count { it },
                )
                assertTrue(topology.activeIndices.all { index ->
                    puzzle.givens[index] == 0 || puzzle.givens[index] == puzzle.solution[index]
                })
                if (variant == Variant.OUTSIDE) {
                    assertOutsideCluesSatisfied(puzzle.solution, puzzle.outsideClues!!)
                }
            }
        }
    }

    private fun assertOutsideCluesSatisfied(solution: IntArray, clues: com.maslarski.sudoku.domain.model.OutsideClues) {
        for (row in 0 until 9) {
            clues.rowsFromLeft.getOrNull(row).orEmpty().forEach { digit ->
                assertTrue((3 until 9).none { solution[row * 9 + it] == digit })
            }
            clues.rowsFromRight.getOrNull(row).orEmpty().forEach { digit ->
                assertTrue((3 until 9).none { solution[row * 9 + 8 - it] == digit })
            }
        }
        for (col in 0 until 9) {
            clues.columnsFromTop.getOrNull(col).orEmpty().forEach { digit ->
                assertTrue((3 until 9).none { solution[it * 9 + col] == digit })
            }
            clues.columnsFromBottom.getOrNull(col).orEmpty().forEach { digit ->
                assertTrue((3 until 9).none { solution[(8 - it) * 9 + col] == digit })
            }
        }
    }
}
