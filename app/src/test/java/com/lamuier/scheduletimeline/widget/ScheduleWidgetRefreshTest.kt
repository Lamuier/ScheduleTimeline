package com.lamuier.scheduletimeline.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.widget.RemoteViews
import android.app.AlarmManager
import android.app.NotificationManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lamuier.scheduletimeline.data.*
import com.lamuier.scheduletimeline.notification.ScheduleNotificationCoordinator
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAppWidgetManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, shadows = [RecordingWidgetManager::class])
class ScheduleWidgetRefreshTest {
    @Test fun notificationDisabledStillRefreshesPlacedWidgetsAndSchedulesTheirBoundary() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val manager = AppWidgetManager.getInstance(app)
        val shadow = Shadow.extract<RecordingWidgetManager>(manager)
        shadow.addBoundWidget(1, AppWidgetProviderInfo().apply {
            provider = ComponentName(app, ScheduleWidgetProviderLarge::class.java)
        })
        val database = Room.inMemoryDatabaseBuilder(app, ScheduleDatabase::class.java).build()
        try {
            val repository = ScheduleRepository(database)
            repository.save(ScheduleEvent(team = "A", dayKey = "2026-10-15", startMinutes = 600, endMinutes = 660))
            val preferences = NotificationPreferences(app).also { it.setEnabled(false) }
            val zone = ZoneId.systemDefault()
            val now = LocalDateTime.parse("2026-10-15T09:00:00").atZone(zone).toInstant()
            ScheduleNotificationCoordinator(app, repository, preferences, Clock.fixed(now, zone)).refresh()
            assertEquals(1, shadow.updates)
            assertEquals(1, shadow.reloads)
            assertTrue(app.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
            assertEquals(1, shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
        } finally { database.close() }
    }

    @Test fun progressTicksSkipDatasetReloadButBoundaryMidnightAndForceStillUpdate() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val manager = AppWidgetManager.getInstance(app)
        val shadow = Shadow.extract<RecordingWidgetManager>(manager)
        shadow.addBoundWidget(1, AppWidgetProviderInfo().apply {
            provider = ComponentName(app, ScheduleWidgetProviderSmall::class.java)
        })
        shadow.addBoundWidget(2, AppWidgetProviderInfo().apply {
            provider = ComponentName(app, ScheduleWidgetProviderLarge::class.java)
        })
        val targets = ScheduleWidgetUpdater.targets(app)
        assertTrue(targets.any)
        val events = listOf(ScheduleEvent(id = 1, team = "A", dayKey = "2026-10-15",
            startMinutes = 600, endMinutes = 660))
        suspend fun refresh(time: String, force: Boolean = false) {
            val snapshot = NotificationSchedule.snapshot(events, Instant.parse(time).toEpochMilli(), ZoneOffset.UTC)
            ScheduleWidgetUpdater.refresh(app, events, snapshot, targets, force)
        }
        refresh("2026-10-15T10:01:00Z")
        assertEquals(2, shadow.updates); assertEquals(1, shadow.reloads)
        refresh("2026-10-15T10:01:30Z")
        assertEquals(2, shadow.updates); assertEquals(1, shadow.reloads)
        refresh("2026-10-15T11:00:00Z")
        assertEquals(4, shadow.updates); assertEquals(1, shadow.reloads)
        refresh("2026-10-16T00:00:00Z")
        assertEquals(6, shadow.updates); assertEquals(2, shadow.reloads)
        refresh("2026-10-16T00:00:00Z", force = true)
        assertEquals(8, shadow.updates); assertEquals(3, shadow.reloads)
        shadow.addBoundWidget(3, AppWidgetProviderInfo().apply {
            provider = ComponentName(app, ScheduleWidgetProviderLarge::class.java)
        })
        ScheduleWidgetUpdater.refresh(app, events,
            NotificationSchedule.snapshot(events, Instant.parse("2026-10-16T00:01:00Z").toEpochMilli(), ZoneOffset.UTC),
            ScheduleWidgetUpdater.targets(app), false)
        assertEquals(9, shadow.updates); assertEquals(4, shadow.reloads)
    }
}

@Implements(AppWidgetManager::class)
class RecordingWidgetManager : ShadowAppWidgetManager() {
    var updates = 0
    var reloads = 0
    @Implementation
    public override fun updateAppWidget(ids: IntArray, views: RemoteViews) { updates++ }
    @Implementation
    protected fun notifyAppWidgetViewDataChanged(ids: IntArray, viewId: Int) { reloads++ }
}
