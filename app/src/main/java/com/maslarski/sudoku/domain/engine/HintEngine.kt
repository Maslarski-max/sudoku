package com.maslarski.sudoku.domain.engine

import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Hint
import com.maslarski.sudoku.domain.model.Topology
import com.maslarski.sudoku.domain.model.UnitKind

/**
 * Produces an explained next step. Strategies are tried from most to least instructive:
 * 1. fix an incorrect entry, 2. naked single, 3. hidden single (row, column, box), 4. reveal.
 */
class HintEngine {

    fun nextHint(state: GameState): Hint {
        val topology = state.topology
        val grid = state.valuesArray()
        val solution = state.puzzle.solution
        val active = topology.activeIndices

        val wrong = active.firstOrNull { grid[it] != 0 && grid[it] != solution[it] }
        if (wrong != null) return Hint.FixMistake(wrong, solution[wrong], grid[wrong])

        if (active.none { grid[it] == 0 }) return Hint.None

        val candidates = candidates(state, grid)
        nakedSingle(grid, candidates, topology)?.let { return it }
        hiddenSingle(
            grid,
            candidates,
            topology,
            if (state.variant == null) state.gridSize.size else 9,
        )?.let { return it }

        val target = active.filter { grid[it] == 0 }
            .minByOrNull { Integer.bitCount(candidates[it]) } ?: return Hint.None
        return Hint.Reveal(target, solution[target])
    }

    fun hintFor(state: GameState, index: Int): Hint? {
        val topology = state.topology
        if (!topology.active[index]) return null
        val grid = state.valuesArray()
        val solution = state.puzzle.solution
        if (grid[index] == solution[index] && !state.cells[index].isGiven) return null
        if (state.cells[index].isGiven) return null
        if (grid[index] != 0) return Hint.FixMistake(index, solution[index], grid[index])
        if (topology.activeIndices.any { grid[it] != 0 && grid[it] != solution[it] }) {
            return Hint.Reveal(index, solution[index])
        }

        val candidates = candidates(state, grid)
        val mask = candidates[index]
        if (Integer.bitCount(mask) == 1) {
            val value = Integer.numberOfTrailingZeros(mask) + 1
            if (value == solution[index]) {
                return Hint.NakedSingle(index, value, topology.peers(index).filter { grid[it] != 0 })
            }
        }

        for (unitIndex in topology.unitsFor(index)) {
            val kind = topology.unitKinds[unitIndex].toHintUnit()
            singleInUnit(
                topology.units[unitIndex],
                grid,
                candidates,
                kind,
                valueCount = if (state.variant == null) state.gridSize.size else 9,
                onlyValue = solution[index],
            )
                ?.takeIf { it.index == index && it.value == solution[index] }
                ?.let { return it }
        }
        return Hint.Reveal(index, solution[index])
    }

    private fun candidates(state: GameState, grid: IntArray): IntArray {
        val topology = state.topology
        return if (state.variant == null) {
            val solver = SudokuSolver(state.gridSize)
            IntArray(grid.size) { index ->
                if (topology.active[index]) solver.candidates(grid, index) else 0
            }
        } else {
            val masks = state.puzzle.outsideClues?.exclusionMasks(topology)
            val solver = UnitSolver(topology, masks)
            IntArray(grid.size) { index ->
                if (topology.active[index]) solver.candidates(grid, index) else 0
            }
        }
    }

    private fun nakedSingle(grid: IntArray, candidates: IntArray, topology: Topology): Hint? {
        for (index in topology.activeIndices) {
            if (grid[index] != 0) continue
            val mask = candidates[index]
            if (Integer.bitCount(mask) == 1) {
                val value = Integer.numberOfTrailingZeros(mask) + 1
                return Hint.NakedSingle(index, value, topology.peers(index).filter { grid[it] != 0 })
            }
        }
        return null
    }

    private fun hiddenSingle(
        grid: IntArray,
        candidates: IntArray,
        topology: Topology,
        valueCount: Int,
    ): Hint? {
        for (unitIndex in topology.units.indices) {
            val unit = topology.units[unitIndex]
            val kind = topology.unitKinds[unitIndex].toHintUnit()
            singleInUnit(unit, grid, candidates, kind, valueCount)?.let { return it }
        }
        return null
    }

    private fun singleInUnit(
        cells: IntArray,
        grid: IntArray,
        candidates: IntArray,
        unit: Hint.HiddenSingle.Unit,
        valueCount: Int,
        onlyValue: Int? = null,
    ): Hint? {
        for (value in onlyValue?.let(::listOf) ?: (1..valueCount).toList()) {
            val bit = 1 shl (value - 1)
            if (cells.any { grid[it] == value }) continue
            val spots = cells.filter { grid[it] == 0 && candidates[it] and bit != 0 }
            if (spots.size == 1) {
                return Hint.HiddenSingle(spots[0], value, unit, cells.filter { it != spots[0] })
            }
        }
        return null
    }

    private fun UnitKind.toHintUnit(): Hint.HiddenSingle.Unit = when (this) {
        UnitKind.ROW -> Hint.HiddenSingle.Unit.ROW
        UnitKind.COLUMN -> Hint.HiddenSingle.Unit.COLUMN
        UnitKind.BOX -> Hint.HiddenSingle.Unit.BOX
    }

    companion object {
        /** All cells sharing a row, column, or box with [index], excluding [index]. */
        fun peersOf(index: Int, gridSize: GridSize): List<Int> =
            peersOf(index, Topology.classic(gridSize))

        fun peersOf(index: Int, topology: Topology): List<Int> = topology.peers(index).toList()
    }
}
