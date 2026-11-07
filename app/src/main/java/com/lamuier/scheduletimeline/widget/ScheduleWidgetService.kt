package com.lamuier.scheduletimeline.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.lamuier.scheduletimeline.R
import com.lamuier.scheduletimeline.ScheduleApplication
import com.lamuier.scheduletimeline.data.EventLabels
import com.lamuier.scheduletimeline.data.EventType
import com.lamuier.scheduletimeline.data.ScheduleEvent
import com.lamuier.scheduletimeline.data.TimeFormat
import com.lamuier.scheduletimeline.data.teamDisplay
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 4×3 小组件当日日程列表的 [RemoteViewsService]。
 *
 * 数据在 [onDataSetChanged] 同步加载，确保 widget 在应用未前台时也能拿到最新日程。
 */
class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory {
        return ScheduleWidgetFactory(applicationContext as ScheduleApplication)
    }
}

private class ScheduleWidgetFactory(
    private val application: ScheduleApplication,
) : RemoteViewsService.RemoteViewsFactory {

    private val appContext = application.applicationContext
    private var events: List<ScheduleEvent> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val todayKey = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        // RemoteViewsService 把这里抛出的异常交给默认异常处理器，整个进程会崩溃；读库失败时保留上一份列表。
        events = try {
            runBlocking { application.repository.eventsForDay(todayKey) }
        } catch (error: Exception) {
            Log.w(WIDGET_LOG_TAG, "widget list load failed, keeping previous list", error)
            events
        }
    }

    override fun getCount(): Int = events.size

    override fun getViewAt(position: Int): RemoteViews {
        val event = events.getOrNull(position)
            ?: return RemoteViews(appContext.packageName, R.layout.widget_schedule_item)
        return scheduleWidgetItem(appContext, event)
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = events.getOrNull(position)?.id ?: -1L
    override fun hasStableIds(): Boolean = true
    override fun onDestroy() = Unit
}

/** 列表单项。颜色都在 widget_schedule_item.xml 里，由桌面按当前深浅色解析。 */
internal fun scheduleWidgetItem(context: Context, event: ScheduleEvent): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_schedule_item)
    val team = event.teamDisplay.ifBlank { event.title }
        .ifBlank { context.getString(R.string.event_untitled) }

    views.setTextViewText(
        R.id.widget_item_time,
        TimeFormat.rangeLabel(event.startMinutes, event.endMinutes),
    )
    views.setTextViewText(R.id.widget_item_title, team)

    // 两种标签各有一套 XML 配色，按类型切换可见性；列表项会被宿主复用，两个都要显式设置。
    val tokuten = EventType.fromStorage(event.eventType) == EventType.TOKUTEN
    val shown = if (tokuten) R.id.widget_item_chip_tokuten else R.id.widget_item_chip_performance
    val hidden = if (tokuten) R.id.widget_item_chip_performance else R.id.widget_item_chip_tokuten
    views.setTextViewText(shown, EventLabels.typeMark(event))
    views.setViewVisibility(shown, View.VISIBLE)
    views.setViewVisibility(hidden, View.GONE)

    views.setOnClickFillInIntent(
        R.id.widget_item_root,
        Intent().putExtra(ScheduleWidgetProviderLarge.EXTRA_ITEM_EVENT_ID, event.id),
    )
    return views
}
