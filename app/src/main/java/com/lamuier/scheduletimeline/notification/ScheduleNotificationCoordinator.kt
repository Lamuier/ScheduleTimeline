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
import com.lamuier.scheduletimeline.data.teamDisplay
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
    /**
     * 通知标题/正文里的「类型单字」与团队名的区分。
     *
     * 关键约束：Notification 跨进程发给 SystemUI 时，非 ParcelableSpan 的 span（如
     * ReplacementSpan / DynamicDrawableSpan / ImageSpan）都会被丢弃，只剩裸文本。
     * 因此这里不用任何 span，直接把类型单字以「类型字·团队名」的纯文本形式写入标题/正文，
     * 既避免跨进程丢失，也天然把「演/特」与团队名用「·」隔开、区分清楚。
     */
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
            if (event != null && !event.completed) {
                postReminder(event, kind)
            }
        }
        refresh()
    }

    /**
     * 特典「完成」动作：标记后该日程不再进入常驻通知与关键时间点提醒，
     * 已弹出的提醒一并取消，再 refresh 重算当前 Live Update。
     */
    suspend fun handleComplete(intent: Intent?) {
        val eventId = intent?.getLongExtra(EXTRA_EVENT_ID, -1L) ?: -1L
        if (eventId > 0) {
            if (repository.setTokutenCompleted(eventId, completed = true)) {
                cancelRemindersFor(eventId)
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
            .setContentTitle(EventLabels.notificationLabel(event))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_REMINDER)
            // 行程属敏感个人信息：锁屏隐藏详情，仅显示应用名
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setTimeoutAfter(REMINDER_AUTO_DISMISS_MILLIS)
            .build()
        val notificationId = reminderNotificationId(event.id, kind)
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
        val labels = events.joinToString("、") { EventLabels.notificationLabel(it) }
        val title = if (upcoming) {
            appContext.getString(
                R.string.notification_live_title_upcoming,
                EventLabels.notificationLabel(events.first()),
            )
        } else if (events.size == 1) {
            appContext.getString(
                R.string.notification_live_title_single,
                EventLabels.notificationLabel(events.first()),
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
                    EventLabels.notificationLabel(next.event),
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

        val badgedText = text
        val builder = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(islandLargeIcon())
            .setContentTitle(title)
            .setContentText(badgedText)
            .setStyle(Notification.BigTextStyle().bigText(badgedText))
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_EVENT)
            // 行程属敏感个人信息：锁屏隐藏团队名与时间等详情，仅显示脱敏公开版
            // （应用名 + 状态概要）；灵动岛 / Live Updates 在解锁表面不受影响。
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(buildPublicVersion(upcoming))
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
        addTokutenCompleteActions(builder, events, upcoming)
        applyAndroid16LiveUpdate(
            builder = builder,
            startMillis = startMillis,
            endMillis = endMillis,
            nowMillis = nowMillis,
            upcoming = upcoming,
            upcomingChipText = upcomingChipText(events.first(), startMillis, nowMillis, upcoming),
            // HyperOS 3 常驻岛表面实际是 Android Status Chip：shortCriticalText
            // 才是摄像头左侧可见文案；原先写死「进行中」导致看不到团队名+类型。
            activeChipText = activeChipText(events),
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
     * 锁屏脱敏公开版：仅应用名 + 状态概要，不含团队、标题、时间等行程细节。
     */
    private fun buildPublicVersion(upcoming: Boolean): Notification =
        Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(R.string.app_name))
            .setContentText(
                appContext.getString(
                    if (upcoming) {
                        R.string.notification_public_live_upcoming
                    } else {
                        R.string.notification_public_live_active
                    },
                ),
            )
            .build()

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

    /**
     * Status Chip / 灵动岛胶囊左侧短文案：单事件用「类型字·团队名」（如「演·空色轨迹」），
     * 与通知标题格式一致；多事件回退「进行中」。Chip 极窄，超长团队名截断，
     * 保留类型字与分隔点。
     */
    private fun activeChipText(events: List<ScheduleEvent>): String {
        if (events.size != 1) {
            return appContext.getString(R.string.notification_short_text)
        }
        val event = events.first()
        return EventLabels.notificationLabel(event)
            .take(CHIP_TEXT_MAX_CHARS)
            .ifBlank { appContext.getString(R.string.notification_short_text) }
    }

    private fun applyAndroid16LiveUpdate(
        builder: Notification.Builder,
        startMillis: Long,
        endMillis: Long,
        nowMillis: Long,
        upcoming: Boolean,
        upcomingChipText: String? = null,
        activeChipText: String? = null,
    ) {
        if (Build.VERSION.SDK_INT < 36) return

        val durationMs = max(1, (endMillis - startMillis).toInt())
        val progressMs = (nowMillis - startMillis).toInt().coerceIn(0, durationMs)
        val segment = Notification.ProgressStyle.Segment(durationMs)
        val progressStyle = Notification.ProgressStyle()
            .setProgress(progressMs)
            .addProgressSegment(segment)

        builder.setStyle(progressStyle)

        if (!upcoming) {
            builder.setShortCriticalText(
                activeChipText
                    ?: appContext.getString(R.string.notification_short_text),
            )
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

    /**
     * 进行中的特典在 Live Update 上提供「完成」动作；多项特典时每项一条，最多 3 条。
     */
    private fun addTokutenCompleteActions(
        builder: Notification.Builder,
        events: List<ScheduleEvent>,
        upcoming: Boolean,
    ) {
        if (upcoming) return
        val tokuten = events.filter {
            EventType.fromStorage(it.eventType) == EventType.TOKUTEN && !it.completed
        }
        tokuten.take(MAX_COMPLETE_ACTIONS).forEach { event ->
            val title = if (tokuten.size == 1) {
                appContext.getString(R.string.notification_action_complete)
            } else {
                appContext.getString(
                    R.string.notification_action_complete_named,
                    event.teamDisplay.ifBlank { event.title }
                        .ifBlank { appContext.getString(R.string.event_untitled) },
                )
            }
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(appContext, R.drawable.ic_notification),
                    title,
                    completePendingIntent(event.id),
                ).build(),
            )
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

    private fun completePendingIntent(eventId: Long): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        COMPLETE_REQUEST_CODE_BASE + (eventId % 1_000).toInt(),
        Intent(appContext, ScheduleAlarmReceiver::class.java)
            .setAction(ACTION_COMPLETE)
            .putExtra(EXTRA_EVENT_ID, eventId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cancelRemindersFor(eventId: Long) {
        ReminderKind.entries.forEach { kind ->
            notificationManager.cancel(reminderNotificationId(eventId, kind))
        }
    }

    private fun reminderNotificationId(eventId: Long, kind: ReminderKind): Int =
        REMINDER_NOTIFICATION_ID_BASE +
            (eventId % 1_000).toInt() * ReminderKind.entries.size + kind.ordinal

    companion object {
        const val ACTION_REFRESH = "com.lamuier.scheduletimeline.action.REFRESH_NOTIFICATIONS"
        const val ACTION_REMINDER = "com.lamuier.scheduletimeline.action.SCHEDULE_REMINDER"
        const val ACTION_COMPLETE = "com.lamuier.scheduletimeline.action.COMPLETE_TOKUTEN"
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
        private const val COMPLETE_REQUEST_CODE_BASE = 4120
        private const val REMINDER_NOTIFICATION_ID_BASE = 5_000
        private const val REMINDER_AUTO_DISMISS_MILLIS = 5 * 60_000L
        private const val CHIP_COUNTDOWN_WINDOW_MINUTES = 120L
        /** Status Chip 左侧短文案上限（码点），超出优先截断团队名、保留类型后缀。 */
        private const val CHIP_TEXT_MAX_CHARS = 8
        private const val MAX_COMPLETE_ACTIONS = 3
        private const val PROGRESS_REFRESH_MIN_MS = 5_000L
        private const val PROGRESS_REFRESH_MAX_MS = 60_000L
        private const val PROGRESS_REFRESH_STEPS = 100L // 把事件时长均分约 100 段 ≈ 每次刷新推进 1%
    }
}
