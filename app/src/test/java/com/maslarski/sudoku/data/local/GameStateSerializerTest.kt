package com.maslarski.sudoku.data.local

import com.maslarski.sudoku.domain.engine.SudokuGenerator
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
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
    fun `out-of-lives deadline and expired state are persisted`() {
        val encoded = serializer.encode(
            game.copy(
                outOfLivesDeadlineEpochMillis = 1_700_000_060_000L,
                outOfLivesExpired = true,
            ),
        )
        val restored = serializer.decode(encoded)

        assertEquals(1_700_000_060_000L, restored.outOfLivesDeadlineEpochMillis)
        assertTrue(restored.outOfLivesExpired)
    }

    @Test
    fun `older snapshot without deadline and expired state uses defaults`() {
        val encoded = serializer.encode(game)
            .replace(",\"outOfLivesDeadline\":null,\"outOfLivesExpired\":false", "")
        val restored = serializer.decode(encoded)

        assertNull(restored.outOfLivesDeadlineEpochMillis)
        assertFalse(restored.outOfLivesExpired)
    }

    @Test
    fun `snapshot without expired state defaults to false`() {
        val encoded = serializer.encode(game).replace(",\"outOfLivesExpired\":false", "")

        assertFalse(serializer.decode(encoded).outOfLivesExpired)
    }
}
