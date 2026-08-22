package com.lamuier.scheduletimeline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class NotificationScheduleTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 8, 4)

    @Test
    fun activeAt_returnsOverlappingEvents() {
        val events = listOf(
            event(1, 10 * 60, 11 * 60),
            event(2, 10 * 60 + 30, 12 * 60),
        )
        val now = date.atStartOfDay(zone).toInstant().toEpochMilli() + (10 * 60 + 45) * 60_000L

        assertEquals(listOf(1L, 2L), NotificationSchedule.activeAt(events, now, zone).map { it.event.id })
    }

    @Test
    fun nextBoundaryAfter_returnsNearestStartOrEnd() {
        val events = listOf(event(1, 10 * 60, 11 * 60), event(2, 12 * 60, 13 * 60))
        val now = date.atStartOfDay(zone).toInstant().toEpochMilli() + 9 * 60 * 60_000L

        assertTrue(
            NotificationSchedule.nextBoundaryAfter(events, now, zone) ==
                date.atStartOfDay(zone).toInstant().toEpochMilli() + 10 * 60 * 60_000L,
        )
    }

    @Test
    fun nextWindowAfter_returnsUpcomingEventForResidentNotification() {
        val events = listOf(event(1, 10 * 60, 11 * 60), event(2, 12 * 60, 13 * 60))
        val now = date.atStartOfDay(zone).toInstant().toEpochMilli() + 11 * 60 * 60_000L

        assertEquals(2L, NotificationSchedule.nextWindowAfter(events, now, zone)!!.event.id)
    }

    @Test
    fun nextRefreshAt_returnsPeriodicIntervalWhileActive() {
        val events = listOf(event(1, 10 * 60, 11 * 60))
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        // 10:20，事件 10:00-11:00 进行中
        val now = dayStart + (10 * 60 + 20) * 60_000L

        // 进行中：取 min(下一边界 11:00, now + 60s) = now + 60s，让进度条周期走动
        assertEquals(
            now + 60_000L,
            NotificationSchedule.nextRefreshAt(events, now, zone, 60_000L),
        )
    }

    @Test
    fun nextRefreshAt_returnsBoundaryWhenNoActiveEvent() {
        val events = listOf(event(1, 10 * 60, 11 * 60))
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        // 09:00，空档，下一项 10:00 未开始
        val now = dayStart + 9 * 60 * 60_000L

        // 空档：不周期刷新，返回下一边界（10:00 开始），保证 upcoming → active 切换可靠
        assertEquals(
            dayStart + 10 * 60 * 60_000L,
            NotificationSchedule.nextRefreshAt(events, now, zone, 60_000L),
        )
    }

    @Test
    fun remindersFor_alignsToLocalWallClock() {
        val window = NotificationSchedule.windows(listOf(event(1, 10 * 60, 11 * 60)), zone).single()
        val reminders = NotificationSchedule.remindersFor(window, zone)
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()

        assertEquals(3, reminders.size)
        // 开始前 3 天：与开始同一时刻（本地墙钟）
        assertEquals(
            dayStart - 3 * 24 * 60 * 60_000L + 10 * 60 * 60_000L,
            reminders.single { it.kind == ReminderKind.THREE_DAYS_BEFORE }.triggerMillis,
        )
        // 当天 0 点
        assertEquals(
            dayStart,
            reminders.single { it.kind == ReminderKind.DAY_OF_MIDNIGHT }.triggerMillis,
        )
        // 开始前 1 小时
        assertEquals(
            dayStart + 9 * 60 * 60_000L,
            reminders.single { it.kind == ReminderKind.ONE_HOUR_BEFORE }.triggerMillis,
        )
    }

    @Test
    fun nextReminderAfter_picksEarliestFutureTrigger() {
        val events = listOf(event(1, 10 * 60, 11 * 60))
        // 3 天前的触发点已过、0 点未到时，应选当天 0 点
        val now = date.atStartOfDay(zone).toInstant().toEpochMilli() - 60 * 60_000L

        val next = NotificationSchedule.nextReminderAfter(events, now, zone)!!
        assertEquals(ReminderKind.DAY_OF_MIDNIGHT, next.kind)
        assertEquals(1L, next.event.id)
    }

    @Test
    fun nextReminderAfter_skipsMissedTriggersSilently() {
        // 距开始仅剩 30 分钟才录入：3 天前与 0 点触发点均已过，只补 1 小时前（也已过则为空）
        val events = listOf(event(1, 10 * 60, 11 * 60))
        val nowBeforeOneHour = date.atStartOfDay(zone).toInstant().toEpochMilli() + 8 * 60 * 60_000L
        val nowAfterOneHour = date.atStartOfDay(zone).toInstant().toEpochMilli() +
            (9 * 60 + 30) * 60_000L

        val next = NotificationSchedule.nextReminderAfter(events, nowBeforeOneHour, zone)!!
        assertEquals(ReminderKind.ONE_HOUR_BEFORE, next.kind)
        assertEquals(null, NotificationSchedule.nextReminderAfter(events, nowAfterOneHour, zone))
    }

    @Test
    fun windows_skipsCompletedTokuten() {
        val events = listOf(
            event(1, 10 * 60, 11 * 60).copy(completed = true),
            event(2, 12 * 60, 13 * 60),
        )
        val now = date.atStartOfDay(zone).toInstant().toEpochMilli() + (10 * 60 + 30) * 60_000L

        assertTrue(NotificationSchedule.activeAt(events, now, zone).isEmpty())
        assertEquals(2L, NotificationSchedule.nextWindowAfter(events, now, zone)!!.event.id)
    }

    @Test
    fun nextReminderAfter_skipsCompletedTokuten() {
        val events = listOf(event(1, 10 * 60, 11 * 60).copy(completed = true))
        val now = date.atStartOfDay(zone).toInstant().toEpochMilli() - 60 * 60_000L

        assertEquals(null, NotificationSchedule.nextReminderAfter(events, now, zone))
    }

    private fun event(id: Long, start: Int, end: Int) = ScheduleEvent(
        id = id,
        team = "Team$id",
        startMinutes = start,
        endMinutes = end,
        dayKey = date.toString(),
    )
}
