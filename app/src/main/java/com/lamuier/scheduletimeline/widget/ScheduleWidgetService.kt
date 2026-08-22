package com.lamuier.scheduletimeline.widget

import android.content.Intent
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
 * 列表项稳定 id 必须是事件 id：若用 position，删除后 launcher 会把旧行当成同一项缓存下来。
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
        events = runBlocking { application.repository.eventsForDay(todayKey) }
    }

    override fun getCount(): Int = events.size

    override fun getItemId(position: Int): Long =
        events.getOrNull(position)?.id ?: (-position.toLong() - 1)

    override fun hasStableIds(): Boolean = true

    override fun getViewAt(position: Int): RemoteViews {
        val event = events.getOrNull(position)
            ?: return RemoteViews(appContext.packageName, R.layout.widget_schedule_item)
        val views = RemoteViews(appContext.packageName, R.layout.widget_schedule_item)

        val type = EventType.fromStorage(event.eventType)
        val team = event.teamDisplay.ifBlank { event.title }
            .ifBlank { appContext.getString(R.string.event_untitled) }

        views.setTextViewText(
            R.id.widget_item_time,
            TimeFormat.rangeLabel(event.startMinutes, event.endMinutes),
        )
        views.setTextViewText(R.id.widget_item_title, team)
        views.setTextViewText(R.id.widget_item_chip, EventLabels.typeMark(event))

        val (chipColor, chipBgRes) = chipColors(type)
        views.setTextColor(R.id.widget_item_chip, appContext.getColor(chipColor))
        views.setInt(R.id.widget_item_chip, "setBackgroundResource", chipBgRes)

        views.setTextColor(R.id.widget_item_time, appContext.getColor(R.color.widget_primary))
        views.setTextColor(R.id.widget_item_title, appContext.getColor(R.color.widget_on_surface))
        // item root 背景已在 XML 中设置圆角 drawable

        // 点击单项深链回主屏并打开对应事件编辑页
        val fillIntent = Intent().apply {
            putExtra(ScheduleWidgetProviderLarge.EXTRA_ITEM_EVENT_ID, event.id)
        }
        views.setOnClickFillInIntent(R.id.widget_item_root, fillIntent)
        return views
    }

    private fun chipColors(type: EventType): Pair<Int, Int> = when (type) {
        EventType.PERFORMANCE ->
            R.color.widget_chip_performance to R.drawable.widget_chip_performance
        EventType.TOKUTEN ->
            R.color.widget_chip_tokuten to R.drawable.widget_chip_tokuten
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun onDestroy() = Unit
}
