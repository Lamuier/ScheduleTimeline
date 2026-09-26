package com.lamuier.scheduletimeline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventMergeTest {

    @Test
    fun plan_mergesSameSlotAndSameTypeIntoOneEvent() {
        val events = listOf(
            event(1, "StarDiary", start = 16 * 60, end = 17 * 60 + 30),
            event(2, "空色轨迹", start = 16 * 60, end = 17 * 60 + 30),
            event(3, "银炼花火", start = 16 * 60, end = 17 * 60 + 30),
        )

        val plan = EventMerge.plan(events)

        assertEquals(listOf(2L, 3L), plan.deleteIds)
        val merged = plan.updates.single()
        assertEquals(1L, merged.id)
        assertEquals(listOf("StarDiary", "空色轨迹", "银炼花火"), merged.teamNames)
        assertEquals(EventType.TOKUTEN.storage, merged.eventType)
        assertEquals(TokutenKind.PARALLEL.storage, merged.tokutenKind)
    }

    @Test
    fun plan_keepsDifferentTokutenKindsSeparate() {
        val events = listOf(
            event(1, "StarDiary", kind = TokutenKind.PRE),
            event(2, "空色轨迹", kind = TokutenKind.FINAL),
        )

        assertTrue(EventMerge.plan(events).isEmpty)
    }

    @Test
    fun plan_keepsDifferentTimeRangesSeparate() {
        val events = listOf(
            event(1, "StarDiary", end = 17 * 60),
            event(2, "空色轨迹", end = 17 * 60 + 30),
        )

        assertTrue(EventMerge.plan(events).isEmpty)
    }

    @Test
    fun plan_keepsPerformanceAndTokutenSeparate() {
        val events = listOf(
            event(1, "StarDiary", eventType = EventType.PERFORMANCE, kind = null),
            event(2, "空色轨迹"),
        )

        assertTrue(EventMerge.plan(events).isEmpty)
    }

    @Test
    fun plan_keepsOtherDaysSeparate() {
        val events = listOf(
            event(1, "StarDiary", dayKey = "2026-09-27"),
            event(2, "空色轨迹", dayKey = "2026-09-28"),
        )

        assertTrue(EventMerge.plan(events).isEmpty)
    }

    @Test
    fun plan_unionsTeamsAndKeepsDistinctDetails() {
        val events = listOf(
            event(
                4,
                teams = listOf("StarDiary", "银炼花火"),
                note = "带会员卡",
                location = "吧台A",
                title = "午场",
                completed = true,
            ),
            event(
                9,
                teams = listOf("银炼花火", "空色轨迹"),
                note = "带会员卡",
                location = "吧台B",
                title = "加演",
                completed = false,
            ),
        )

        val merged = EventMerge.plan(events).updates.single()

        assertEquals(listOf("StarDiary", "银炼花火", "空色轨迹"), merged.teamNames)
        assertEquals("午场 / 加演", merged.title)
        assertEquals("吧台A / 吧台B", merged.location)
        assertEquals("带会员卡", merged.note)
        assertEquals(false, merged.completed)
        assertEquals(listOf(9L), EventMerge.plan(events).deleteIds)
    }

    @Test
    fun plan_marksCompletedOnlyWhenEverySourceIsCompleted() {
        val events = listOf(
            event(1, "StarDiary", completed = true),
            event(2, "空色轨迹", completed = true),
        )

        assertEquals(true, EventMerge.plan(events).updates.single().completed)
    }

    @Test
    fun plan_usesTitleAsTeamWhenTeamIsBlank() {
        val events = listOf(
            event(1, teams = emptyList(), title = "StarDiary"),
            event(2, "空色轨迹"),
        )

        val merged = EventMerge.plan(events).updates.single()

        assertEquals(listOf("StarDiary", "空色轨迹"), merged.teamNames)
        assertEquals("", merged.title)
    }

    @Test
    fun plan_mergesPerformancesAndClearsCompleted() {
        val events = listOf(
            event(1, "StarDiary", eventType = EventType.PERFORMANCE, kind = null, completed = true),
            event(2, "空色轨迹", eventType = EventType.PERFORMANCE, kind = null, completed = true),
        )

        val merged = EventMerge.plan(events).updates.single()

        assertEquals(listOf("StarDiary", "空色轨迹"), merged.teamNames)
        assertEquals(EventType.PERFORMANCE.storage, merged.eventType)
        assertEquals("", merged.tokutenKind)
        assertEquals(false, merged.completed)
    }

    @Test
    fun plan_skipsUpdateWhenKeeperAlreadyHoldsTheUnion() {
        val keeper = event(1, teams = listOf("StarDiary", "空色轨迹"))
        val duplicate = event(2, "空色轨迹")

        val plan = EventMerge.plan(listOf(keeper, duplicate))

        assertTrue(plan.updates.isEmpty())
        assertEquals(listOf(2L), plan.deleteIds)
    }

    private fun event(
        id: Long,
        team: String = "",
        teams: List<String>? = null,
        start: Int = 16 * 60,
        end: Int = 17 * 60 + 30,
        eventType: EventType = EventType.TOKUTEN,
        kind: TokutenKind? = TokutenKind.PARALLEL,
        dayKey: String = "2026-09-27",
        title: String = "",
        location: String = "",
        note: String = "",
        completed: Boolean = false,
    ) = ScheduleEvent(
        id = id,
        team = TeamNames.encode(teams ?: listOf(team).filter { it.isNotBlank() }),
        title = title,
        startMinutes = start,
        endMinutes = end,
        eventType = eventType.storage,
        tokutenKind = kind?.storage.orEmpty(),
        dayKey = dayKey,
        location = location,
        note = note,
        completed = completed,
    )
}
