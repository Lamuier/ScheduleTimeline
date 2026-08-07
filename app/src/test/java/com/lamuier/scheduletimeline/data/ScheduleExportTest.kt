package com.lamuier.scheduletimeline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleExportTest {

    @Test
    fun toCsv_includesHeaderAndV2Fields() {
        val perf = ScheduleEvent(
            id = 1,
            team = "StarDiary",
            eventType = EventType.PERFORMANCE.storage,
            startMinutes = 14 * 60 + 20,
            endMinutes = 14 * 60 + 40,
            dayKey = "2026-07-12",
        )
        val tokuten = ScheduleEvent(
            id = 2,
            team = TeamNames.encode(listOf("StarDiary", "银烁花火")),
            eventType = EventType.TOKUTEN.storage,
            tokutenKind = TokutenKind.PARALLEL.storage,
            location = "酒馆A",
            startMinutes = 14 * 60,
            endMinutes = 15 * 60,
            note = "无",
            dayKey = "2026-07-12",
        )
        val csv = ScheduleExport.toCsv(listOf(perf, tokuten))
        assertTrue(csv.startsWith(ScheduleExport.IMPORT_HEADER))
        assertTrue(csv.contains("2026-07-12, StarDiary, 演出, , , , 14:20, 14:40, "))
        assertTrue(
            csv.contains(
                "2026-07-12, StarDiary / 银烁花火, 特典, 平特, , 酒馆A, 14:00, 15:00, 无",
            ),
        )
    }

    @Test
    fun parseImport_skipsHeaderAndReadsSample() {
        val drafts = ScheduleExport.parseImportDrafts(ScheduleExport.IMPORT_SAMPLE)
        assertEquals(2, drafts.size)
        assertEquals(EventType.PERFORMANCE.storage, drafts[0].event.eventType)
        assertEquals("StarDiary", drafts[0].event.team)
        assertEquals(EventType.TOKUTEN.storage, drafts[1].event.eventType)
        assertEquals(listOf("StarDiary", "银烁花火"), drafts[1].event.teamNames)
    }

    @Test
    fun parseImport_acceptsOldTenColumnFormatAndIgnoresManualLink() {
        val drafts = ScheduleExport.parseImportDrafts(
            "2026-06-01, StarDiary, 特典, 平特, , 吧台A, 17:00, 19:00, , 14:20",
        )

        assertEquals(1, drafts.size)
        assertEquals("StarDiary", drafts.single().event.team)
        assertNull(drafts.single().event.linkedPerformanceId)
    }

    @Test
    fun parseImport_v1_mapsTitleToTeamAndCategoryToType() {
        val events = ScheduleExport.parseImport(
            "2026-06-01, StarDiary, 特典, , 14:00, 15:00,\n2026-06-02, Demo, 舞台,,16:00,17:00",
            fallbackDayKey = "2026-07-12",
        )
        assertEquals(2, events.size)
        assertEquals("StarDiary", events[0].team)
        assertEquals(EventType.TOKUTEN.storage, events[0].eventType)
        assertEquals(TokutenKind.PARALLEL.storage, events[0].tokutenKind)
        assertEquals("Demo", events[1].team)
        assertEquals(EventType.PERFORMANCE.storage, events[1].eventType)
        assertEquals("2026-06-02", events[1].dayKey)
    }

    @Test
    fun parseImport_blankDateUsesFallbackDayKey() {
        val events = ScheduleExport.parseImport(
            ", StarDiary, 特典, , 14:00, 15:00,",
            fallbackDayKey = "2026-07-12",
        )
        assertEquals(1, events.size)
        assertEquals("2026-07-12", events[0].dayKey)
    }

    @Test
    fun parseImport_skipsBlankTeamInvalidDateAndInvalidTime() {
        val events = ScheduleExport.parseImport(
            "2026-06-01, , 特典, 酒馆, 14:00, 15:00,\n" +
                "not-a-date, bad, 特典, 酒馆, 14:00, 15:00,\n" +
                "2026-06-01, bad, 特典, 酒馆, xx, 15:00,",
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun parseDayKey_acceptsIsoDate() {
        assertEquals("2026-06-01", ScheduleExport.parseDayKey("2026-06-01"))
        assertNull(ScheduleExport.parseDayKey("06/01/2026"))
        assertNull(ScheduleExport.parseDayKey(""))
    }

    @Test
    fun nearestScheduleHint_prefersFuture() {
        val hint = ScheduleExport.nearestScheduleHint(
            from = LocalDate.parse("2026-06-01"),
            dayKeys = listOf("2026-05-01", "2026-06-10", "2026-06-03"),
        )
        assertEquals("2026-06-03", hint!!.dayKey)
        assertEquals(2L, hint.daysAway)
        assertTrue(hint.isFuture)
    }

    @Test
    fun nearestScheduleHint_fallsBackToPast() {
        val hint = ScheduleExport.nearestScheduleHint(
            from = LocalDate.parse("2026-06-01"),
            dayKeys = listOf("2026-05-20", "2026-05-01"),
        )
        assertEquals("2026-05-20", hint!!.dayKey)
        assertEquals(12L, hint.daysAway)
        assertFalse(hint.isFuture)
    }
}
