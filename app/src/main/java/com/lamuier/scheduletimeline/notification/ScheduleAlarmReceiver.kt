package com.lamuier.scheduletimeline.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lamuier.scheduletimeline.ScheduleApplication

class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != ScheduleNotificationCoordinator.ACTION_REFRESH &&
            action != ScheduleNotificationCoordinator.ACTION_REMINDER &&
            action != ScheduleNotificationCoordinator.ACTION_COMPLETE &&
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_DATE_CHANGED &&
            action != android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pendingResult = goAsync()
        val application = context.applicationContext as ScheduleApplication
        BroadcastWork.launch(
            label = "broadcast ${action.substringAfterLast('.')}",
            finish = { pendingResult?.finish() },
        ) {
            when (action) {
                ScheduleNotificationCoordinator.ACTION_REMINDER ->
                    application.notificationCoordinator.handleReminder(intent)
                ScheduleNotificationCoordinator.ACTION_COMPLETE ->
                    application.notificationCoordinator.handleComplete(intent)
                else -> application.refreshNotificationsAndWait(forceWidgets = action != ScheduleNotificationCoordinator.ACTION_REFRESH)
            }
        }
    }
}
