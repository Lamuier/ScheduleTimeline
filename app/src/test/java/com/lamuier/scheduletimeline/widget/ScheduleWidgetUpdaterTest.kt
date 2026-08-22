package com.lamuier.scheduletimeline.widget

import com.lamuier.scheduletimeline.data.EventType
import com.lamuier.scheduletimeline.data.ScheduleEvent
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleWidgetUpdaterTest {

    @Test
    fun collectionFingerprint_changesWhenEventRemoved() {
        val a = event(1, "StarDiary")
        val b = event(2, "空色轨迹")
        val before = ScheduleWidgetUpdater.collectionFingerprint(listOf(a, b))
        val after = ScheduleWidgetUpdater.collectionFingerprint(listOf(a))
        assertNotEquals(before, after)
    }

    @Test
    fun collectionFingerprint_stableForSameEvents() {
        val events = listOf(event(1, "StarDiary"), event(2, "空色轨迹"))
        assertEquals(
            ScheduleWidgetUpdater.collectionFingerprint(events),
            ScheduleWidgetUpdater.collectionFingerprint(events),
        )
    }

    private fun event(id: Long, team: String) = ScheduleEvent(
        id = id,
        team = team,
        startMinutes = 14 * 60,
        endMinutes = 15 * 60,
        eventType = EventType.PERFORMANCE.storage,
    )
}
