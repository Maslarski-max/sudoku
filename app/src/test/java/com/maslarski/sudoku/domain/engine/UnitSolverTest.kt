package com.maslarski.sudoku.domain.engine

import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Topology
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UnitSolverTest {
    private val solution = intArrayOf(
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
    fun `classic topology solves and counts a unique puzzle`() {
        val puzzle = solution.copyOf().also { it[0] = 0 }
        val solver = UnitSolver(Topology.classic(GridSize.NINE))

        assertArrayEquals(solution, solver.solve(puzzle))
        assertTrue(solver.countSolutions(puzzle).isUnique)
    }

    @Test
    fun `randomized solver fills a valid grid`() {
        val topology = Topology.forVariant(com.maslarski.sudoku.domain.model.Variant.SAMURAI, GridSize.NINE)
        val solver = UnitSolver(topology)
        val filled = solver.solve(IntArray(topology.cellCount), Random(42L))

        assertNotNull(filled)
        assertTrue(solver.isSolved(filled!!))
        topology.units.forEach { unit ->
            assertEquals((1..9).toSet(), unit.map { filled[it] }.toSet())
        }
        assertTrue(topology.activeIndices.all { filled[it] in 1..9 })
        assertTrue(filled.indices.filterNot { topology.active[it] }.all { filled[it] == 0 })
    }

    @Test
    fun `extra masks exclude candidates and reject conflicting givens`() {
        val topology = Topology.classic(GridSize.NINE)
        val masks = IntArray(topology.cellCount).also { it[3] = 1 shl (5 - 1) }
        val solver = UnitSolver(topology, masks)

        assertEquals(0, solver.candidates(IntArray(81), 3) and (1 shl 4))
        val invalid = IntArray(81).also { it[3] = 5 }
        assertEquals(0, solver.countSolutions(invalid).count)
    }
}
