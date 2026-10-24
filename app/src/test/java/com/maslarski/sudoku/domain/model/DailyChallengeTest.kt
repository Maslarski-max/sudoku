package com.maslarski.sudoku.domain.model

import com.maslarski.sudoku.domain.engine.SudokuGenerator
import java.time.YearMonth
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyChallengeTest {

    @Test
    fun `same date produces identical daily puzzle from fresh generators`() {
        val date = "2024-02-29"
        val seed = DailyChallenge.seed(date).toLong()
        val first = SudokuGenerator { 1L }.generate(
            GridSize.NINE,
            Difficulty.MEDIUM,
            seed = seed,
            deterministic = true,
        )
        val second = SudokuGenerator { Long.MAX_VALUE }.generate(
            GridSize.NINE,
            Difficulty.MEDIUM,
            seed = seed,
            deterministic = true,
        )

        assertArrayEquals(first.givens, second.givens)
        assertArrayEquals(first.solution, second.solution)
    }

    @Test
    fun `different dates produce different daily puzzles`() {
        fun generate(date: String) = SudokuGenerator().generate(
            GridSize.NINE,
            Difficulty.MEDIUM,
            seed = DailyChallenge.seed(date).toLong(),
            deterministic = true,
        )

        val first = generate("2024-03-01")
        val second = generate("2024-03-02")

        assertFalse(first.givens.contentEquals(second.givens) && first.solution.contentEquals(second.solution))
    }

    @Test
    fun `month completion handles thirty and thirty one day months`() {
        fun stamps(month: YearMonth, missingDay: Int? = null) =
            (1..month.lengthOfMonth()).filter { it != missingDay }.map { month.atDay(it).toString() }.toSet()

        assertTrue(DailyChallenge.isMonthComplete(YearMonth.of(2025, 4), stamps(YearMonth.of(2025, 4))))
        assertTrue(DailyChallenge.isMonthComplete(YearMonth.of(2025, 3), stamps(YearMonth.of(2025, 3))))
        assertFalse(
            DailyChallenge.isMonthComplete(
                YearMonth.of(2025, 4),
                stamps(YearMonth.of(2025, 4), missingDay = 17),
            ),
        )
    }

    @Test
    fun `month completion handles leap February`() {
        val month = YearMonth.of(2024, 2)
        val stamps = (1..29).map { month.atDay(it).toString() }.toSet()
        assertTrue(DailyChallenge.isMonthComplete(month, stamps))
        assertFalse(DailyChallenge.isMonthComplete(month, stamps - "2024-02-29"))
    }

    @Test
    fun `daily date and seed use UTC calendar date`() {
        val epoch = java.time.Instant.parse("2024-01-01T00:30:00Z").toEpochMilli()
        assertTrue(DailyChallenge.dateStringAt(epoch) == "2024-01-01")
        assertTrue(DailyChallenge.seed("2024-01-01") == 20240101)
    }
}
