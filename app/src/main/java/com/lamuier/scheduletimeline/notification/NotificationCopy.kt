package com.lamuier.scheduletimeline.notification

import com.lamuier.scheduletimeline.data.EventLabels
import com.lamuier.scheduletimeline.data.ReminderKind
import com.lamuier.scheduletimeline.data.ScheduleEvent

/**
 * 常驻通知 / Live Update / 超级岛 / 提醒的短文案。
 *
 * 标题与正文拆开：标题只放「谁」，正文只放「何时」，避免重复堆叠导致
 * Status Chip、超级岛胶囊和通知栏一行显示不全。
 */
object NotificationCopy {
    /** Status Chip / 小岛左侧短文案上限（码点）。 */
    const val CHIP_MAX_CHARS = 8

    fun liveTitle(upcoming: Boolean, events: List<ScheduleEvent>): String {
        val first = events.firstOrNull()?.let(EventLabels::notificationLabel).orEmpty()
        return when {
            upcoming -> "下一项 $first"
            events.size == 1 -> first
            else -> "${events.size}项进行"
        }
    }

    fun liveText(upcoming: Boolean, whenText: String, nextWhen: String?): String =
        if (upcoming) {
            whenText
        } else if (nextWhen.isNullOrBlank()) {
            "→$whenText"
        } else {
            "→$whenText · 下$nextWhen"
        }

    fun upcomingWhen(isToday: Boolean, weekday: String, timeHm: String): String =
        if (isToday) timeHm else "$weekday $timeHm"

    fun islandTitle(events: List<ScheduleEvent>): String {
        if (events.size != 1) return "${events.size}项"
        return EventLabels.notificationLabel(events.first()).take(CHIP_MAX_CHARS)
    }

    fun reminderText(kind: ReminderKind, startHm: String): String = when (kind) {
        ReminderKind.THREE_DAYS_BEFORE -> "3天后 $startHm"
        ReminderKind.DAY_OF_MIDNIGHT -> "今天 $startHm"
        ReminderKind.ONE_HOUR_BEFORE -> "1小时后 $startHm"
    }
}
