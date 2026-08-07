package com.lamuier.scheduletimeline

import android.app.Application
import com.lamuier.scheduletimeline.data.ScheduleDatabase
import com.lamuier.scheduletimeline.data.NotificationPreferences
import com.lamuier.scheduletimeline.data.ScheduleRepository
import com.lamuier.scheduletimeline.data.ThemePreferences
import com.lamuier.scheduletimeline.notification.ScheduleNotificationCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScheduleApplication : Application() {
    lateinit var repository: ScheduleRepository
        private set
    lateinit var themePreferences: ThemePreferences
        private set
    lateinit var notificationPreferences: NotificationPreferences
        private set
    lateinit var notificationCoordinator: ScheduleNotificationCoordinator
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        repository = ScheduleRepository(ScheduleDatabase.get(this))
        themePreferences = ThemePreferences(this)
        notificationPreferences = NotificationPreferences(this)
        notificationCoordinator = ScheduleNotificationCoordinator(
            context = this,
            repository = repository,
            preferences = notificationPreferences,
        )
        refreshNotifications()
    }

    fun refreshNotifications() {
        applicationScope.launch {
            runCatching { notificationCoordinator.refresh() }
        }
    }
}
