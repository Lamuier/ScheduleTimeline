package com.lamuier.scheduletimeline.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 2×1 紧凑小组件：展示当前状态 / 下一项。 */
class ScheduleWidgetProviderSmall : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        (context.applicationContext as com.lamuier.scheduletimeline.ScheduleApplication).refreshNotifications()
    }
    override fun onDisabled(context: Context) {
        (context.applicationContext as com.lamuier.scheduletimeline.ScheduleApplication).refreshNotifications()
    }
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                (context.applicationContext as com.lamuier.scheduletimeline.ScheduleApplication).notificationCoordinator.refresh()
                ScheduleWidgetUpdater.refreshAll(context)
            }
            pending.finish()
        }
    }
}
