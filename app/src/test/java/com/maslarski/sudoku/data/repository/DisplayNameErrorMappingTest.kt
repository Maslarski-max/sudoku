package com.maslarski.sudoku.data.repository

import com.maslarski.sudoku.domain.model.DisplayNameError
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayNameErrorMappingTest {

    @Test
    fun `maps invalid argument reasons and falls back to blocked`() {
        assertEquals(DisplayNameError.LENGTH, displayNameErrorFor("INVALID_ARGUMENT", mapOf("reason" to "LENGTH")))
        assertEquals(
            DisplayNameError.CHARACTERS,
            displayNameErrorFor("INVALID_ARGUMENT", mapOf("reason" to "CHARACTERS")),
        )
        assertEquals(DisplayNameError.RESERVED, displayNameErrorFor("INVALID_ARGUMENT", mapOf("reason" to "RESERVED")))
        assertEquals(DisplayNameError.BLOCKED, displayNameErrorFor("INVALID_ARGUMENT", mapOf("reason" to "BLOCKED")))
        assertEquals(DisplayNameError.BLOCKED, displayNameErrorFor("INVALID_ARGUMENT", mapOf("reason" to "unknown")))
        assertEquals(DisplayNameError.BLOCKED, displayNameErrorFor("INVALID_ARGUMENT", null))
    }

    @Test
    fun `maps resource exhaustion and other failures`() {
        assertEquals(DisplayNameError.RATE_LIMIT, displayNameErrorFor("RESOURCE_EXHAUSTED", null))
        assertEquals(DisplayNameError.NETWORK, displayNameErrorFor("UNAVAILABLE", null))
    }
}
