package com.maslarski.sudoku.domain.engine

import com.maslarski.sudoku.domain.model.GridSize

/**
 * Bitmask constraint-propagation solver with minimum-remaining-values backtracking.
 * Works for any [GridSize]; a value v (1..size) is represented by bit (v - 1).
 */
class SudokuSolver(private val gridSize: GridSize) {

    private val n = gridSize.size
    private val fullMask = (1 shl n) - 1

    /** Result of [countSolutions]: the number of solutions found (capped at `limit`) and whether the search was cut short. */
    data class CountResult(val count: Int, val exhausted: Boolean) {
        val isUnique: Boolean get() = count == 1 && exhausted
    }

    /** Solve [grid] (0 = empty). Returns a solved copy or null when unsolvable. */
    fun solve(grid: IntArray): IntArray? {
        val board = grid.copyOf()
        val state = buildState(board) ?: return null
        return if (search(state, limit = 1, budget = Long.MAX_VALUE, sink = null)) board else null
    }

    /**
     * Count solutions up to [limit]. [nodeBudget] caps the number of search nodes visited so that
     * pathological grids cannot stall the generator; when exceeded, [CountResult.exhausted] is false.
     */
    fun countSolutions(grid: IntArray, limit: Int = 2, nodeBudget: Long = 2_000_000L): CountResult {
        val board = grid.copyOf()
        val state = buildState(board) ?: return CountResult(0, exhausted = true)
        val counter = Counter(limit)
        search(state, limit, nodeBudget, counter)
        return CountResult(counter.found, exhausted = !counter.budgetExceeded)
    }

    /** True when every filled cell respects Sudoku constraints (empty cells are ignored). */
    fun isConsistent(grid: IntArray): Boolean = buildState(grid.copyOf()) != null

    /** True when the grid is fully filled and valid. */
    fun isSolved(grid: IntArray): Boolean = grid.none { it == 0 } && isConsistent(grid)

    /** Candidate bitmask for an empty cell given the current grid, or 0 for filled cells. */
    fun candidates(grid: IntArray, index: Int): Int {
        if (grid[index] != 0) return 0
        val row = index / n
        val col = index % n
        var used = 0
        for (i in 0 until n) {
            val rv = grid[row * n + i]
            if (rv != 0) used = used or (1 shl (rv - 1))
            val cv = grid[i * n + col]
            if (cv != 0) used = used or (1 shl (cv - 1))
        }
        val boxRow0 = (row / gridSize.boxRows) * gridSize.boxRows
        val boxCol0 = (col / gridSize.boxCols) * gridSize.boxCols
        for (r in boxRow0 until boxRow0 + gridSize.boxRows) {
            for (c in boxCol0 until boxCol0 + gridSize.boxCols) {
                val v = grid[r * n + c]
                if (v != 0) used = used or (1 shl (v - 1))
            }
        }
        return fullMask and used.inv()
    }

    private class Counter(val limit: Int) {
        var found = 0
        var budgetExceeded = false
    }

    /** Mutable search state: the board plus per-unit "used" masks. */
    private inner class State(
        val board: IntArray,
        val rowUsed: IntArray,
        val colUsed: IntArray,
        val boxUsed: IntArray,
    ) {
        fun candidatesAt(index: Int): Int {
            val r = index / n
            val c = index % n
            val used = rowUsed[r] or colUsed[c] or boxUsed[gridSize.boxIndex(r, c)]
            return fullMask and used.inv()
        }

        fun place(index: Int, value: Int) {
            val bit = 1 shl (value - 1)
            val r = index / n
            val c = index % n
            board[index] = value
            rowUsed[r] = rowUsed[r] or bit
            colUsed[c] = colUsed[c] or bit
            val b = gridSize.boxIndex(r, c)
            boxUsed[b] = boxUsed[b] or bit
        }

        fun remove(index: Int, value: Int) {
            val bit = (1 shl (value - 1)).inv()
            val r = index / n
            val c = index % n
            board[index] = 0
            rowUsed[r] = rowUsed[r] and bit
            colUsed[c] = colUsed[c] and bit
            val b = gridSize.boxIndex(r, c)
            boxUsed[b] = boxUsed[b] and bit
        }
    }

    /** Builds a [State] from a board, returning null if the givens already conflict. */
    private fun buildState(board: IntArray): State? {
        require(board.size == n * n) { "board must have ${n * n} cells" }
        val rowUsed = IntArray(n)
        val colUsed = IntArray(n)
        val boxUsed = IntArray(n)
        for (i in board.indices) {
            val v = board[i]
            if (v == 0) continue
            if (v < 1 || v > n) return null
            val bit = 1 shl (v - 1)
            val r = i / n
            val c = i % n
            val b = gridSize.boxIndex(r, c)
            if (rowUsed[r] and bit != 0 || colUsed[c] and bit != 0 || boxUsed[b] and bit != 0) return null
            rowUsed[r] = rowUsed[r] or bit
            colUsed[c] = colUsed[c] or bit
            boxUsed[b] = boxUsed[b] or bit
        }
        return State(board, rowUsed, colUsed, boxUsed)
    }

    /**
     * Depth-first search with MRV. When [sink] is null, stops at the first solution and leaves it on the board,
     * returning true. Otherwise counts solutions into [sink] (up to limit) and returns true when the limit is hit.
     */
    private fun search(state: State, limit: Int, budget: Long, sink: Counter?): Boolean {
        var nodes = 0L

        fun recurse(): Boolean {
            if (++nodes > budget) {
                sink?.budgetExceeded = true
                return true
            }
            // Pick the empty cell with the fewest candidates.
            var bestIndex = -1
            var bestMask = 0
            var bestCount = Int.MAX_VALUE
            val board = state.board
            for (i in board.indices) {
                if (board[i] != 0) continue
                val mask = state.candidatesAt(i)
                val count = Integer.bitCount(mask)
                if (count == 0) return false
                if (count < bestCount) {
                    bestCount = count
                    bestMask = mask
                    bestIndex = i
                    if (count == 1) break
                }
            }
            if (bestIndex == -1) {
                if (sink == null) return true
                sink.found++
                return sink.found >= limit
            }
            var mask = bestMask
            while (mask != 0) {
                val bit = mask and -mask
                mask = mask xor bit
                val value = Integer.numberOfTrailingZeros(bit) + 1
                state.place(bestIndex, value)
                if (recurse()) return true
                state.remove(bestIndex, value)
            }
            return false
        }

        return recurse()
    }
}
