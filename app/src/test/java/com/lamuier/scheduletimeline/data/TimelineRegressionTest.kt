package com.lamuier.scheduletimeline.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TimelineRegressionTest {
    private fun event(id: Long, start: Int = 600, end: Int = 660,
        type: EventType = EventType.PERFORMANCE) = ScheduleEvent(id = id, team = "Team$id",
        startMinutes = start, endMinutes = end, dayKey = "2026-10-20", eventType = type.storage)

    @Test fun sameTypeContainedAndChainedOverlapsAreSlicedButTouchingIsSeparate() {
        for (type in EventType.entries) {
            val items = TimelineBuilder.build(listOf(event(1, 600, 630, type), event(2, 610, 620, type),
                event(3, 620, 650, type), event(4, 650, 660, type)))
            assertEquals(2, items.size)
            val group = items[0] as TimelineItem.OverlapGroup
            assertEquals(3, group.events.size)
            assertTrue(items[1] is TimelineItem.Event)
            val plan = TimelineLayoutPlan.build(group, TimelineTextMetrics())
            assertEquals(1, plan.columns.size)
            assertEquals(1f, plan.columns.single().widthFraction, 0.01f)
            assertEquals(listOf(600 to 610, 610 to 620, 620 to 630, 630 to 650),
                plan.segments.map { it.startMinutes to it.endMinutes })
            for (minute in 600..649) assertTrue(plan.yFor(minute + 1)!! >= plan.yFor(minute)!!)
        }
    }

    @Test fun oneMinuteMixedSlicesKeepTimeAndTeamAtAllFontScales() {
        val group = TimelineBuilder.build(listOf(event(1, 600, 602),
            event(2, 601, 603, EventType.TOKUTEN).copy(completed = true))).single() as TimelineItem.OverlapGroup
        for (scale in listOf(1f, 1.5f, 2f, 3f)) {
            val metrics = TimelineTextMetrics(22f * scale, 28f * scale, 22f * scale, 24f * scale)
            val plan = TimelineLayoutPlan.build(group, metrics)
            assertEquals(3, plan.segments.size)
            assertEquals(listOf(0f, 0.5f), plan.columns.map { it.startFraction })
            assertEquals(listOf(0.5f, 0.5f), plan.columns.map { it.widthFraction })
            assertTrue(plan.flow.slots.all { it.height <= 320f })
            val content = metrics.contentPlan(plan.flow.slots[1].height - 12f, true, true)
            assertTrue(content.timeLines >= 1)
            assertTrue(content.titleLines >= 1)
            assertTrue(metrics.height(true, content.showSubtitle, content.titleLines, content.timeLines) <= plan.flow.slots[1].height - 12f + 0.5f)
        }
    }

    @Test fun invalidStoredRowsAreExcludedWithoutAlteringThem() {
        val invalid = event(1, 660, 600)
        assertTrue(TimelineBuilder.build(listOf(invalid)).isEmpty())
        assertTrue(NotificationSchedule.windows(listOf(invalid), ZoneOffset.UTC).isEmpty())
        assertNull(ChartLayout.stats(listOf(invalid)))
        assertEquals(60, ChartLayout.stats(listOf(invalid, event(2)))!!.busyMinutes)
        assertEquals(660, invalid.startMinutes)
    }

    @Test fun mixedInvalidImportReportsOriginalLinesAndPreservesQuotedFenceText() {
        val text = "介绍\n```csv\n${ScheduleExport.IMPORT_HEADER}\n" +
            "2026-10-20,A,演出,,,,10:00,11:00,\"首行\n```不要删除\n末行\"\n" +
            "2026-10-20,B,演出,,,,11:00,11:00,\n" +
            "2026-10-20,C,演出,,,,11:00,,\n" +
            "2026-10-20,D,演出,,,,12:00,11:00,\n```"
        val parsed = ScheduleExport.parseImportResult(text, "2026-10-20")
        assertEquals(1, parsed.drafts.size)
        assertEquals(4, parsed.drafts.single().line)
        assertEquals("首行\n```不要删除\n末行", parsed.drafts.single().event.note)
        assertEquals(listOf(7, 8, 9), parsed.issues.map { it.line })
        assertEquals(listOf(ImportError.EndBeforeStart, ImportError.Time, ImportError.EndBeforeStart), parsed.issues.map { it.error })
    }

    @Test fun repeatMergeDoesNotGrowUserTextOrCleanHistoricalText() {
        val first = event(1).copy(title = "第一场 / 原样", location = "一号 / 入口", note = "自带\n换行")
        val second = event(2).copy(title = "第二场", location = "二号", note = "其他备注")
        val merged = EventMerge.plan(listOf(first, second)).updates.single()
        val again = EventMerge.plan(listOf(merged, first.copy(id = 3), second.copy(id = 4)))
        assertTrue(again.updates.isEmpty())
        assertEquals("自带\n换行\n其他备注", merged.note)
        val historical = merged.copy(note = "旧备注\n旧备注")
        assertTrue(EventMerge.plan(listOf(historical, historical.copy(id = 5))).updates.isEmpty())
    }

    @Test fun snapshotUsesInjectedClockAndZoneAcrossMidnight() {
        val before = Clock.fixed(Instant.parse("2026-10-19T15:59:59Z"), ZoneOffset.UTC)
        val zone = ZoneId.of("Asia/Shanghai")
        assertEquals(LocalDate.parse("2026-10-19"), ScheduleTimeSnapshot.now(before, zone).today)
        assertEquals(LocalDate.parse("2026-10-20"), ScheduleTimeSnapshot.now(Clock.offset(before, Duration.ofSeconds(2)), zone).today)
    }
}
