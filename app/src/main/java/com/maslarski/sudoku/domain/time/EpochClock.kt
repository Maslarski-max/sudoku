package com.maslarski.sudoku.domain.time

fun interface EpochClock {
    fun now(): Long
}
