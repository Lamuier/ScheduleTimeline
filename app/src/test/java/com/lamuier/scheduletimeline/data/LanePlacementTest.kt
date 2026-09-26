package com.lamuier.scheduletimeline.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LanePlacementTest {

    @Test
    fun place_keepsNonOverlappingEventsInOneColumn() {
        val slots = LanePlacement.place(
            listOf(
                12 * 60 + 45 to 13 * 60 + 10,
                14 * 60 to 14 * 60 + 25,
                14 * 60 + 50 to 15 * 60 + 40,
            ),
        )

        assertEquals(listOf(0, 0, 0), slots.map { it.column })
        assertEquals(listOf(1, 1, 1), slots.map { it.columnCount })
        assertEquals(listOf(1, 1, 1), slots.map { it.span })
    }

    @Test
    fun place_putsOverlappingTokutenSideBySide() {
        val slots = LanePlacement.place(
            listOf(
                13 * 60 to 14 * 60 + 30,
                13 * 60 + 30 to 15 * 60,
            ),
        )

        assertEquals(0, slots[0].column)
        assertEquals(1, slots[1].column)
        assertEquals(2, slots[0].columnCount)
        assertEquals(1, slots[0].span)
        assertEquals(1, slots[1].span)
    }

    @Test
    fun place_treatsTouchingEndpointsAsTheSameColumn() {
        val slots = LanePlacement.place(
            listOf(
                13 * 60 to 14 * 60,
                14 * 60 to 15 * 60,
            ),
        )

        assertEquals(listOf(0, 0), slots.map { it.column })
        assertEquals(1, slots[0].columnCount)
    }

    @Test
    fun place_letsLaterEventSpanColumnsThatAreFree() {
        val slots = LanePlacement.place(
            listOf(
                13 * 60 to 14 * 60,
                13 * 60 + 30 to 14 * 60,
                14 * 60 to 15 * 60,
            ),
        )

        assertEquals(0, slots[0].column)
        assertEquals(1, slots[1].column)
        assertEquals(0, slots[2].column)
        assertEquals(2, slots[2].span)
        assertEquals(2, slots[2].columnCount)
    }

    @Test
    fun place_emptyInput_returnsEmpty() {
        assertEquals(emptyList<LanePlacement.Slot>(), LanePlacement.place(emptyList()))
    }
}
