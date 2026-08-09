package com.lamuier.scheduletimeline.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lamuier.scheduletimeline.ScheduleApplication
import com.lamuier.scheduletimeline.widget.ScheduleWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != ScheduleNotificationCoordinator.ACTION_REFRESH &&
            action != ScheduleNotificationCoordinator.ACTION_REMINDER &&
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pendingResult = goAsync()
        val application = context.applicationContext as ScheduleApplication
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                if (action == ScheduleNotificationCoordinator.ACTION_REMINDER) {
                    application.notificationCoordinator.handleReminder(intent)
                } else {
                    application.notificationCoordinator.refresh()
                }
            }
            // 通知刷新后同步桌面小组件
            runCatching { ScheduleWidgetUpdater.refreshAll(context) }
            pendingResult.finish()
        }
    }
}
