package com.lamuier.scheduletimeline.notification

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.lamuier.scheduletimeline.MainActivity
import com.lamuier.scheduletimeline.R
import com.lamuier.scheduletimeline.data.EventLabels
import com.lamuier.scheduletimeline.data.EventType
import com.lamuier.scheduletimeline.data.NotificationPreferences
import com.lamuier.scheduletimeline.data.NotificationSchedule
import com.lamuier.scheduletimeline.data.ReminderKind
import com.lamuier.scheduletimeline.data.ScheduleEvent
import com.lamuier.scheduletimeline.data.ScheduleRepository
import com.lamuier.scheduletimeline.data.ScheduledEventWindow
import com.lamuier.scheduletimeline.data.TimeFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

class ScheduleNotificationCoordinator(
    context: Context,
    private val repository: ScheduleRepository,
    private val preferences: NotificationPreferences,
) {
    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val zone = ZoneId.systemDefault()
    private val dateFormatter = DateTimeFormatter.ofPattern(
        appContext.getString(R.string.date_pattern),
    )

    suspend fun refresh() {
        ensureChannel()
        ensureReminderChannel()
        if (!preferences.enabled.value || !canPostNotifications()) {
            notificationManager.cancel(LIVE_NOTIFICATION_ID)
            cancelBoundaryAlarm()
            cancelReminderAlarm()
            return
        }

        val events = repository.allEvents()
        val now = System.currentTimeMillis()
        val active = NotificationSchedule.activeAt(events, now, zone)
        if (active.isEmpty()) {
            val upcoming = if (preferences.alwaysOn.value) {
                NotificationSchedule.nextWindowAfter(events, now, zone)
            } else {
                null
            }
            if (upcoming == null) {
                notificationManager.cancel(LIVE_NOTIFICATION_ID)
            } else {
                postLiveUpdate(
                    events = listOf(upcoming.event),
                    startMillis = upcoming.startMillis,
                    endMillis = upcoming.endMillis,
                    nowMillis = now,
                    upcoming = true,
                )
            }
        } else {
            postLiveUpdate(
                events = active.map { it.event },
                startMillis = active.minOf { it.startMillis },
                endMillis = active.maxOf { it.endMillis },
                nowMillis = now,
                upcoming = false,
                // 进行中通知的详情始终预告下一项，与「显示未来日程」开关无关——
                // 开关只决定空档期是否单独发通知。
                next = NotificationSchedule.nextWindowAfter(events, now, zone),
            )
        }
        scheduleNextBoundary(events, now, progressRefreshIntervalMs(events, now, zone))
        scheduleNextReminder(events, now)
    }

    /**
     * 关键时间点提醒闹钟触发：按 eventId 取最新数据发普通提醒通知
     * （非常驻、5 分钟自动消失），随后 refresh 调度下一个提醒。
     * 事件已被删除/改期时跳过发通知，仅推进调度。
     */
    suspend fun handleReminder(intent: Intent?) {
        val eventId = intent?.getLongExtra(EXTRA_EVENT_ID, -1L) ?: -1L
        val kindOrdinal = intent?.getIntExtra(EXTRA_REMINDER_KIND, -1) ?: -1
        val kind = ReminderKind.entries.getOrNull(kindOrdinal)

        if (eventId > 0 && kind != null &&
            preferences.enabled.value && canPostNotifications()
        ) {
            val event = repository.allEvents().firstOrNull { it.id == eventId }
            if (event != null) {
                postReminder(event, kind)
            }
        }
        refresh()
    }

    private fun postReminder(event: ScheduleEvent, kind: ReminderKind) {
        val window = NotificationSchedule.windows(listOf(event), zone).firstOrNull() ?: return
        val start = Instant.ofEpochMilli(window.startMillis).atZone(zone)
        val startTime = TimeFormat.minutesToHm(start.hour * 60 + start.minute)
        val text = when (kind) {
            ReminderKind.THREE_DAYS_BEFORE -> appContext.getString(
                R.string.notification_reminder_text_three_days,
                start.toLocalDate().format(dateFormatter),
                startTime,
            )
            ReminderKind.DAY_OF_MIDNIGHT -> appContext.getString(
                R.string.notification_reminder_text_midnight,
                startTime,
            )
            ReminderKind.ONE_HOUR_BEFORE -> appContext.getString(
                R.string.notification_reminder_text_one_hour,
                startTime,
            )
        }

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            appContext,
            REMINDER_CONTENT_REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // 普通提醒：非常驻、不请求 Live Updates 提升、不上岛，5 分钟后自动消失。
        val notification = Notification.Builder(appContext, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(EventLabels.displayLabel(event))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setTimeoutAfter(REMINDER_AUTO_DISMISS_MILLIS)
            .build()
        val notificationId = REMINDER_NOTIFICATION_ID_BASE +
            (event.id % 1_000).toInt() * ReminderKind.entries.size + kind.ordinal
        runCatching { notificationManager.notify(notificationId, notification) }
    }

    /**
     * Colored launcher icon for the island capsule's left area. The status-bar
     * small icon is a black monochrome silhouette that renders invisible on the
     * dark capsule, so notifications also carry this colored large icon.
     */
    private fun islandLargeIcon(): Icon =
        Icon.createWithResource(appContext, R.drawable.ic_island)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // Channel importance is locked by the system once a channel is created, so the
        // legacy DEFAULT channel can never be upgraded in place. Drop it and recreate
        // under a new id: Xiaomi focus notifications / HyperIsland only attach to
        // IMPORTANCE_HIGH channels (both reference projects do this).
        notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = appContext.getString(R.string.notification_channel_description)
                setShowBadge(true)
            },
        )
    }

    private fun ensureReminderChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL_ID,
                appContext.getString(R.string.notification_reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = appContext.getString(
                    R.string.notification_reminder_channel_description,
                )
                setShowBadge(true)
            },
        )
    }

    private fun canPostNotifications(): Boolean {
        return hasNotificationPermission() && notificationManager.areNotificationsEnabled()
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun postLiveUpdate(
        events: List<ScheduleEvent>,
        startMillis: Long,
        endMillis: Long,
        nowMillis: Long,
        upcoming: Boolean,
        next: ScheduledEventWindow? = null,
    ) {
        val labels = events.joinToString("、") { EventLabels.displayLabel(it) }
        val title = if (upcoming) {
            appContext.getString(
                R.string.notification_live_title_upcoming,
                EventLabels.displayLabel(events.first()),
            )
        } else if (events.size == 1) {
            appContext.getString(
                R.string.notification_live_title_single,
                EventLabels.displayLabel(events.first()),
            )
        } else {
            appContext.getString(R.string.notification_live_title_multiple, events.size)
        }
        val text = if (upcoming) {
            appContext.getString(
                R.string.notification_live_upcoming_content,
                labels,
                Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate().format(dateFormatter),
                Instant.ofEpochMilli(startMillis).atZone(zone).toLocalTime().let {
                    TimeFormat.minutesToHm(it.hour * 60 + it.minute)
                },
            )
        } else {
            val base = appContext.getString(
                R.string.notification_live_content,
                labels,
                Instant.ofEpochMilli(endMillis).atZone(zone).toLocalTime().let {
                    TimeFormat.minutesToHm(it.hour * 60 + it.minute)
                },
            )
            if (next == null) {
                base
            } else {
                val nextStart = Instant.ofEpochMilli(next.startMillis).atZone(zone)
                val nextTime = TimeFormat.minutesToHm(
                    nextStart.hour * 60 + nextStart.minute,
                )
                // 下一项不在今天时仅显示几点几分会误导，补星期前缀
                val nextWhen = if (nextStart.toLocalDate() ==
                    Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
                ) {
                    nextTime
                } else {
                    val weekday = nextStart.dayOfWeek
                        .getDisplayName(TextStyle.SHORT, Locale.SIMPLIFIED_CHINESE)
                    appContext.getString(R.string.notification_chip_later, weekday, nextTime)
                }
                base + appContext.getString(
                    R.string.notification_live_next_suffix,
                    EventLabels.displayLabel(next.event),
                    nextWhen,
                )
            }
        }
        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            appContext,
            CONTENT_REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(islandLargeIcon())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(startMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(upcoming)
            .setTimeoutAfter(
                // upcoming：到开始时刻超时（由边界闹钟 refresh 切换为进行中，
                // 避免倒计时走过 0 后长期挂负数）；active：到结束时刻超时作为保底。
                max(1L, if (upcoming) startMillis - nowMillis else endMillis - nowMillis),
            )

        val durationMs = max(1, (endMillis - startMillis).toInt())
        val progressMs = (nowMillis - startMillis).toInt().coerceIn(0, durationMs)
        builder.setProgress(durationMs, progressMs, false)
        applyAndroid16LiveUpdate(
            builder = builder,
            startMillis = startMillis,
            endMillis = endMillis,
            nowMillis = nowMillis,
            upcoming = upcoming,
            upcomingChipText = upcomingChipText(events.first(), startMillis, nowMillis, upcoming),
        )

        val notification = builder.build()
        XiaomiHyperIslandAdapter.applyIfSupported(
            context = appContext,
            notification = notification,
            title = title,
            content = text,
            events = events,
            startMillis = startMillis,
            upcoming = upcoming,
        )
        runCatching { notificationManager.notify(LIVE_NOTIFICATION_ID, notification) }
    }

    /**
     * Compact capsule/chip label for a far-future upcoming event. Within the
     * countdown window the system renders a live countdown from `when`, so no
     * text is needed; beyond it the island falls back to the full notification
     * title, which overflows the capsule (observed on HyperOS 3).
     */
    private fun upcomingChipText(
        event: ScheduleEvent,
        startMillis: Long,
        nowMillis: Long,
        upcoming: Boolean,
    ): String? {
        if (!upcoming) return null
        val minutesUntil = (startMillis - nowMillis) / 60_000L
        if (minutesUntil <= CHIP_COUNTDOWN_WINDOW_MINUTES) return null

        val start = Instant.ofEpochMilli(startMillis).atZone(zone)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val time = TimeFormat.minutesToHm(start.hour * 60 + start.minute)
        val verb = appContext.getString(
            if (EventType.fromStorage(event.eventType) == EventType.PERFORMANCE) {
                R.string.notification_chip_verb_performance
            } else {
                R.string.notification_chip_verb_tokuten
            },
        )
        return if (start.toLocalDate() == now.toLocalDate()) {
            appContext.getString(R.string.notification_chip_today, time, verb)
        } else {
            val weekday = start.dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale.SIMPLIFIED_CHINESE)
            appContext.getString(R.string.notification_chip_later, weekday, time)
        }
    }

    private fun applyAndroid16LiveUpdate(
        builder: Notification.Builder,
        startMillis: Long,
        endMillis: Long,
        nowMillis: Long,
        upcoming: Boolean,
        upcomingChipText: String? = null,
    ) {
        if (Build.VERSION.SDK_INT < 36) return

        val durationMs = max(1, (endMillis - startMillis).toInt())
        val progressMs = (nowMillis - startMillis).toInt().coerceIn(0, durationMs)
        val progressStyle = Notification.ProgressStyle()
            .setProgress(progressMs)
            .addProgressSegment(Notification.ProgressStyle.Segment(durationMs))

        builder.setStyle(progressStyle)

        if (!upcoming) {
            builder.setShortCriticalText(appContext.getString(R.string.notification_short_text))
        } else if (upcomingChipText != null) {
            builder.setShortCriticalText(upcomingChipText)
        }

        // Promotion (Live Updates / HyperOS island) works on every Android 16 build,
        // not just QPR2: verified on HyperOS 3 (API 36.0), where the island surfaces
        // ProgressStyle notifications carrying the promotion request while ignoring
        // MIUI focus payloads. The public setter only exists on 36.1+, so on 36.0 we
        // write the same extra NotificationCompat emits. Colorized is never combined
        // with a promotion request (QPR2 treats that as ineligible).
        if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
            builder.setRequestPromotedOngoing(true)
        } else {
            builder.addExtras(Bundle().apply {
                putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
            })
        }
    }

    private fun scheduleNextBoundary(
        events: List<ScheduleEvent>,
        nowMillis: Long,
        intervalMs: Long,
    ) {
        val next = NotificationSchedule.nextRefreshAt(
            events, nowMillis, zone, intervalMs,
        )
        val pendingIntent = boundaryPendingIntent()
        alarmManager.cancel(pendingIntent)
        if (next != null) {
            // 用精确闹钟：setAndAllowWhileIdle 的一次性闹钟在设备低活跃分桶下会被系统
            // 批处理/节流到数分钟一次，导致进度条每次可见更新跨度过大（看起来像一下跳 20%）。
            // setExactAndAllowWhileIdle 不被分桶延迟，进度条得以按设定间隔平滑推进。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent)
            }
        }
    }

    /**
     * 进度条刷新间隔：目标是让每次刷新都让进度推进约 1%（= 按事件时长均分约 100 段），
     * 同时限制在 [PROGRESS_REFRESH_MIN_MS, PROGRESS_REFRESH_MAX_MS] 之间，兼顾平滑与续航。
     * 进行中用组合窗口（多个同时进行的事件取并集），空档期用下一项窗口。
     */
    private fun progressRefreshIntervalMs(
        events: List<ScheduleEvent>,
        nowMillis: Long,
        zone: ZoneId,
    ): Long {
        val active = NotificationSchedule.activeAt(events, nowMillis, zone)
        val (start, end) = if (active.isNotEmpty()) {
            active.minOf { it.startMillis } to active.maxOf { it.endMillis }
        } else {
            val up = NotificationSchedule.nextWindowAfter(events, nowMillis, zone)
            (up?.startMillis ?: nowMillis) to (up?.endMillis ?: (nowMillis + PROGRESS_REFRESH_MAX_MS))
        }
        val durationMs = max(1L, end - start)
        return (durationMs / PROGRESS_REFRESH_STEPS).coerceIn(
            PROGRESS_REFRESH_MIN_MS,
            PROGRESS_REFRESH_MAX_MS,
        )
    }

    private fun cancelBoundaryAlarm() {
        alarmManager.cancel(boundaryPendingIntent())
    }

    private fun boundaryPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        BOUNDARY_REQUEST_CODE,
        Intent(appContext, ScheduleAlarmReceiver::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * 关键时间点提醒（开始前 3 天、当天 0 点、前 1 小时）与「显示未来日程」
     * 开关无关，只受总通知开关约束。任意时刻只挂最近一个触发点的闹钟，
     * 触发后由 handleReminder 发通知并 refresh 推进到下一个。
     */
    private fun scheduleNextReminder(events: List<ScheduleEvent>, nowMillis: Long) {
        val next = NotificationSchedule.nextReminderAfter(events, nowMillis, zone)
        alarmManager.cancel(reminderPendingIntent())
        if (next != null) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                next.triggerMillis,
                reminderPendingIntent(next.event.id, next.kind),
            )
        }
    }

    private fun cancelReminderAlarm() {
        alarmManager.cancel(reminderPendingIntent())
    }

    private fun reminderPendingIntent(
        eventId: Long = -1L,
        kind: ReminderKind? = null,
    ): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        REMINDER_ALARM_REQUEST_CODE,
        Intent(appContext, ScheduleAlarmReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .putExtra(EXTRA_EVENT_ID, eventId)
            .putExtra(EXTRA_REMINDER_KIND, kind?.ordinal ?: -1),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_REFRESH = "com.lamuier.scheduletimeline.action.REFRESH_NOTIFICATIONS"
        const val ACTION_REMINDER = "com.lamuier.scheduletimeline.action.SCHEDULE_REMINDER"
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_REMINDER_KIND = "extra_reminder_kind"
        const val LIVE_NOTIFICATION_ID = 4101
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        private const val CHANNEL_ID = "schedule_live_updates_v2"
        private const val LEGACY_CHANNEL_ID = "schedule_live_updates"
        private const val REMINDER_CHANNEL_ID = "schedule_reminders"
        private const val CONTENT_REQUEST_CODE = 4102
        private const val BOUNDARY_REQUEST_CODE = 4103
        private const val REMINDER_ALARM_REQUEST_CODE = 4108
        private const val REMINDER_CONTENT_REQUEST_CODE = 4109
        private const val REMINDER_NOTIFICATION_ID_BASE = 5_000
        private const val REMINDER_AUTO_DISMISS_MILLIS = 5 * 60_000L
        private const val CHIP_COUNTDOWN_WINDOW_MINUTES = 120L
        private const val PROGRESS_REFRESH_MIN_MS = 5_000L
        private const val PROGRESS_REFRESH_MAX_MS = 60_000L
        private const val PROGRESS_REFRESH_STEPS = 100L // 把事件时长均分约 100 段 ≈ 每次刷新推进 1%
    }
}
