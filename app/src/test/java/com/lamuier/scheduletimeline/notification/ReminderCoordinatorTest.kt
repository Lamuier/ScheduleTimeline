package com.lamuier.scheduletimeline.notification

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lamuier.scheduletimeline.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReminderCoordinatorTest {
    private lateinit var database: ScheduleDatabase
    private lateinit var repository: ScheduleRepository
    private lateinit var preferences: NotificationPreferences
    private lateinit var coordinator: ScheduleNotificationCoordinator
    private lateinit var manager: NotificationManager
    private lateinit var alarms: AlarmManager
    private val zone get() = ZoneId.systemDefault()
    private val clock = MutableClock()
    private fun instant(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant()
    private class MutableClock : Clock() {
        var now: Instant = Instant.EPOCH
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneId.systemDefault()
        override fun withZone(zone: ZoneId): Clock = this
    }
    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        database = Room.inMemoryDatabaseBuilder(app, ScheduleDatabase::class.java).build()
        repository = ScheduleRepository(database)
        preferences = NotificationPreferences(app).also { it.setEnabled(true) }
        coordinator = ScheduleNotificationCoordinator(app, repository, preferences, clock)
        manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        clock.now = instant("2026-10-19T23:59:00")
    }
    @After fun close() { database.close() }

    @Test fun sameMomentBroadcastPostsAllEventsOnlyOnceAndDeleteClearsPostedReminder() = runBlocking {
        val first = repository.save(ScheduleEvent(team = "A", dayKey = "2026-10-20", startMinutes = 600, endMinutes = 630))
        repository.save(ScheduleEvent(team = "B", dayKey = "2026-10-20", startMinutes = 660, endMinutes = 690))
        coordinator.refresh()
        clock.now = instant("2026-10-20T00:00:00")
        coordinator.handleReminder(Intent())
        assertEquals(2, manager.activeNotifications.count { it.notification.channelId == "schedule_reminders" })
        coordinator.handleReminder(Intent()); coordinator.refresh()
        assertEquals(2, manager.activeNotifications.count { it.notification.channelId == "schedule_reminders" })
        repository.delete(first); coordinator.refresh()
        assertEquals(1, manager.activeNotifications.count { it.notification.channelId == "schedule_reminders" })
        preferences.setEnabled(false); coordinator.refresh()
        assertTrue(manager.activeNotifications.isEmpty())
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test fun unavailableExactPermissionSchedulesInexactFallback() = runBlocking {
        repository.save(ScheduleEvent(team = "A", dayKey = "2026-10-20", startMinutes = 600, endMinutes = 630))
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        coordinator.refresh()
        assertEquals(2, shadowOf(alarms).scheduledAlarms.size)
        assertTrue(shadowOf(alarms).scheduledAlarms.all { it.windowLengthMs != ShadowAlarmManager.WINDOW_EXACT })
    }
}
