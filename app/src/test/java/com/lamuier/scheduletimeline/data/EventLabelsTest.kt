package com.lamuier.scheduletimeline.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EventLabelsTest {

    @Test
    fun typeChip_tokutenShowsKindWithoutPrefix() {
        assertEquals("前特", EventLabels.typeChip(tokuten(TokutenKind.PRE)))
        assertEquals("平特", EventLabels.typeChip(tokuten(TokutenKind.PARALLEL)))
        assertEquals("终特", EventLabels.typeChip(tokuten(TokutenKind.FINAL)))
        assertEquals("特典", EventLabels.typeChip(tokuten(kind = null)))
    }

    @Test
    fun typeChip_performanceStaysPerformance() {
        assertEquals(
            "演出",
            EventLabels.typeChip(
                ScheduleEvent(
                    team = "StarDiary",
                    startMinutes = 14 * 60,
                    endMinutes = 15 * 60,
                    eventType = EventType.PERFORMANCE.storage,
                ),
            ),
        )
    }

    @Test
    fun displayLabel_usesShortTokutenChip() {
        assertEquals("StarDiary前特", EventLabels.displayLabel(tokuten(TokutenKind.PRE)))
    }

    private fun tokuten(kind: TokutenKind?) = ScheduleEvent(
        team = "StarDiary",
        startMinutes = 17 * 60,
        endMinutes = 19 * 60,
        eventType = EventType.TOKUTEN.storage,
        tokutenKind = kind?.storage.orEmpty(),
    )
}
