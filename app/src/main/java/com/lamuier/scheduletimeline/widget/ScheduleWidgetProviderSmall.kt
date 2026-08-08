package com.lamuier.scheduletimeline.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 2×1 紧凑小组件：展示当前状态 / 下一项。 */
class ScheduleWidgetProviderSmall : AppWidgetProvider() {
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
}
