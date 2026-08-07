package com.lamuier.scheduletimeline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartLayoutTest {

    @Test
    fun position_assignsSeparateColumnsForOverlappingEvents() {
        val events = listOf(
            TimelineItem.Event(event(1, start = 600, end = 700)),
            TimelineItem.Event(event(2, start = 650, end = 750)),
        )

        val positioned = ChartLayout.position(events)

        assertEquals(2, positioned.size)
        assertEquals(2, positioned.first().columns)
        assertTrue(positioned.map { it.column }.toSet().size == 2)
    }

    @Test
    fun freeSlots_returnsGapBetweenBusyIntervals() {
        val events = listOf(
            event(1, start = 600, end = 660),
            event(2, start = 720, end = 780),
        )

        val slots = ChartLayout.freeSlots(events)

        assertEquals(1, slots.size)
        assertEquals(660, slots[0].startMinutes)
        assertEquals(720, slots[0].endMinutes)
    }

    @Test
    fun stats_countsBusyFreeAndConflicts() {
        val events = listOf(
            event(1, start = 600, end = 700),
            event(2, start = 650, end = 750),
            event(3, start = 800, end = 860),
        )

        val stats = ChartLayout.stats(events)!!

        assertEquals(3, stats.eventCount)
        assertEquals(1, stats.conflictCount)
        assertEquals(600, stats.firstStart)
        assertEquals(860, stats.lastEnd)
        assertEquals(210, stats.busyMinutes) // 600-750 merged + 800-860
        assertEquals(50, stats.freeMinutes) // 750-800
    }

    @Test
    fun stats_empty_returnsNull() {
        assertNull(ChartLayout.stats(emptyList()))
    }

    private fun event(
        id: Long,
        start: Int,
        end: Int,
    ) = ScheduleEvent(
        id = id,
        title = "E$id",
        startMinutes = start,
        endMinutes = end,
        dayKey = "2026-07-12",
    )
}
