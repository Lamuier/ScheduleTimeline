package com.lamuier.scheduletimeline.widget

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.net.Uri
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.lamuier.scheduletimeline.MainActivity
import com.lamuier.scheduletimeline.R
import com.lamuier.scheduletimeline.ScheduleApplication
import com.lamuier.scheduletimeline.data.EventLabels
import com.lamuier.scheduletimeline.data.NotificationSnapshot
import com.lamuier.scheduletimeline.data.ScheduleEvent
import com.lamuier.scheduletimeline.data.ScheduledEventWindow
import com.lamuier.scheduletimeline.data.TimeFormat
import com.lamuier.scheduletimeline.notification.BroadcastWork
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** 小组件绘制失败的日志标签；只记组件种类和异常，不含日程内容。 */
internal const val WIDGET_LOG_TAG = "ScheduleWidget"

/**
 * 桌面小组件数据装配。
 *
 * 复用 [NotificationSchedule] 计算进行中 / 下一项状态，保证与通知栏语义一致。
 * 颜色全部写在布局 XML 里引用 `res/values[-night]/widget_colors.xml`，由桌面在套用布局时按当前深浅色解析；
 * 不要在这里用 `setTextColor(int)` 写死颜色，否则切换深浅色后文字停在旧主题、背景却已换色。
 */
object ScheduleWidgetUpdater {

    private const val OPEN_APP_REQUEST_CODE = 0
    private const val LIST_ITEM_TEMPLATE_REQUEST_CODE = 4201

    internal data class Targets(val small: List<Int>, val large: List<Int>) {
        val any: Boolean get() = small.isNotEmpty() || large.isNotEmpty()
    }
    private data class ListContent(val today: LocalDate, val events: List<ScheduleEvent>, val config: String)
    private data class SummaryContent(val list: ListContent, val active: List<ScheduledEventWindow>,
        val next: ScheduledEventWindow?, val ids: List<Int>)
    private val mutex = Mutex()
    private var lastContext: Context? = null
    private var lastSmall: SummaryContent? = null
    private var lastLarge: SummaryContent? = null
    private var lastList: Pair<ListContent, List<Int>>? = null

    internal fun targets(context: Context): Targets {
        val manager = AppWidgetManager.getInstance(context)
        return Targets(
            manager.getAppWidgetIds(ComponentName(context, ScheduleWidgetProviderSmall::class.java)).toList(),
            manager.getAppWidgetIds(ComponentName(context, ScheduleWidgetProviderLarge::class.java)).toList(),
        )
    }
    fun hasWidgets(context: Context): Boolean = targets(context).any

    internal suspend fun clearCachedViews() = mutex.withLock {
        lastSmall = null; lastLarge = null; lastList = null
    }

    suspend fun refreshAll(context: Context) {
        (context.applicationContext as? ScheduleApplication)?.refreshNotificationsAndWait(forceWidgets = true)
    }

    /** 组件广播：托住广播强制刷新一轮，最多等 [BroadcastWork.WAIT_BUDGET_MS] 就交还，没做完的在后台跑完。 */
    internal fun refreshFromBroadcast(context: Context, pending: BroadcastReceiver.PendingResult?) {
        val appContext = context.applicationContext
        BroadcastWork.launch("widget update", finish = { pending?.finish() }) { refreshAll(appContext) }
    }

    internal suspend fun refresh(context: Context, allEvents: List<ScheduleEvent>,
        snapshot: NotificationSnapshot, targets: Targets, force: Boolean) = mutex.withLock {
        val appContext = context.applicationContext
        if (lastContext !== appContext) {
            lastContext = appContext
            lastSmall = null; lastLarge = null; lastList = null
        }
        if (!targets.any) {
            lastSmall = null; lastLarge = null; lastList = null
            return@withLock
        }
        val manager = AppWidgetManager.getInstance(appContext)
        val today = java.time.Instant.ofEpochMilli(snapshot.nowMillis).atZone(snapshot.zone).toLocalDate()
        val todayKey = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val events = allEvents.filter { it.dayKey == todayKey }
        val windows = snapshot.windows.filter { it.event.dayKey == todayKey }
        val active = snapshot.active.filter { it.event.dayKey == todayKey }
        val next = windows.filter { it.startMillis > snapshot.nowMillis }.minByOrNull { it.startMillis }
        val list = ListContent(today, events, appContext.resources.configuration.toString())
        val small = SummaryContent(list, active, next, targets.small)
        val large = SummaryContent(list, active, next, targets.large)
        if (targets.small.isEmpty()) lastSmall = null
        else if (force || lastSmall != small) {
            val pushed = push("small") {
                manager.updateAppWidget(targets.small.toIntArray(), buildSmall(appContext, today, events, active, next))
            }
            if (pushed) lastSmall = small
        }
        if (targets.large.isEmpty()) { lastLarge = null; lastList = null }
        else {
            if (force || lastLarge != large) {
                val pushed = push("large") {
                    manager.updateAppWidget(targets.large.toIntArray(), buildLarge(appContext, today, events, active, next))
                }
                if (pushed) lastLarge = large
            }
            val dataset = list to targets.large
            if (force || lastList != dataset) {
                val pushed = push("list") {
                    manager.notifyAppWidgetViewDataChanged(targets.large.toIntArray(), R.id.widget_list)
                }
                if (pushed) lastList = dataset
            }
        }
    }

