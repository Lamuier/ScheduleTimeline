package com.lamuier.scheduletimeline.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.lamuier.scheduletimeline.MainActivity
import com.lamuier.scheduletimeline.R
import com.lamuier.scheduletimeline.ScheduleApplication
import com.lamuier.scheduletimeline.data.EventLabels
import com.lamuier.scheduletimeline.data.NotificationSchedule
import com.lamuier.scheduletimeline.data.ScheduleEvent
import com.lamuier.scheduletimeline.data.ScheduledEventWindow
import com.lamuier.scheduletimeline.data.TimeFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 桌面小组件数据装配。
 *
 * 复用 [NotificationSchedule] 计算进行中 / 下一项状态，保证与通知栏语义一致；
 * 颜色走 `res/values[-night]/widget_colors.xml`，由系统按夜间模式自动选择。
 */
object ScheduleWidgetUpdater {

    suspend fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val smallIds = manager.getAppWidgetIds(
            ComponentName(appContext, ScheduleWidgetProviderSmall::class.java),
        )
        val largeIds = manager.getAppWidgetIds(
            ComponentName(appContext, ScheduleWidgetProviderLarge::class.java),
        )
        if (smallIds.isEmpty() && largeIds.isEmpty()) return

        val application = appContext as? ScheduleApplication ?: return
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val todayKey = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val events = runCatching { application.repository.eventsForDay(todayKey) }
            .getOrDefault(emptyList())

        val windows = NotificationSchedule.windows(events, zone)
        val active = windows
            .filter { now in it.startMillis until it.endMillis }
            .sortedBy { it.startMillis }
        val next = windows
            .filter { it.startMillis > now }
            .minByOrNull { it.startMillis }

        smallIds.forEach { id ->
            manager.updateAppWidget(id, buildSmall(appContext, today, events, active, next))
        }
        if (largeIds.isNotEmpty()) {
            val largeViews = buildLarge(appContext, today, events, active, next)
            largeIds.forEach { id ->
                manager.updateAppWidget(id, largeViews)
            }
            manager.notifyAppWidgetViewDataChanged(largeIds, R.id.widget_list)
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 2×1 紧凑卡：状态行 + 主标题 + 时间副行。 */
    private fun buildSmall(
        context: Context,
        today: LocalDate,
        events: List<ScheduleEvent>,
        active: List<ScheduledEventWindow>,
        next: ScheduledEventWindow?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_schedule_small)

        val statusText: String
        val mainText: String
        val subText: String

        when {
            active.isNotEmpty() -> {
                if (active.size == 1) {
                    val event = active.first().event
                    statusText = context.getString(R.string.widget_status_ongoing)
                    mainText = EventLabels.displayLabel(event)
                    subText = TimeFormat.rangeLabel(event.startMinutes, event.endMinutes)
                } else {
                    statusText = context.getString(
                        R.string.widget_status_ongoing_multiple,
                        active.size,
                    )
                    mainText = active.joinToString("、") { EventLabels.displayLabel(it.event) }
                    subText = TimeFormat.rangeLabel(
                        active.minOf { it.event.startMinutes },
                        active.maxOf { it.event.endMinutes },
                    )
                }
            }

            next != null -> {
                statusText = context.getString(R.string.widget_status_upcoming)
                mainText = EventLabels.displayLabel(next.event)
                val eventDate = runCatching { LocalDate.parse(next.event.dayKey) }.getOrDefault(today)
                subText = if (eventDate == today) {
                    TimeFormat.minutesToHm(next.event.startMinutes)
                } else {
                    val weekday = eventDate.dayOfWeek
                        .getDisplayName(TextStyle.SHORT, Locale.SIMPLIFIED_CHINESE)
                    context.getString(
                        R.string.widget_upcoming_later,
                        weekday,
                        TimeFormat.minutesToHm(next.event.startMinutes),
                    )
                }
            }

            else -> {
                statusText = context.getString(R.string.app_name)
                mainText = if (events.isEmpty()) {
                    context.getString(R.string.widget_empty_today)
                } else {
                    context.getString(R.string.widget_status_finished)
                }
                subText = today.format(DateTimeFormatter.ofPattern(
                    context.getString(R.string.date_pattern),
                ))
            }
        }

        views.setTextViewText(R.id.widget_small_status, statusText)
        views.setTextViewText(R.id.widget_small_title, mainText)
        views.setTextViewText(R.id.widget_small_subtitle, subText)

        views.setTextColor(R.id.widget_small_status, context.getColor(R.color.widget_primary))
        views.setTextColor(R.id.widget_small_title, context.getColor(R.color.widget_on_surface))
        views.setTextColor(
            R.id.widget_small_subtitle,
            context.getColor(R.color.widget_on_surface_variant),
        )
        views.setInt(R.id.widget_root, "setBackgroundColor", context.getColor(R.color.widget_background))

        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        return views
    }

    /** 4×3 列表卡：顶部摘要 + 当日全部日程。 */
    private fun buildLarge(
        context: Context,
        today: LocalDate,
        events: List<ScheduleEvent>,
        active: List<ScheduledEventWindow>,
        next: ScheduledEventWindow?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_schedule_large)

        val dateText = today.format(DateTimeFormatter.ofPattern(
            context.getString(R.string.date_pattern),
        ))
        val weekday = today.dayOfWeek
            .getDisplayName(TextStyle.SHORT, Locale.SIMPLIFIED_CHINESE)

        val summaryText = when {
            active.isNotEmpty() -> if (active.size == 1) {
                context.getString(R.string.widget_status_ongoing)
            } else {
                context.getString(R.string.widget_status_ongoing_multiple, active.size)
            }
            next != null -> context.getString(R.string.widget_status_upcoming)
            events.isEmpty() -> context.getString(R.string.widget_empty_today)
            else -> context.getString(R.string.widget_status_finished)
        }

        views.setTextViewText(R.id.widget_large_title, context.getString(R.string.app_name))
        views.setTextViewText(
            R.id.widget_large_date,
            context.getString(R.string.widget_date_with_weekday, dateText, weekday),
        )
        views.setTextViewText(R.id.widget_large_summary, summaryText)

        views.setTextColor(R.id.widget_large_title, context.getColor(R.color.widget_primary))
        views.setTextColor(R.id.widget_large_date, context.getColor(R.color.widget_on_surface))
        views.setTextColor(
            R.id.widget_large_summary,
            context.getColor(R.color.widget_on_surface_variant),
        )

        views.setInt(R.id.widget_root, "setBackgroundColor", context.getColor(R.color.widget_background))
        views.setInt(R.id.widget_card, "setBackgroundColor", context.getColor(R.color.widget_surface))

        val empty = events.isEmpty()
        views.setViewVisibility(R.id.widget_list, if (empty) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_empty, if (empty) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_empty_today_hint))
        views.setTextColor(R.id.widget_empty, context.getColor(R.color.widget_on_surface_variant))

        // RemoteViewsService 提供列表数据
        val listIntent = Intent(context, ScheduleWidgetService::class.java)
        views.setRemoteAdapter(R.id.widget_list, listIntent)
        // 列表项点击模板：合并 service 的 fillInIntent 后启动 MainActivity
        views.setPendingIntentTemplate(R.id.widget_list, openAppIntent(context))

        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        return views
    }
}
