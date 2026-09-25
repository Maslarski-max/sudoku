package com.maslarski.sudoku.domain.engine

import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Hint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringRulesTest {

    private val engine = GameEngine()
    private val hints = HintEngine()

    private fun newGame(size: GridSize = GridSize.NINE, difficulty: Difficulty = Difficulty.EASY): GameState =
        GameState.fromPuzzle(SudokuGenerator().generate(size, difficulty, seed = 7L), nowEpochMillis = 0L)

    private fun firstEmpty(s: GameState) = s.cells.indexOfFirst { it.isEmpty }

    @Test
    fun `base points per grid size`() {
        assertEquals(10, ScoringRules.basePoints(GridSize.NINE))
        assertEquals(15, ScoringRules.basePoints(GridSize.TWELVE))
        assertEquals(20, ScoringRules.basePoints(GridSize.FIFTEEN))
        assertEquals(25, ScoringRules.basePoints(GridSize.EIGHTEEN))
    }

    @Test
    fun `correct entry earns base points and a mistake costs the penalty`() {
        val s0 = newGame()
        assertEquals(0, ScoringRules.points(s0))

        val i = firstEmpty(s0)
        val s1 = engine.enterValue(s0, i, s0.puzzle.solution[i]).state
        assertEquals(10, ScoringRules.points(s1))

        val j = firstEmpty(s1)
        val wrong = (1..9).first { it != s1.puzzle.solution[j] }
        val s2 = engine.enterValue(s1, j, wrong).state
        assertEquals(10 - ScoringRules.MISTAKE_PENALTY, ScoringRules.breakdown(s2).run { basePoints - mistakePenalty })
        assertEquals(0, ScoringRules.points(s2)) // floored at zero
    }

    @Test
    fun `erasing and re-entering does not farm points`() {
        val s0 = newGame()
        val i = firstEmpty(s0)
        var s = engine.enterValue(s0, i, s0.puzzle.solution[i]).state
        repeat(3) {
            s = engine.erase(s, i)
            s = engine.enterValue(s, i, s.puzzle.solution[i]).state
        }
        assertEquals(10, ScoringRules.points(s))
    }

    @Test
    fun `hint-filled cells earn nothing`() {
        val s0 = newGame()
        val s1 = engine.applyHint(s0, hints.nextHint(s0))
        assertEquals(0, ScoringRules.points(s1))
    }

    @Test
    fun `difficulty multiplier and time bonus apply on completion`() {
        for (difficulty in Difficulty.entries) {
            var s = newGame(GridSize.NINE, difficulty)
            val empties = s.puzzle.givens.count { it == 0 }
            while (!s.isComplete) {
                val i = firstEmpty(s)
                s = engine.enterValue(s, i, s.puzzle.solution[i]).state
            }
            // Instant completion: full time bonus (= base × empties) on top of base points.
            val fast = ScoringRules.breakdown(s.copy(elapsedMillis = 0L))
            assertEquals(empties * 10, fast.basePoints)
            assertEquals(empties * 10, fast.timeBonus)
            assertEquals(ScoringRules.multiplier(difficulty), fast.multiplier, 0.0)
            assertEquals(Math.round(empties * 20 * ScoringRules.multiplier(difficulty)).toInt(), fast.total)

            // Over the target time: no bonus.
            val slow = ScoringRules.breakdown(s.copy(elapsedMillis = ScoringRules.targetTimeMillis(s) + 1))
            assertEquals(0, slow.timeBonus)
            assertTrue(slow.total < fast.total)
        }
        assertEquals(1.0, ScoringRules.multiplier(Difficulty.EASY), 0.0)
        assertEquals(1.5, ScoringRules.multiplier(Difficulty.MEDIUM), 0.0)
        assertEquals(2.0, ScoringRules.multiplier(Difficulty.HARD), 0.0)
    }

    @Test
    fun `no bonus before completion`() {
        val s = newGame()
        assertEquals(0, ScoringRules.breakdown(s).timeBonus)
        assertTrue(hints.nextHint(s) !is Hint.None)
    }
}
