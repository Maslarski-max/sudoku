package com.maslarski.sudoku.domain.engine

import com.maslarski.sudoku.domain.model.Cell
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.Puzzle
import com.maslarski.sudoku.domain.model.Variant
import com.maslarski.sudoku.domain.model.toTournamentCompletionPayload
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TournamentScoringFixtureTest {

    @Test
    fun `writes Kotlin scoring fixtures for callable parity tests`() {
        val variantPuzzles = Variant.entries.associateWith { variant ->
            SudokuGenerator().generateVariant(
                variant = variant,
                difficulty = Difficulty.EASY,
                seed = 9_100L + variant.ordinal,
                timeBudgetMillis = 2_000L,
            )
        }
        val scenarios = listOf(
            Scenario("classic-9-easy", GridSize.NINE, Difficulty.EASY),
            Scenario("classic-9-medium-hints-combo", GridSize.NINE, Difficulty.MEDIUM, hints = 1, combo = 3),
            Scenario("classic-12-easy", GridSize.TWELVE, Difficulty.EASY, combo = 2),
            Scenario("classic-12-hard-slow", GridSize.TWELVE, Difficulty.HARD, hints = 2, mistakes = 1, elapsedOverTarget = true),
            Scenario("classic-15-medium-mistakes", GridSize.FIFTEEN, Difficulty.MEDIUM, hints = 2, mistakes = 3, combo = 4),
            Scenario("classic-18-hard", GridSize.EIGHTEEN, Difficulty.HARD, mistakes = 5, combo = 5, elapsedOverTarget = true),
            Scenario("samurai-easy", GridSize.NINE, Difficulty.EASY, Variant.SAMURAI, combo = 2),
            Scenario("samurai-hard-hints", GridSize.NINE, Difficulty.HARD, Variant.SAMURAI, hints = 2, mistakes = 2, combo = 8),
            Scenario("flower-medium", GridSize.NINE, Difficulty.MEDIUM, Variant.FLOWER, hints = 1, mistakes = 1, combo = 2),
            Scenario("flower-hard-slow", GridSize.NINE, Difficulty.HARD, Variant.FLOWER, hints = 3, mistakes = 5, elapsedOverTarget = true),
            Scenario("outside-easy", GridSize.NINE, Difficulty.EASY, Variant.OUTSIDE, combo = 3),
            Scenario("daily-medium", GridSize.NINE, Difficulty.MEDIUM, hints = 2, mistakes = 1, combo = 4, dailyDate = "2026-10-24"),
        )
        val fixtures = scenarios.map { scenario ->
            val state = completedState(scenario, variantPuzzles[scenario.variant])
            ScoringFixture(
                name = scenario.name,
                payload = state.toTournamentCompletionPayload(),
                expectedPoints = ScoringRules.points(state),
            )
        }

        assertEquals(12, fixtures.size)
        assertEquals("CLASSIC", fixtures.first().payload.kind)
        assertEquals("VARIANT", fixtures.first { it.name == "samurai-easy" }.payload.kind)
        val samurai = fixtures.first { it.name == "samurai-easy" }.payload
        assertEquals("SAMURAI", samurai.variant)
        assertEquals(21 * 21, samurai.solutionCells.size)
        val outside = fixtures.first { it.name == "outside-easy" }.payload
        assertEquals("OUTSIDE", outside.variant)
        assertEquals(9 * 9, outside.solutionCells.size)
        assertEquals(9, outside.outsideClues?.rowsFromLeft?.size)
        assertEquals(9, outside.outsideClues?.rowsFromRight?.size)
        assertEquals(9, outside.outsideClues?.columnsFromTop?.size)
        assertEquals(9, outside.outsideClues?.columnsFromBottom?.size)
        assertEquals("DAILY", fixtures.last().payload.kind)

        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
        val fixtureFile = File(root, "functions/test/fixtures/scoring.json")
        assertTrue(fixtureFile.parentFile.mkdirs() || fixtureFile.parentFile.isDirectory)
        fixtureFile.writeText(
            Json { prettyPrint = true; explicitNulls = false }.encodeToString(fixtures) + "\n",
        )
        assertTrue(fixtureFile.isFile)
    }

    private fun completedState(scenario: Scenario, generatedVariant: Puzzle?): GameState {
        val topology = com.maslarski.sudoku.domain.model.Topology.forVariant(
            scenario.variant,
            scenario.gridSize,
        )
        val solution = generatedVariant?.solution?.copyOf() ?: canonicalSolution(scenario.gridSize)
        val emptyCount = when {
            scenario.variant == null -> (scenario.gridSize.cellCount * scenario.difficulty.emptyFraction).toInt()
            else -> topology.activeIndices.size - targetGivens(topology.width, scenario.difficulty)
        }
        val emptyIndices = topology.activeIndices.take(emptyCount).toSet()
        val givens = IntArray(topology.cellCount) { index ->
            if (index in emptyIndices) 0 else solution[index]
        }
        val puzzle = Puzzle(
            gridSize = scenario.gridSize,
            difficulty = scenario.difficulty,
            givens = givens,
            solution = solution,
            seed = scenario.variant?.ordinal?.toLong() ?: scenario.gridSize.size.toLong(),
            variant = scenario.variant,
            outsideClues = generatedVariant?.outsideClues,
        )
        val comboIndices = topology.activeIndices
            .filter { givens[it] == 0 }
            .take(scenario.combo)
            .toSet()
        val cells = solution.indices.map { index ->
            Cell(
                value = solution[index],
                isGiven = givens[index] != 0,
                combo = index in comboIndices,
            )
        }
        var state = GameState(
            puzzle = puzzle,
            cells = cells,
            elapsedMillis = 0L,
            moves = emptyCount + 3,
            mistakes = scenario.mistakes,
            hintsUsed = scenario.hints,
            isComplete = true,
            startedAtEpochMillis = 0L,
            dailyDate = scenario.dailyDate,
        )
        val target = ScoringRules.targetTimeMillis(state)
        state = state.copy(
            elapsedMillis = if (scenario.elapsedOverTarget) target + 1_000L else target / 2,
        )
        return state
    }

    private fun canonicalSolution(gridSize: GridSize): IntArray {
        val n = gridSize.size
        return IntArray(n * n) { index ->
            val row = index / n
            val column = index % n
            (gridSize.boxCols * (row % gridSize.boxRows) + row / gridSize.boxRows + column) % n + 1
        }
    }

    private fun targetGivens(width: Int, difficulty: Difficulty): Int = when (width to difficulty) {
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

    private data class Scenario(
        val name: String,
        val gridSize: GridSize,
        val difficulty: Difficulty,
        val variant: Variant? = null,
        val hints: Int = 0,
        val mistakes: Int = 0,
        val combo: Int = 0,
        val elapsedOverTarget: Boolean = false,
        val dailyDate: String? = null,
    )

    @Serializable
    private data class ScoringFixture(
        val name: String,
        val payload: com.maslarski.sudoku.domain.model.TournamentCompletionPayload,
        val expectedPoints: Int,
    )
}
