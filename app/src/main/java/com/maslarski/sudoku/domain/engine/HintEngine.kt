package com.maslarski.sudoku.domain.engine

import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Hint

/**
 * Produces an explained next step. Strategies are tried from most to least instructive:
 * 1. fix an incorrect entry, 2. naked single, 3. hidden single (row, column, box), 4. reveal.
 */
class HintEngine {

    fun nextHint(state: GameState): Hint {
        val gridSize = state.gridSize
        val grid = state.valuesArray()
        val solution = state.puzzle.solution

        val wrong = grid.indices.firstOrNull { grid[it] != 0 && grid[it] != solution[it] }
        if (wrong != null) return Hint.FixMistake(wrong, solution[wrong], grid[wrong])

        if (grid.none { it == 0 }) return Hint.None

        val solver = SudokuSolver(gridSize)
        val candidates = IntArray(grid.size) { solver.candidates(grid, it) }

        nakedSingle(grid, candidates, gridSize)?.let { return it }
        hiddenSingle(grid, candidates, gridSize)?.let { return it }

        // Fall back to the emptiest-constrained cell so the reveal is at least plausible to deduce.
        val target = grid.indices.filter { grid[it] == 0 }.minByOrNull { Integer.bitCount(candidates[it]) } ?: return Hint.None
        return Hint.Reveal(target, solution[target])
    }

    private fun nakedSingle(grid: IntArray, candidates: IntArray, gridSize: GridSize): Hint? {
        for (i in grid.indices) {
            if (grid[i] != 0) continue
            val mask = candidates[i]
            if (Integer.bitCount(mask) == 1) {
                val value = Integer.numberOfTrailingZeros(mask) + 1
                return Hint.NakedSingle(i, value, peersOf(i, gridSize).filter { grid[it] != 0 })
            }
        }
        return null
    }

    private fun hiddenSingle(grid: IntArray, candidates: IntArray, gridSize: GridSize): Hint? {
        val n = gridSize.size
        for (unit in 0 until n) {
            val row = (0 until n).map { unit * n + it }
            singleInUnit(row, grid, candidates, Hint.HiddenSingle.Unit.ROW)?.let { return it }
            val col = (0 until n).map { it * n + unit }
            singleInUnit(col, grid, candidates, Hint.HiddenSingle.Unit.COLUMN)?.let { return it }
            singleInUnit(boxCells(unit, gridSize), grid, candidates, Hint.HiddenSingle.Unit.BOX)?.let { return it }
        }
        return null
    }

    private fun singleInUnit(cells: List<Int>, grid: IntArray, candidates: IntArray, unit: Hint.HiddenSingle.Unit): Hint? {
        val n = cells.size
        for (value in 1..n) {
            val bit = 1 shl (value - 1)
            if (cells.any { grid[it] == value }) continue
            val spots = cells.filter { grid[it] == 0 && candidates[it] and bit != 0 }
            if (spots.size == 1) {
                return Hint.HiddenSingle(spots[0], value, unit, cells.filter { it != spots[0] })
            }
        }
        return null
    }

    private fun boxCells(box: Int, gridSize: GridSize): List<Int> {
        val n = gridSize.size
        val boxesPerRow = n / gridSize.boxCols
        val r0 = (box / boxesPerRow) * gridSize.boxRows
        val c0 = (box % boxesPerRow) * gridSize.boxCols
        return buildList {
            for (r in r0 until r0 + gridSize.boxRows) for (c in c0 until c0 + gridSize.boxCols) add(r * n + c)
        }
    }

    companion object {
        /** All cells sharing a row, column, or box with [index], excluding [index]. */
        fun peersOf(index: Int, gridSize: GridSize): List<Int> {
            val n = gridSize.size
            val r = index / n
            val c = index % n
            val box = gridSize.boxIndex(r, c)
            return (0 until n * n).filter { other ->
                other != index && (other / n == r || other % n == c || gridSize.boxIndex(other / n, other % n) == box)
            }
        }
    }
}
