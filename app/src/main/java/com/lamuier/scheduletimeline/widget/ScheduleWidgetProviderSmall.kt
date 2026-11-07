package com.lamuier.scheduletimeline.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** 2×1 紧凑小组件：展示当前状态 / 下一项。 */
class ScheduleWidgetProviderSmall : AppWidgetProvider() {
    override fun onEnabled(context: Context) = refresh(context)
    override fun onDisabled(context: Context) = refresh(context)
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) =
        refresh(context)

    private fun refresh(context: Context) = ScheduleWidgetUpdater.refreshFromBroadcast(context, goAsync())
}
