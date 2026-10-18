package com.lamuier.scheduletimeline.data

import java.time.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class NotificationSnapshotTest {
    @Test fun sharedSnapshotMatchesPublishedPlannerAndBoundaryResultsAcrossClockChanges() {
        val random = Random(1141)
        val zones = listOf(ZoneOffset.UTC, ZoneId.of("Asia/Shanghai"), ZoneId.of("America/New_York"))
        val instants = listOf("2026-03-08T06:59:59Z", "2026-11-01T05:59:59Z", "2026-10-15T15:59:59Z")
        repeat(40) { round ->
            for (zone in zones) for (instant in instants) {
                val now = Instant.parse(instant).toEpochMilli() - round * 60_000L
                val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                val events = List(30) { i -> ScheduleEvent(id = i + 1L, team = "Team$i",
                    dayKey = if (i == 0) "bad-date" else date.plusDays(random.nextLong(-4, 5)).toString(),
                    startMinutes = random.nextInt(0, 1440), endMinutes = random.nextInt(0, 1440),
                    completed = i % 5 == 0) }
                val keys = NotificationSchedule.windows(events, zone).flatMap {
                    NotificationSchedule.remindersFor(it, zone)
                }.map { it.key }
                val previous = ReminderState(keys.filterIndexed { i, _ -> i % 2 == 0 }.toSet(),
                    keys.filterIndexed { i, _ -> i % 5 == 0 }.toSet())
                val snapshot = NotificationSchedule.snapshot(events, now, zone)
                assertEquals(NotificationSchedule.activeAt(events, now, zone), snapshot.active)
                assertEquals(NotificationSchedule.nextWindowAfter(events, now, zone), snapshot.next)
                assertEquals(NotificationSchedule.nextBoundaryAfter(events, now, zone), snapshot.nextBoundary)
                assertEquals(ReferenceReminderPlanner.plan(events, now, zone, previous), ReminderPlanner.plan(snapshot, previous))
                for (notifications in listOf(false, true)) for (widgets in listOf(false, true)) {
                    val boundary = if (notifications) NotificationSchedule.nextRefreshAt(events, now, zone, 5000)
                        else NotificationSchedule.nextBoundaryAfter(events, now, zone)
                    val midnight = if (widgets) Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1)
                        .atStartOfDay(zone).toInstant().toEpochMilli() else null
                    val expected = if (!notifications && !widgets) null else listOfNotNull(boundary, midnight).minOrNull()
                    assertEquals(expected, BoundarySchedule.next(snapshot, notifications, widgets, 5000))
                }
            }
        }
    }
}

private object ReferenceReminderPlanner {
    fun plan(events: List<ScheduleEvent>, now: Long, zone: ZoneId, previous: ReminderState): ReminderDecision {
        val windows = NotificationSchedule.windows(events, zone)
        val knownKeys = windows.flatMap { NotificationSchedule.remindersFor(it, zone) }.map { it.key }.toSet()
        val futureWindows = windows.filter { it.startMillis > now }
        val candidates = futureWindows.flatMap { NotificationSchedule.remindersFor(it, zone) }
        val valid = candidates.map { it.key }.toSet()
        val pending = candidates.filter { it.key !in previous.handled &&
            (it.triggerMillis > now || it.key in previous.pending) }
        val due = pending.filter { it.triggerMillis <= now }
        val remaining = pending.filter { it.triggerMillis > now }
        val first = remaining.minOfOrNull { it.triggerMillis }
        return ReminderDecision(
            deliver = due.groupBy { it.event.id }.values.map { group ->
                group.maxWith(compareBy<ScheduledReminder> { it.triggerMillis }.thenBy { it.kind.ordinal })
            },
            state = ReminderState(remaining.map { it.key }.toSet(),
                (previous.handled + due.map { it.key }).intersect(knownKeys)),
            next = first?.let { time -> ReminderBatch(time, remaining.filter { it.triggerMillis == time }) },
            validKeys = valid,
        )
    }
}
