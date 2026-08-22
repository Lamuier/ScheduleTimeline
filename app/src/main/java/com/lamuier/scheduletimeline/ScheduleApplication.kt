package com.lamuier.scheduletimeline

import android.app.Application
import com.lamuier.scheduletimeline.data.ScheduleDatabase
import com.lamuier.scheduletimeline.data.NotificationPreferences
import com.lamuier.scheduletimeline.data.ScheduleRepository
import com.lamuier.scheduletimeline.data.ThemePreferences
import com.lamuier.scheduletimeline.notification.ScheduleNotificationCoordinator
import com.lamuier.scheduletimeline.widget.ScheduleWidgetUpdater
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
        AppShortcuts.register(this)
        observeScheduleChanges()
        refreshNotifications()
    }

    /**
     * 以 Room 为小组件 / 通知的单一数据源：任意日程增删改（含删除团队时连带改写）
     * 都会自动刷新，避免只靠调用方记得 [refreshNotifications]。
     */
    private fun observeScheduleChanges() {
        applicationScope.launch {
            repository.observeAll().collect {
                refreshNotifications()
            }
        }
    }

    fun refreshNotifications() {
        applicationScope.launch {
            runCatching { notificationCoordinator.refresh() }
            // 同步刷新桌面小组件，与通知栏共享同一日程状态
            runCatching { ScheduleWidgetUpdater.refreshAll(this@ScheduleApplication) }
        }
    }
}
