package com.lamuier.scheduletimeline.notification

import com.lamuier.scheduletimeline.data.EventType
import com.lamuier.scheduletimeline.data.ReminderKind
import com.lamuier.scheduletimeline.data.ScheduleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCopyTest {

    @Test
    fun liveTitle_isCompact() {
        val single = listOf(event(1, "空色轨迹"))
        assertEquals("演·空色轨迹", NotificationCopy.liveTitle(upcoming = false, single))
        assertEquals("下一项 演·空色轨迹", NotificationCopy.liveTitle(upcoming = true, single))
        assertEquals(
            "2项进行",
            NotificationCopy.liveTitle(
                upcoming = false,
                events = listOf(event(1, "A"), event(2, "B")),
            ),
        )
    }

    @Test
    fun liveText_keepsOnlyWhen() {
        assertEquals("14:00", NotificationCopy.liveText(true, "14:00", null))
        assertEquals("→15:00", NotificationCopy.liveText(false, "15:00", null))
        assertEquals("→15:00 · 下16:00", NotificationCopy.liveText(false, "15:00", "16:00"))
        assertEquals("→15:00 · 下周六 16:00", NotificationCopy.liveText(false, "15:00", "周六 16:00"))
    }

    @Test
    fun upcomingWhen_usesWeekdayOnlyAcrossDays() {
        assertEquals("14:00", NotificationCopy.upcomingWhen(true, "周六", "14:00"))
        assertEquals("周六 14:00", NotificationCopy.upcomingWhen(false, "周六", "14:00"))
    }

    @Test
    fun islandTitle_truncatesLongTeamNames() {
        assertEquals("演·空色轨迹", NotificationCopy.islandTitle(listOf(event(1, "空色轨迹"))))
        assertEquals(
            "演·超长团队名超",
            NotificationCopy.islandTitle(listOf(event(1, "超长团队名超长"))),
        )
        assertEquals("2项", NotificationCopy.islandTitle(listOf(event(1, "A"), event(2, "B"))))
        assertTrue(NotificationCopy.islandTitle(listOf(event(1, "超长团队名超长"))).length <= 8)
    }

    @Test
    fun reminderText_dropsFullDate() {
        assertEquals("3天后 14:00", NotificationCopy.reminderText(ReminderKind.THREE_DAYS_BEFORE, "14:00"))
        assertEquals("今天 14:00", NotificationCopy.reminderText(ReminderKind.DAY_OF_MIDNIGHT, "14:00"))
        assertEquals("1小时后 14:00", NotificationCopy.reminderText(ReminderKind.ONE_HOUR_BEFORE, "14:00"))
    }

    private fun event(id: Long, team: String) = ScheduleEvent(
        id = id,
        team = team,
        startMinutes = 14 * 60,
        endMinutes = 15 * 60,
        eventType = EventType.PERFORMANCE.storage,
    )
}
