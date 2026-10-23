package com.maslarski.sudoku.data.local

import com.maslarski.sudoku.domain.engine.SudokuGenerator
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GameState
import com.maslarski.sudoku.domain.model.GridSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameStateSerializerTest {
    private val serializer = GameStateSerializer()
    private val game = GameState.fromPuzzle(
        SudokuGenerator().generate(GridSize.NINE, Difficulty.EASY, seed = 7L),
        nowEpochMillis = 0L,
    )

    @Test
    fun `out-of-lives deadline is persisted`() {
        val encoded = serializer.encode(game.copy(outOfLivesDeadlineEpochMillis = 1_700_000_060_000L))

        assertEquals(1_700_000_060_000L, serializer.decode(encoded).outOfLivesDeadlineEpochMillis)
    }

    @Test
    fun `older snapshot without deadline defaults to null`() {
        val encoded = serializer.encode(game).replace(",\"outOfLivesDeadline\":null", "")

        assertNull(serializer.decode(encoded).outOfLivesDeadlineEpochMillis)
    }
}
