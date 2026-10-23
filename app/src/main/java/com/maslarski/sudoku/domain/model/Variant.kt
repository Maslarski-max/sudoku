package com.maslarski.sudoku.domain.model

enum class Variant(val slotId: Int) {
    SAMURAI(101),
    FLOWER(102),
    OUTSIDE(103);

    val displayName: String
        get() = when (this) {
            SAMURAI -> "Samurai"
            FLOWER -> "Flower"
            OUTSIDE -> "Outside"
        }

    companion object {
        fun fromSlotId(slotId: Int): Variant? = entries.firstOrNull { it.slotId == slotId }
    }
}
