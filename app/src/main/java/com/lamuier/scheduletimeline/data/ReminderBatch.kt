package com.lamuier.scheduletimeline.data

import java.time.Instant
import java.time.ZoneId

val ScheduledReminder.key: String get() =
    "${event.id}|${event.dayKey}|${event.startMinutes}|${event.endMinutes}|${kind.name}"
data class ReminderBatch(val triggerMillis: Long, val reminders: List<ScheduledReminder>)
data class ReminderState(val pending: Set<String> = emptySet(), val handled: Set<String> = emptySet())
data class ReminderDecision(val deliver: List<ScheduledReminder>, val state: ReminderState,
    val next: ReminderBatch?, val validKeys: Set<String>)

/** Register future reminders, retain delayed work, and coalesce overdue reminders per event. */
object ReminderPlanner {
    fun plan(events: List<ScheduleEvent>, now: Long, zone: ZoneId, previous: ReminderState): ReminderDecision {
        return plan(NotificationSchedule.snapshot(events, now, zone), previous)
    }

    fun plan(snapshot: NotificationSnapshot, previous: ReminderState): ReminderDecision {
        val now = snapshot.nowMillis
        val reminders = snapshot.windows.flatMap { window ->
            NotificationSchedule.remindersFor(window, snapshot.zone).map { window to it }
        }
        val knownKeys = reminders.map { it.second.key }.toSet()
        val candidates = reminders.filter { it.first.startMillis > now }.map { it.second }
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

object BoundarySchedule {
    fun next(events: List<ScheduleEvent>, now: Long, zone: ZoneId,
        notifications: Boolean, widgets: Boolean, intervalMs: Long = 60_000L): Long? {
        return next(NotificationSchedule.snapshot(events, now, zone), notifications, widgets, intervalMs)
    }

    fun next(snapshot: NotificationSnapshot, notifications: Boolean, widgets: Boolean,
        intervalMs: Long = 60_000L): Long? {
        if (!notifications && !widgets) return null
        val eventBoundary = if (notifications) snapshot.nextRefreshAt(intervalMs) else snapshot.nextBoundary
        val midnight = if (widgets) Instant.ofEpochMilli(snapshot.nowMillis).atZone(snapshot.zone).toLocalDate()
            .plusDays(1).atStartOfDay(snapshot.zone).toInstant().toEpochMilli() else null
        return listOfNotNull(eventBoundary, midnight).minOrNull()
    }
}
