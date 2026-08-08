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

object NotificationSchedule {
    fun windows(
        events: List<ScheduleEvent>,
        zone: ZoneId,
    ): List<ScheduledEventWindow> = events.mapNotNull { event ->
        val date = runCatching { LocalDate.parse(event.dayKey) }.getOrNull() ?: return@mapNotNull null
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli() + event.startMinutes * 60_000L
        val end = date.atStartOfDay(zone).toInstant().toEpochMilli() + event.endMinutes * 60_000L
        if (end <= start) null else ScheduledEventWindow(event, start, end)
    }

    fun activeAt(
        events: List<ScheduleEvent>,
        nowMillis: Long,
        zone: ZoneId,
    ): List<ScheduledEventWindow> = windows(events, zone)
        .filter { nowMillis in it.startMillis until it.endMillis }
        .sortedBy { it.startMillis }

    fun nextBoundaryAfter(
        events: List<ScheduleEvent>,
        nowMillis: Long,
        zone: ZoneId,
    ): Long? = windows(events, zone)
        .flatMap { listOf(it.startMillis, it.endMillis) }
        .filter { it > nowMillis + 1_000L }
        .minOrNull()

    fun nextWindowAfter(
        events: List<ScheduleEvent>,
        nowMillis: Long,
        zone: ZoneId,
    ): ScheduledEventWindow? = windows(events, zone)
        .filter { it.startMillis > nowMillis }
        .minByOrNull { it.startMillis }

    /**
     * 下一次需要刷新常驻通知的时刻。
     *
     * - 有进行中事件时，取「下一个边界」与「now + intervalMs」的较早者，
     *   让进行中通知的进度条按 [intervalMs] 周期性走动（Doze 下由系统节流，
     *   但此时屏幕熄灭用户不可见，亮屏退出 Doze 后恢复正常周期）。
     * - 空档 / upcoming 状态不周期性刷新，避免抢占 Doze 维护窗口，
     *   保证下一项 startMillis 边界能可靠触发 upcoming → active 切换。
     */
    fun nextRefreshAt(
        events: List<ScheduleEvent>,
        nowMillis: Long,
        zone: ZoneId,
        intervalMs: Long,
    ): Long? {
        val boundary = nextBoundaryAfter(events, nowMillis, zone)
        val hasActive = activeAt(events, nowMillis, zone).isNotEmpty()
        return if (hasActive) {
            listOfNotNull(boundary, nowMillis + intervalMs).minOrNull()
        } else {
            boundary
        }
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

    /**
     * 全量事件中最早触发的未来提醒；已错过的触发点静默跳过
     * （例如临开场 30 分钟才录入的事件，不再补发 3 天前/0 点提醒）。
     */
    fun nextReminderAfter(
        events: List<ScheduleEvent>,
        nowMillis: Long,
        zone: ZoneId,
    ): ScheduledReminder? = windows(events, zone)
        .flatMap { remindersFor(it, zone) }
        .filter { it.triggerMillis > nowMillis + 1_000L }
        .minByOrNull { it.triggerMillis }
}
