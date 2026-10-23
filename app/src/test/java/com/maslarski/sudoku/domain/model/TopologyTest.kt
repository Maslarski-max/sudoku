package com.maslarski.sudoku.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopologyTest {
    @Test
    fun `classic topology preserves rows columns and boxes`() {
        val topology = Topology.classic(GridSize.NINE)
        assertEquals(27, topology.units.size)
        assertEquals(27, topology.units.count { it.size == 9 })
        assertEquals(20, topology.peers(0).size)
        assertTrue(topology.units.any { it.contentEquals(intArrayOf(0, 1, 2, 9, 10, 11, 18, 19, 20)) })
    }

    @Test
    fun `samurai and flower topologies have the specified active cells`() {
        val samurai = Topology.forVariant(Variant.SAMURAI, GridSize.NINE)
        val flower = Topology.forVariant(Variant.FLOWER, GridSize.NINE)

        assertEquals(21, samurai.width)
        assertEquals(21, samurai.height)
        assertEquals(369, samurai.active.count { it })
        assertTrue(samurai.units.all { it.size == 9 })
        assertEquals(15, flower.width)
        assertEquals(15, flower.height)
        assertEquals(189, flower.active.count { it })
        assertTrue(flower.units.all { it.size == 9 })
    }

    @Test
    fun `overlapping boxes are represented once`() {
        val flower = Topology.forVariant(Variant.FLOWER, GridSize.NINE)
        val distinct = flower.units.filterIndexed { index, unit ->
            flower.unitKinds[index] == UnitKind.BOX
        }.map { it.sorted().joinToString(",") }
        assertEquals(distinct.size, distinct.toSet().size)
    }
}
