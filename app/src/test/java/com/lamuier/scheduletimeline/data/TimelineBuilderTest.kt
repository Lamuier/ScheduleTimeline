package com.lamuier.scheduletimeline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineBuilderTest {

    @Test
    fun build_insertsGapBetweenNonOverlappingEvents() {
        val events = listOf(
            event(1, "A", start = 600, end = 660),
            event(2, "B", start = 720, end = 780),
        )

        val items = TimelineBuilder.build(events)

        assertEquals(3, items.size)
        assertTrue(items[0] is TimelineItem.Event)
        val gap = items[1] as TimelineItem.Gap
        assertEquals(660, gap.startMinutes)
        assertEquals(720, gap.endMinutes)
        assertTrue(items[2] is TimelineItem.Event)
    }

    @Test
    fun build_marksOverlappingEvents() {
        val events = listOf(
            event(1, "A", start = 600, end = 700),
            event(
                2,
                "B",
                start = 650,
                end = 750,
                eventType = EventType.TOKUTEN,
            ),
        )

        val items = TimelineBuilder.build(events)
        val group = items.filterIsInstance<TimelineItem.OverlapGroup>().single()
        assertEquals(2, group.events.size)
        assertEquals(listOf(1L), group.performanceEvents.map { it.event.id })
        assertEquals(listOf(2L), group.tokutenEvents.map { it.event.id })
        val first = group.events.first { it.event.id == 1L }

        assertTrue(first.overlapNotes.any { it.contains("重叠") })
    }

    @Test
    fun build_keepsSameTypeEventsInTheirOwnLane() {
        val events = listOf(
            event(1, "演出一", start = 600, end = 720),
            event(2, "演出二", start = 630, end = 750),
            event(3, "特典", start = 615, end = 700, eventType = EventType.TOKUTEN),
        )

        val group = TimelineBuilder.build(events)
            .filterIsInstance<TimelineItem.OverlapGroup>()
            .single()

        assertEquals(listOf(1L, 2L), group.performanceEvents.map { it.event.id })
        assertEquals(listOf(3L), group.tokutenEvents.map { it.event.id })
    }

    @Test
    fun build_emptyInput_returnsEmpty() {
        assertTrue(TimelineBuilder.build(emptyList()).isEmpty())
    }

    @Test
    fun build_separatesManualNoteFromOverlapNotes() {
        val events = listOf(
            event(1, "A", start = 600, end = 700, note = "记得带会员卡"),
            event(2, "B", start = 720, end = 780),
        )

        val first = TimelineBuilder.build(events)
            .filterIsInstance<TimelineItem.Event>()
            .single { it.event.id == 1L }

        assertEquals("记得带会员卡", first.note)
        assertTrue(first.overlapNotes.isEmpty())
    }

    @Test
    fun build_overlapWarningNotSuppressedByNoteText() {
        val events = listOf(
            event(1, "A", start = 600, end = 700, note = "与B重叠，注意时间"),
            event(2, "B", start = 650, end = 750, eventType = EventType.TOKUTEN),
        )

        val first = TimelineBuilder.build(events)
            .filterIsInstance<TimelineItem.OverlapGroup>()
            .single()
            .events
            .single { it.event.id == 1L }

        assertEquals("与B重叠，注意时间", first.note)
        assertTrue(first.overlapNotes.any { it.contains("重叠") })
    }

    private fun event(
        id: Long,
        title: String,
        start: Int,
        end: Int,
        eventType: EventType = EventType.PERFORMANCE,
        note: String = "",
    ) = ScheduleEvent(
        id = id,
        team = title,
        title = "",
        startMinutes = start,
        endMinutes = end,
        eventType = eventType.storage,
        dayKey = "2026-07-12",
        note = note,
    )
}