    /**
     * 一种组件画不成或被宿主拒绝，只记日志，不连累另一种组件。
     * 失败时不更新上一轮缓存，下一轮即使内容没变也会重试。
     */
    private inline fun push(part: String, block: () -> Unit): Boolean = try {
        block()
        true
    } catch (error: Exception) {
        Log.w(WIDGET_LOG_TAG, "$part widget update failed", error)
        false
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * 4×3 列表点击模板：必须可变，否则 Android 12+ 不会把
     * [RemoteViews.setOnClickFillInIntent] 的事件 id 合并进启动 Intent。
     */
    private fun listItemTemplateIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ScheduleWidgetProviderLarge.ACTION_WIDGET_ITEM
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        return PendingIntent.getActivity(
            context,
            LIST_ITEM_TEMPLATE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutable,
        )
    }

    /** 带事件 id 的跳转：点击小组件日程项直达该事件编辑页。 */
    private fun openEventIntent(context: Context, eventId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = "${context.packageName}.OPEN_WIDGET_EVENT"
            data = Uri.parse("schedule://widget/$eventId")
            putExtra(ScheduleWidgetProviderLarge.EXTRA_ITEM_EVENT_ID, eventId)
        }
        // 用事件 id 作 requestCode，避免不同事件的 PendingIntent 被系统合并
        return PendingIntent.getActivity(
            context,
            eventId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 2×1 紧凑卡：圆角卡片，顶部状态+日期，主标题，可选副标题。 */
    private fun buildSmall(
        context: Context,
        today: LocalDate,
        events: List<ScheduleEvent>,
        active: List<ScheduledEventWindow>,
        next: ScheduledEventWindow?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_schedule_small)

        val dateText = today.format(
            DateTimeFormatter.ofPattern(context.getString(R.string.widget_small_date_format)),
        )
        val weekday = today.dayOfWeek
            .getDisplayName(TextStyle.SHORT, Locale.SIMPLIFIED_CHINESE)

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
                    val eventWeekday = eventDate.dayOfWeek
                        .getDisplayName(TextStyle.SHORT, Locale.SIMPLIFIED_CHINESE)
                    context.getString(
                        R.string.widget_upcoming_later,
                        eventWeekday,
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
                subText = ""
            }
        }

        views.setTextViewText(R.id.widget_small_status, statusText)
        views.setTextViewText(
            R.id.widget_small_date,
            context.getString(R.string.widget_date_with_weekday, dateText, weekday),
        )
        views.setTextViewText(R.id.widget_small_title, mainText)
        views.setTextViewText(R.id.widget_small_subtitle, subText)
        views.setViewVisibility(
            R.id.widget_small_subtitle,
            if (subText.isEmpty()) View.GONE else View.VISIBLE,
        )
        // 文字颜色与圆角背景都在 widget_schedule_small.xml 里，随桌面深浅色解析

        // 有主事件（进行中/下一项）时点击直达该事件编辑页，否则仅打开应用
        val primaryEventId = when {
            active.isNotEmpty() -> active.first().event.id
            next != null -> next.event.id
            else -> null
        }
        val clickIntent = primaryEventId?.let { openEventIntent(context, it) }
            ?: openAppIntent(context)
        views.setOnClickPendingIntent(R.id.widget_root, clickIntent)
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
        // 文字颜色、根布局与列表卡片背景都在 widget_schedule_large.xml 里，随桌面深浅色解析

        val empty = events.isEmpty()
        views.setViewVisibility(R.id.widget_list, if (empty) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_empty, if (empty) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_empty_today_hint))

        // RemoteViewsService 提供列表数据
        val listIntent = Intent(context, ScheduleWidgetService::class.java)
        views.setRemoteAdapter(R.id.widget_list, listIntent)
        views.setPendingIntentTemplate(R.id.widget_list, listItemTemplateIntent(context))

        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        return views
    }
}
