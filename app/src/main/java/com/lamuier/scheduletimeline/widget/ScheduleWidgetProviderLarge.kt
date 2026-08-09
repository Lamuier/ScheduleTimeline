package com.lamuier.scheduletimeline.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 4×3 列表小组件：展示当日全部日程。 */
class ScheduleWidgetProviderLarge : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { ScheduleWidgetUpdater.refreshAll(context) }
            pending.finish()
        }
    }

    companion object {
        /** 列表项 fillInIntent 携带的事件 id（预留后续点击直达编辑页）。 */
        const val EXTRA_ITEM_EVENT_ID = "extra_widget_item_event_id"
    }
}
