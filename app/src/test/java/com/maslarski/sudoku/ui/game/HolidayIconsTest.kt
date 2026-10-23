package com.maslarski.sudoku.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test

class HolidayIconsTest {
    @Test
    fun `there are 12 distinct holiday icons with matching names`() {
        assertEquals(12, HolidayIcons.size)
        assertEquals(12, HolidayIcons.map { it.name }.distinct().size)
        assertEquals(12, holidayIconNames.size)
    }
}
