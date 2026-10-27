package com.maslarski.sudoku.domain.model

import java.text.Normalizer

enum class DisplayNameError {
    LENGTH,
    CHARACTERS,
    RESERVED,
    BLOCKED,
    RATE_LIMIT,
    NETWORK,
}

object DisplayNameRules {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 20

    private val allowedCharacters = Regex("^[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N} _.\\-]*$")
    private val reservedName = Regex("^player\\b", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("(?U)\\s+")

    fun normalize(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFC)
            .trim()
            .replace(whitespace, " ")

    fun validate(raw: String): DisplayNameError? {
        val name = normalize(raw)
        val length = name.codePointCount(0, name.length)
        if (length !in MIN_LENGTH..MAX_LENGTH) return DisplayNameError.LENGTH
        if (!allowedCharacters.matches(name)) return DisplayNameError.CHARACTERS
        if (reservedName.containsMatchIn(name)) return DisplayNameError.RESERVED
        return null
    }
}

class DisplayNameException(val error: DisplayNameError) : Exception()
