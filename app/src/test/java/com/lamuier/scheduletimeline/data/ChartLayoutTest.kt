package com.lamuier.scheduletimeline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartLayoutTest {

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
