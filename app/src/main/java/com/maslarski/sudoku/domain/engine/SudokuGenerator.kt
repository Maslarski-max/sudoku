package com.maslarski.sudoku.domain.engine

import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.OutsideClues
import com.maslarski.sudoku.domain.model.Puzzle
import com.maslarski.sudoku.domain.model.Topology
import com.maslarski.sudoku.domain.model.Variant
import kotlin.random.Random

/**
 * Generates puzzles with a unique solution for any [GridSize].
 *
 * 1. Build a complete grid from the canonical Latin-square pattern for R x C boxes, then apply the
 *    validity-preserving symmetries (digit relabelling, row/column permutations within bands/stacks,
 *    band/stack permutations, optional transpose for square boxes). This is O(n^2) and never fails.
 * 2. Remove cells in random order while the solver confirms the solution stays unique. Removal stops
 *    when the difficulty's target is reached or the time budget expires, so large grids finish promptly
 *    and the result is always uniquely solvable.
 */
class SudokuGenerator(
    private val timeSource: () -> Long = System::nanoTime,
) {

    fun generate(
        gridSize: GridSize,
        difficulty: Difficulty,
        seed: Long = Random.nextLong(),
        timeBudgetMillis: Long = defaultBudgetMillis(gridSize),
        deterministic: Boolean = false,
    ): Puzzle {
        val random = Random(seed)
        val solution = fillGrid(gridSize, random)
        val givens = carve(gridSize, difficulty, solution, random, timeBudgetMillis, deterministic)
        return Puzzle(gridSize, difficulty, givens, solution, seed)
    }

    fun generateVariant(
        variant: Variant,
        difficulty: Difficulty,
        seed: Long = Random.nextLong(),
        timeBudgetMillis: Long = VARIANT_TIME_BUDGET_MILLIS,
    ): Puzzle {
        val gridSize = GridSize.NINE
        val topology = Topology.forVariant(variant, gridSize)
        val random = Random(seed)
        val deadline = timeSource() + timeBudgetMillis * 1_000_000L
        val solver = UnitSolver(topology, timeSource = timeSource)
        val solution = solver.solve(IntArray(topology.cellCount), random, deadline)
            ?: error("Could not fill ${variant.displayName} topology")
        val clues = if (variant == Variant.OUTSIDE) generateOutsideClues(solution, random) else null
        val extraMasks = clues?.exclusionMasks(topology)
        val givens = carveVariant(
            topology = topology,
            difficulty = difficulty,
            solution = solution,
            extraMasks = extraMasks,
            random = random,
            deadlineNanos = deadline,
        )
        return Puzzle(
            gridSize = gridSize,
            difficulty = difficulty,
            givens = givens,
            solution = solution,
            seed = seed,
            variant = variant,
            outsideClues = clues,
        )
    }

    /** Produces a complete valid grid. */
    fun fillGrid(gridSize: GridSize, random: Random): IntArray {
        val n = gridSize.size
        val boxRows = gridSize.boxRows
        val boxCols = gridSize.boxCols

        // Canonical pattern: value(r, c) = (boxCols * (r % boxRows) + r / boxRows + c) % n, offset by one.
        val base = IntArray(n * n) { i ->
            val r = i / n
            val c = i % n
            (boxCols * (r % boxRows) + r / boxRows + c) % n + 1
        }

        val digitMap = (1..n).shuffled(random)
        val rowMap = permuteWithinGroups(n, boxRows, random)
        val colMap = permuteWithinGroups(n, boxCols, random)
        val transpose = boxRows == boxCols && random.nextBoolean()

        val out = IntArray(n * n)
        for (r in 0 until n) {
            for (c in 0 until n) {
                val v = digitMap[base[rowMap[r] * n + colMap[c]] - 1]
                if (transpose) out[c * n + r] = v else out[r * n + c] = v
            }
        }
        return out
    }

    /**
     * Permutation of 0 until n that shuffles groups of [groupSize] consecutive indices among themselves
     * and shuffles the indices inside each group. Both operations preserve Sudoku validity.
     */
    private fun permuteWithinGroups(n: Int, groupSize: Int, random: Random): IntArray {
        val groupCount = n / groupSize
        val groupOrder = (0 until groupCount).shuffled(random)
        val result = IntArray(n)
        for (g in 0 until groupCount) {
            val inner = (0 until groupSize).shuffled(random)
            for (k in 0 until groupSize) {
                result[g * groupSize + k] = groupOrder[g] * groupSize + inner[k]
            }
        }
        return result
    }

    private fun carve(
        gridSize: GridSize,
        difficulty: Difficulty,
        solution: IntArray,
        random: Random,
        timeBudgetMillis: Long,
        deterministic: Boolean,
    ): IntArray {
        val solver = SudokuSolver(gridSize)
        val grid = solution.copyOf()
        val targetEmpty = (gridSize.cellCount * difficulty.emptyFraction).toInt()
        val deadline = if (deterministic) Long.MAX_VALUE else timeSource() + timeBudgetMillis * 1_000_000L
        val nodeBudget = nodeBudgetFor(gridSize)

        var empty = 0
        val order = grid.indices.shuffled(random)
        for (index in order) {
            if (empty >= targetEmpty) break
            if (!deterministic && timeSource() > deadline) break
            val removed = grid[index]
            grid[index] = 0
            // Cheap pre-check: if the removed cell has more than one candidate, the puzzle *might* be non-unique.
            // Cells with exactly one candidate are always safe to remove.
            val safe = Integer.bitCount(solver.candidates(grid, index)) == 1 ||
                solver.countSolutions(grid, limit = 2, nodeBudget = nodeBudget).isUnique
            if (safe) empty++ else grid[index] = removed
        }
        return grid
    }

    private fun carveVariant(
        topology: Topology,
        difficulty: Difficulty,
        solution: IntArray,
        extraMasks: IntArray?,
        random: Random,
        deadlineNanos: Long,
    ): IntArray {
        val targetGivens = when (topology.width to difficulty) {
            21 to Difficulty.EASY -> 190
            21 to Difficulty.MEDIUM -> 165
            21 to Difficulty.HARD -> 145
            15 to Difficulty.EASY -> 105
            15 to Difficulty.MEDIUM -> 92
            15 to Difficulty.HARD -> 82
            else -> when (difficulty) {
                Difficulty.EASY -> 30
                Difficulty.MEDIUM -> 25
                Difficulty.HARD -> 20
            }
        }
        val solver = UnitSolver(topology, extraMasks, timeSource)
        val grid = solution.copyOf()
        val order = topology.activeIndices.toList().shuffled(random)
        var givens = topology.activeIndices.size
        for (index in order) {
            if (givens <= targetGivens || timeSource() >= deadlineNanos) break
            val removed = grid[index]
            grid[index] = 0
            val isForced = Integer.bitCount(solver.candidates(grid, index)) == 1
            val remainsUnique = isForced ||
                solver.countSolutions(
                    grid,
                    limit = 2,
                    nodeBudget = VARIANT_NODE_BUDGET,
                    deadlineNanos = deadlineNanos,
                ).isUnique
            if (remainsUnique) {
                givens--
            } else {
                grid[index] = removed
            }
        }
        return grid
    }

    private fun generateOutsideClues(solution: IntArray, random: Random): OutsideClues {
        fun choose(values: List<Int>): List<Int> =
            if (random.nextDouble() < OUTSIDE_CLUE_PROBABILITY) {
                values.distinct().shuffled(random).take(random.nextInt(1, 3))
            } else {
                emptyList()
            }

        val left = (0 until 9).map { row -> choose((0 until 3).map { col -> solution[row * 9 + col] }) }
        val right = (0 until 9).map { row -> choose((0 until 3).map { offset -> solution[row * 9 + 8 - offset] }) }
        val top = (0 until 9).map { col -> choose((0 until 3).map { row -> solution[row * 9 + col] }) }
        val bottom = (0 until 9).map { col -> choose((0 until 3).map { offset -> solution[(8 - offset) * 9 + col] }) }
        return OutsideClues(left, right, top, bottom)
    }

    private fun nodeBudgetFor(gridSize: GridSize): Long = when (gridSize) {
        GridSize.NINE -> 200_000L
        GridSize.TWELVE -> 300_000L
        GridSize.FIFTEEN -> 400_000L
        GridSize.EIGHTEEN -> 500_000L
    }

    companion object {
        const val VARIANT_TIME_BUDGET_MILLIS = 8_000L
        private const val VARIANT_NODE_BUDGET = 300_000L
        private const val OUTSIDE_CLUE_PROBABILITY = 0.6

        fun defaultBudgetMillis(gridSize: GridSize): Long = when (gridSize) {
            GridSize.NINE -> 2_000L
            GridSize.TWELVE -> 4_000L
            GridSize.FIFTEEN -> 6_000L
            GridSize.EIGHTEEN -> 8_000L
        }
    }
}
