package com.maslarski.sudoku.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object DailyChallenge {
    const val SLOT_ID = 201

    private val seedFormatter = DateTimeFormatter.BASIC_ISO_DATE

    fun dateAt(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate()

    fun dateStringAt(epochMillis: Long): String = dateAt(epochMillis).toString()

    fun seed(date: String): Int = LocalDate.parse(date).format(seedFormatter).toInt()

    fun isMonthComplete(month: YearMonth, stampedDates: Set<String>): Boolean =
        (1..month.lengthOfMonth()).all { day ->
            month.atDay(day).toString() in stampedDates
        }

    fun hasWeekStreak(stampedDates: Set<String>): Boolean {
        val dates = stampedDates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        return dates.any { start -> (0L..6L).all { start.plusDays(it) in dates } }
    }
}
