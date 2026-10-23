package com.maslarski.sudoku.domain.engine

import com.maslarski.sudoku.domain.model.Topology
import kotlin.random.Random

/** Bitmask MRV solver for topologies whose units each contain the digits 1..9 once. */
class UnitSolver(
    private val topology: Topology,
    /** Bits set here are excluded candidates for the corresponding cell. */
    private val extraMasks: IntArray? = null,
    private val timeSource: () -> Long = System::nanoTime,
) {
    private val fullMask = (1 shl 9) - 1

    data class CountResult(val count: Int, val exhausted: Boolean) {
        val isUnique: Boolean get() = count == 1 && exhausted
    }

    init {
        require(topology.units.all { it.size == 9 }) { "UnitSolver requires units of nine cells" }
        require(extraMasks == null || extraMasks.size == topology.cellCount)
    }

    fun solve(
        grid: IntArray,
        random: Random? = null,
        deadlineNanos: Long = Long.MAX_VALUE,
    ): IntArray? {
        val board = grid.copyOf()
        val state = buildState(board) ?: return null
        val counter = Counter(limit = 1, deadlineNanos = deadlineNanos)
        search(state, limit = 1, nodeBudget = Long.MAX_VALUE, random = random, counter = counter)
        return board.takeIf { counter.found > 0 && !counter.budgetExceeded }
    }

    fun countSolutions(
        grid: IntArray,
        limit: Int = 2,
        nodeBudget: Long = 2_000_000L,
        deadlineNanos: Long = Long.MAX_VALUE,
    ): CountResult {
        require(limit > 0)
        val state = buildState(grid.copyOf()) ?: return CountResult(0, exhausted = true)
        val counter = Counter(limit, deadlineNanos)
        search(state, limit, nodeBudget, random = null, counter = counter)
        return CountResult(counter.found, exhausted = !counter.budgetExceeded)
    }

    fun candidates(grid: IntArray, index: Int): Int {
        require(grid.size == topology.cellCount)
        if (!topology.active[index] || grid[index] != 0) return 0
        val state = buildState(grid.copyOf()) ?: return 0
        return state.candidatesAt(index)
    }

    fun isConsistent(grid: IntArray): Boolean = buildState(grid.copyOf()) != null

    fun isSolved(grid: IntArray): Boolean =
        grid.size == topology.cellCount &&
            topology.activeIndices.all { grid[it] in 1..9 } &&
            buildState(grid.copyOf()) != null

    private class Counter(val limit: Int, val deadlineNanos: Long) {
        var found = 0
        var budgetExceeded = false
    }

    private inner class State(
        val board: IntArray,
        val used: IntArray,
    ) {
        fun candidatesAt(index: Int): Int {
            var usedMask = extraMasks?.get(index) ?: 0
            topology.unitsFor(index).forEach { unitIndex ->
                usedMask = usedMask or used[unitIndex]
            }
            return fullMask and usedMask.inv()
        }

        fun place(index: Int, value: Int) {
            val bit = 1 shl (value - 1)
            board[index] = value
            topology.unitsFor(index).forEach { unitIndex -> used[unitIndex] = used[unitIndex] or bit }
        }

        fun remove(index: Int, value: Int) {
            val bit = (1 shl (value - 1)).inv()
            board[index] = 0
            topology.unitsFor(index).forEach { unitIndex -> used[unitIndex] = used[unitIndex] and bit }
        }
    }

    private fun buildState(board: IntArray): State? {
        if (board.size != topology.cellCount) return null
        val used = IntArray(topology.units.size)
        for (index in board.indices) {
            val value = board[index]
            if (!topology.active[index]) {
                if (value != 0) return null
                continue
            }
            if (value == 0) continue
            if (value !in 1..9) return null
            val bit = 1 shl (value - 1)
            if ((extraMasks?.get(index) ?: 0) and bit != 0) return null
            for (unitIndex in topology.unitsFor(index)) {
                if (used[unitIndex] and bit != 0) return null
                used[unitIndex] = used[unitIndex] or bit
            }
        }
        return State(board, used)
    }

    private fun search(
        state: State,
        limit: Int,
        nodeBudget: Long,
        random: Random?,
        counter: Counter?,
    ): Boolean {
        var nodes = 0L
        fun recurse(): Boolean {
            if (timeSource() >= counterDeadline(counter)) {
                counter?.budgetExceeded = true
                return true
            }
            if (++nodes > nodeBudget) {
                counter?.budgetExceeded = true
                return true
            }
            var bestIndex = -1
            var bestMask = 0
            var bestCount = Int.MAX_VALUE
            for (index in topology.activeIndices) {
                if (state.board[index] != 0) continue
                val mask = state.candidatesAt(index)
                val count = Integer.bitCount(mask)
                if (count == 0) return false
                if (count < bestCount) {
                    bestIndex = index
                    bestMask = mask
                    bestCount = count
                    if (count == 1) break
                }
            }
            if (bestIndex < 0) {
                if (counter == null) return true
                counter.found++
                return counter.found >= limit
            }
            val values = buildList {
                var mask = bestMask
                while (mask != 0) {
                    val bit = mask and -mask
                    mask = mask xor bit
                    add(Integer.numberOfTrailingZeros(bit) + 1)
                }
            }.let { if (random == null) it else it.shuffled(random) }
            for (value in values) {
                state.place(bestIndex, value)
                if (recurse()) return true
                state.remove(bestIndex, value)
            }
            return false
        }
        return recurse()
    }

    private fun counterDeadline(counter: Counter?): Long = counter?.deadlineNanos ?: Long.MAX_VALUE
}
