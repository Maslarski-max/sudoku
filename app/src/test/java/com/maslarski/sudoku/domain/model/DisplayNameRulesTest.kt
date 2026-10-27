package com.maslarski.sudoku.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayNameRulesTest {

    @Test
    fun `trims and collapses whitespace`() {
        assertEquals("Max 99", DisplayNameRules.normalize("  Max   99 "))
    }

    @Test
    fun `counts normalized Unicode code points`() {
        assertEquals(DisplayNameError.LENGTH, DisplayNameRules.validate("ab"))
        assertNull(DisplayNameRules.validate("abc"))
        assertNull(DisplayNameRules.validate("a".repeat(20)))
        assertEquals(DisplayNameError.LENGTH, DisplayNameRules.validate("a".repeat(21)))
        assertEquals(DisplayNameError.LENGTH, DisplayNameRules.validate("😀😀"))
        assertEquals("Željko", DisplayNameRules.normalize("Z\u030Celjko"))
    }

    @Test
    fun `accepts Unicode letters and rejects unsupported characters`() {
        assertNull(DisplayNameRules.validate("Željko"))
        assertEquals(DisplayNameError.CHARACTERS, DisplayNameRules.validate("Max🙂"))
        assertEquals(DisplayNameError.CHARACTERS, DisplayNameRules.validate("_max"))
    }

    @Test
    fun `reserves automatic Player names`() {
        assertEquals(DisplayNameError.RESERVED, DisplayNameRules.validate("Player AB12CD"))
        assertEquals(DisplayNameError.RESERVED, DisplayNameRules.validate("player x"))
        assertNull(DisplayNameRules.validate("Playerone"))
    }

    @Test
    fun `blocklist remains server authoritative`() {
        assertNull(DisplayNameRules.validate("Fuck"))
    }
}
