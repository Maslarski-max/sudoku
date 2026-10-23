package com.maslarski.sudoku.data.time

import com.maslarski.sudoku.domain.time.EpochClock

object SystemEpochClock : EpochClock {
    override fun now(): Long = System.currentTimeMillis()
}
