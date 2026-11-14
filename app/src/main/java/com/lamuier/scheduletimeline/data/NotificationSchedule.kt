package com.lamuier.scheduletimeline.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ScheduledEventWindow(
    val event: ScheduleEvent,
    val startMillis: Long,
    val endMillis: Long,
)

/** 关键时间点提醒类型：开始前 3 天（与开始同一时刻）、当天 0 点、开始前 1 小时。 */
enum class ReminderKind { THREE_DAYS_BEFORE, DAY_OF_MIDNIGHT, ONE_HOUR_BEFORE }

data class ScheduledReminder(
    val event: ScheduleEvent,
    val kind: ReminderKind,
    val triggerMillis: Long,
)

/** One wall-clock/zone conversion per refresh, shared by reminders, progress and widgets. */
data class NotificationSnapshot(
    val windows: List<ScheduledEventWindow>,
    val nowMillis: Long,
    val zone: ZoneId,
) {
    val active = windows.filter { nowMillis in it.startMillis until it.endMillis }.sortedBy { it.startMillis }
    val next = windows.filter { it.startMillis > nowMillis }.minByOrNull { it.startMillis }
    val nextBoundary = windows.asSequence().flatMap { sequenceOf(it.startMillis, it.endMillis) }
        .filter { it > nowMillis }.minOrNull()

    fun nextRefreshAt(intervalMs: Long): Long? = if (active.isNotEmpty())
        minOf(nextBoundary ?: Long.MAX_VALUE, nowMillis + intervalMs) else nextBoundary
}

object NotificationSchedule {
    fun snapshot(events: List<ScheduleEvent>, nowMillis: Long, zone: ZoneId) =
        NotificationSnapshot(windows(events, zone), nowMillis, zone)
    fun windows(
        events: List<ScheduleEvent>,
        zone: ZoneId,
    ): List<ScheduledEventWindow> = events.mapNotNull { event ->
        // 已完成的特典不再进入常驻通知、进度刷新与关键时间点提醒。
        if (event.completed || !event.hasValidTime) return@mapNotNull null
        val date = runCatching { LocalDate.parse(event.dayKey) }.getOrNull() ?: return@mapNotNull null
        val start = date.atTime(event.startMinutes / 60, event.startMinutes % 60).atZone(zone).toInstant().toEpochMilli()
        val end = date.atTime(event.endMinutes / 60, event.endMinutes % 60).atZone(zone).toInstant().toEpochMilli()
        if (end <= start) null else ScheduledEventWindow(event, start, end)
    }

    /**
     * 单个事件窗口的关键时间点提醒。用 ZonedDateTime 做偏移而非毫秒加减，
     * 保证「3 天前同一时刻」「当天 0 点」按本地墙钟时间对齐。
     */
    fun remindersFor(
        window: ScheduledEventWindow,
        zone: ZoneId,
    ): List<ScheduledReminder> {
        val start = Instant.ofEpochMilli(window.startMillis).atZone(zone)
        val midnight = start.toLocalDate().atStartOfDay(zone)
        return listOf(
            ScheduledReminder(
                window.event,
                ReminderKind.THREE_DAYS_BEFORE,
                start.minusDays(3).toInstant().toEpochMilli(),
            ),
            ScheduledReminder(
                window.event,
                ReminderKind.DAY_OF_MIDNIGHT,
                midnight.toInstant().toEpochMilli(),
            ),
            ScheduledReminder(
                window.event,
                ReminderKind.ONE_HOUR_BEFORE,
                start.minusHours(1).toInstant().toEpochMilli(),
            ),
        )
    }
}
