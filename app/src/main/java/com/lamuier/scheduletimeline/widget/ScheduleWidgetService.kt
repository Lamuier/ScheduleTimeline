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
        views.setTextViewText(R.id.widget_item_chip, EventLabels.typeChip(event))

        val (chipColor, chipBg) = chipColors(type)
        views.setTextColor(R.id.widget_item_chip, appContext.getColor(chipColor))
        views.setInt(R.id.widget_item_chip, "setBackgroundColor", appContext.getColor(chipBg))

        views.setTextColor(R.id.widget_item_time, appContext.getColor(R.color.widget_primary))
        views.setTextColor(R.id.widget_item_title, appContext.getColor(R.color.widget_on_surface))
        views.setInt(
            R.id.widget_item_root,
            "setBackgroundColor",
            appContext.getColor(R.color.widget_surface),
        )

        // 点击单项同样回到主屏（编辑跳转需 Compose 导航参数，留待后续）
        val fillIntent = Intent().apply {
            putExtra(ScheduleWidgetProviderLarge.EXTRA_ITEM_EVENT_ID, event.id)
        }
        views.setOnClickFillInIntent(R.id.widget_item_root, fillIntent)
        return views
    }

    private fun chipColors(type: EventType): Pair<Int, Int> = when (type) {
        EventType.PERFORMANCE ->
            R.color.widget_chip_performance to R.color.widget_chip_performance_bg
        EventType.TOKUTEN ->
            R.color.widget_chip_tokuten to R.color.widget_chip_tokuten_bg
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
