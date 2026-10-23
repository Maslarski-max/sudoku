package com.maslarski.sudoku.data.local

import com.maslarski.sudoku.domain.engine.SudokuGenerator
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.domain.model.OutsideClues
import com.maslarski.sudoku.domain.model.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateSerializerTest {
    private val serializer = GameStateSerializer()
    private val game = GameState.fromPuzzle(
        SudokuGenerator().generate(GridSize.NINE, Difficulty.EASY, seed = 7L),
        nowEpochMillis = 0L,
    )

    @Test
    fun `out-of-lives deadline and cell combo state are persisted`() {
        val comboIndex = game.cells.indexOfFirst { !it.isGiven }
        val cells = game.cells.toMutableList().also {
            it[comboIndex] = it[comboIndex].copy(
                value = game.puzzle.solution[comboIndex],
                combo = true,
            )
        }
        val encoded = serializer.encode(
            game.copy(
                cells = cells,
                outOfLivesDeadlineEpochMillis = 1_700_000_060_000L,
                outOfLivesExpired = true,
                comboStreak = 3,
                lastCorrectElapsedMillis = 20_000L,
                countedCells = setOf(4, 9, 12),
            ),
        )
        val restored = serializer.decode(encoded)

        assertEquals(1_700_000_060_000L, restored.outOfLivesDeadlineEpochMillis)
        assertTrue(restored.outOfLivesExpired)
        assertEquals(3, restored.comboStreak)
        assertEquals(20_000L, restored.lastCorrectElapsedMillis)
        assertEquals(setOf(4, 9, 12), restored.countedCells)
        assertTrue(restored.cells[comboIndex].combo)
    }

    @Test
    fun `older snapshot without deadline and expired state uses defaults`() {
        val encoded = serializer.encode(game)
            .replace(
                ",\"outOfLivesDeadline\":null,\"outOfLivesExpired\":false,\"comboStreak\":0," +
                    "\"lastCorrectElapsedMillis\":null,\"countedCells\":[]",
                "",
            ).replace(",\"c\":false", "")
        val restored = serializer.decode(encoded)

        assertNull(restored.outOfLivesDeadlineEpochMillis)
        assertFalse(restored.outOfLivesExpired)
        assertEquals(0, restored.comboStreak)
        assertNull(restored.lastCorrectElapsedMillis)
        assertEquals(emptySet<Int>(), restored.countedCells)
        assertTrue(restored.cells.none { it.combo })
    }

    @Test
    fun `snapshot without expired state defaults to false`() {
        val encoded = serializer.encode(game).replace(",\"outOfLivesExpired\":false", "")

        assertFalse(serializer.decode(encoded).outOfLivesExpired)
    }

    @Test
    fun `legacy comboCells property is ignored and missing cell combo defaults false`() {
        val comboIndex = game.cells.indexOfFirst { !it.isGiven }
        val legacyJson = serializer.encode(game)
            .replace(",\"c\":false", "")
            .dropLast(1) + ",\"comboCells\":[$comboIndex]}"

        val restored = serializer.decode(legacyJson)

        assertFalse(restored.cells[comboIndex].combo)
    }

    @Test
    fun `variant and outside clues round trip`() {
        val clues = OutsideClues(
            rowsFromLeft = listOf(listOf(1, 2)),
            rowsFromRight = listOf(emptyList(), listOf(4)),
            columnsFromTop = listOf(listOf(7)),
            columnsFromBottom = listOf(listOf(9, 3)),
        )
        val variantGame = game.copy(
            puzzle = game.puzzle.copy(variant = Variant.OUTSIDE, outsideClues = clues),
        )

        val restored = serializer.decode(serializer.encode(variantGame))

        assertEquals(Variant.OUTSIDE, restored.puzzle.variant)
        assertEquals(clues, restored.puzzle.outsideClues)
    }

    @Test
    fun `old snapshot without variant metadata restores as classic`() {
        val encoded = serializer.encode(game)
            .replace(",\"variant\":null", "")
            .replace(",\"outsideClues\":null", "")

        val restored = serializer.decode(encoded)

        assertNull(restored.puzzle.variant)
        assertNull(restored.puzzle.outsideClues)
    }
}
