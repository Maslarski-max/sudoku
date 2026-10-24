package com.maslarski.sudoku.domain.engine

import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Hint
import com.maslarski.sudoku.domain.model.Puzzle
import com.maslarski.sudoku.domain.model.Topology
import com.maslarski.sudoku.domain.model.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VariantRulesTest {
    @Test
    fun `engine hints and score rules use the overlapping Samurai topology`() {
        val topology = Topology.forVariant(Variant.SAMURAI, GridSize.NINE)
        val solution = checkNotNull(UnitSolver(topology).solve(IntArray(topology.cellCount), Random(71L)))
        val target = 6 * topology.width + 6
        val peer = 6 * topology.width + 12
        assertTrue(peer in topology.peers(target))

        val puzzle = Puzzle(
            gridSize = GridSize.NINE,
            difficulty = Difficulty.EASY,
            givens = solution.copyOf(),
            solution = solution,
            seed = 71L,
            variant = Variant.SAMURAI,
        )
        val initial = GameState.fromPuzzle(puzzle, nowEpochMillis = 0L)
        val cells = initial.cells.toMutableList()
        cells[target] = cells[target].copy(value = 0, isGiven = false)
        cells[peer] = cells[peer].copy(
            value = 0,
            isGiven = false,
            notes = 1 shl (solution[target] - 1),
        )
        val editable = initial.copy(cells = cells)

        val entered = GameEngine().enterValue(editable, target, solution[target]).state
        assertEquals(0, entered.cells[peer].notes)

        val oneEmptyGivens = solution.copyOf().also { it[target] = 0 }
        val oneEmpty = GameState.fromPuzzle(puzzle.copy(givens = oneEmptyGivens), nowEpochMillis = 0L)
        val hint = HintEngine().hintFor(oneEmpty, target)
        assertEquals(target, hint?.index)
        assertEquals(solution[target], hint?.value)
        val inactive = 10
        assertTrue(!topology.active[inactive])
        assertNull(HintEngine().hintFor(oneEmpty, inactive))

        val second = 6 * topology.width + 7
        val scoreGivens = solution.copyOf().also {
            it[target] = 0
            it[second] = 0
        }
        val scoreGame = GameState.fromPuzzle(puzzle.copy(givens = scoreGivens), nowEpochMillis = 0L)
        val scoreCells = scoreGame.cells.toMutableList()
        listOf(target, second).forEach { index ->
            scoreCells[index] = scoreCells[index].copy(value = solution[index], combo = true)
        }
        val score = ScoringRules.breakdown(scoreGame.copy(cells = scoreCells))
        assertEquals(30, ScoringRules.basePoints(scoreGame))
        assertEquals(60, score.basePoints)
        assertEquals(60, score.comboBonus)
        assertEquals(120, score.total)
        assertTrue(hint is Hint.NakedSingle || hint is Hint.HiddenSingle)

        val expectedBases = mapOf(
            Variant.SAMURAI to 30,
            Variant.FLOWER to 25,
            Variant.OUTSIDE to 15,
        )
        expectedBases.forEach { (variant, expected) ->
            val variantTopology = Topology.forVariant(variant, GridSize.NINE)
            val emptyPuzzle = Puzzle(
                gridSize = GridSize.NINE,
                difficulty = Difficulty.EASY,
                givens = IntArray(variantTopology.cellCount),
                solution = IntArray(variantTopology.cellCount),
                seed = 1L,
                variant = variant,
            )
            assertEquals(expected, ScoringRules.basePoints(GameState.fromPuzzle(emptyPuzzle, 0L)))
        }
    }
}
