package com.lamuier.scheduletimeline.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderBatchTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun time(value: String, z: ZoneId = zone) = LocalDateTime.parse(value).atZone(z).toInstant().toEpochMilli()
    private fun event(id: Long, start: Int = 600) = ScheduleEvent(id = id, team = "T$id", dayKey = "2026-10-20",
        startMinutes = start, endMinutes = start + 30)

    @Test fun midnightBatchIncludesEveryEventAndIsProcessedOnce() {
        val events = listOf(event(1), event(2), event(3, 60))
        val before = ReminderPlanner.plan(events, time("2026-10-19T23:59:00"), zone, ReminderState())
        assertEquals(4, before.next!!.reminders.size)
        val due = ReminderPlanner.plan(events, time("2026-10-20T00:00:00"), zone, before.state)
        assertEquals(setOf(1L, 2L, 3L), due.deliver.map { it.event.id }.toSet())
        assertTrue(ReminderPlanner.plan(events, time("2026-10-20T00:00:01"), zone, due.state).deliver.isEmpty())
    }

    @Test fun delayedRegisteredRemindersCoalesceOnlyBeforeStart() {
        val events = listOf(event(1))
        val registered = ReminderPlanner.plan(events, time("2026-10-16T00:00:00"), zone, ReminderState())
        assertEquals(1, ReminderPlanner.plan(events, time("2026-10-20T09:30:00"), zone, registered.state).deliver.size)
        assertTrue(ReminderPlanner.plan(events, time("2026-10-20T10:00:00"), zone, registered.state).deliver.isEmpty())
        assertTrue(ReminderPlanner.plan(events, time("2026-10-20T09:30:00"), zone, ReminderState()).deliver.isEmpty())
    }

    @Test fun rescheduledDeletedCompletedAndInvalidEventsClearOldReminders() {
        val original = event(1)
        val registered = ReminderPlanner.plan(listOf(original), time("2026-10-19T23:59:00"), zone, ReminderState())
        for (events in listOf(emptyList(), listOf(original.copy(completed = true)),
            listOf(original.copy(startMinutes = 660, endMinutes = 690)), listOf(original.copy(endMinutes = 0)))) {
            val decision = ReminderPlanner.plan(events, time("2026-10-20T00:01:00"), zone, registered.state)
            assertTrue(decision.deliver.isEmpty())
            assertTrue(decision.validKeys.intersect(registered.state.pending).isEmpty())
        }
    }

    @Test fun dstUsesLocalWallClockAndRecomputesAfterZoneChange() {
        val ny = ZoneId.of("America/New_York")
        for (day in listOf("2026-03-08", "2026-11-01")) {
            val e = event(1).copy(dayKey = day)
            val window = NotificationSchedule.windows(listOf(e), ny).single()
            assertEquals(time("${day}T10:00:00", ny), window.startMillis)
            assertEquals(10, Instant.ofEpochMilli(window.startMillis).atZone(ny).hour)
        }
        val e = event(1)
        val now = time("2026-10-19T10:00:00")
        val first = ReminderPlanner.plan(listOf(e), now, zone, ReminderState())
        val changed = ReminderPlanner.plan(listOf(e), now, ny, first.state)
        assertNotEquals(first.next!!.triggerMillis, changed.next!!.triggerMillis)
        assertEquals(first.state.pending, changed.state.pending)
    }

    @Test fun backwardsClockChangeDoesNotRepeatHandledReminders() {
        val events = listOf(event(1))
        val registered = ReminderPlanner.plan(events, time("2026-10-19T23:59:00"), zone, ReminderState())
        val handled = ReminderPlanner.plan(events, time("2026-10-20T00:00:00"), zone, registered.state)
        val afterStart = ReminderPlanner.plan(events, time("2026-10-20T10:01:00"), zone, handled.state)
        val backwards = ReminderPlanner.plan(events, time("2026-10-20T00:01:00"), zone, afterStart.state)
        assertTrue(backwards.deliver.isEmpty())
    }

    @Test fun widgetsRefreshWithoutNotificationsAndStopWhenNeitherNeedsWork() {
        val now = time("2026-10-20T09:00:00")
        assertEquals(time("2026-10-20T10:00:00"), BoundarySchedule.next(listOf(event(1)), now, zone, false, true))
        assertEquals(time("2026-10-21T00:00:00"), BoundarySchedule.next(emptyList(), now, zone, false, true))
        assertNull(BoundarySchedule.next(listOf(event(1)), now, zone, false, false))
    }
}
