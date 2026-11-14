package com.lamuier.scheduletimeline.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EventLabelsTest {

    @Test
    fun typeMark_isSingleCharacter() {
        assertEquals("演", EventLabels.typeMark(performance()))
        assertEquals("特", EventLabels.typeMark(tokuten(TokutenKind.PRE)))
        assertEquals("特", EventLabels.typeMark(tokuten(kind = null)))
    }

    @Test
    fun typeChip_tokutenShowsKindWithoutPrefix() {
        assertEquals("前特", EventLabels.typeChip(tokuten(TokutenKind.PRE)))
        assertEquals("平特", EventLabels.typeChip(tokuten(TokutenKind.PARALLEL)))
        assertEquals("终特", EventLabels.typeChip(tokuten(TokutenKind.FINAL)))
        assertEquals("特典", EventLabels.typeChip(tokuten(kind = null)))
    }

    @Test
    fun typeChip_performanceStaysPerformance() {
        assertEquals("演出", EventLabels.typeChip(performance()))
    }

    @Test
    fun displayLabel_usesSingleCharacterMark() {
        assertEquals("StarDiary演", EventLabels.displayLabel(performance()))
        assertEquals("StarDiary特", EventLabels.displayLabel(tokuten(TokutenKind.PRE)))
    }

    @Test
    fun notificationLabel_usesTypeMarkDotTeam() {
        assertEquals("演·StarDiary", EventLabels.notificationLabel(performance()))
        assertEquals("特·StarDiary", EventLabels.notificationLabel(tokuten(TokutenKind.PRE)))
    }

    @Test
    fun eventTypeStorageFromCsv_recognizesChineseAndEnglishCaseInsensitively() {
        assertEquals(EventType.TOKUTEN, EventLabels.eventTypeStorageFromCsv("TOKUTEN"))
        assertEquals(EventType.TOKUTEN, EventLabels.eventTypeStorageFromCsv("tokuten"))
        assertEquals(EventType.TOKUTEN, EventLabels.eventTypeStorageFromCsv("  Tokuten  "))
        assertEquals(EventType.TOKUTEN, EventLabels.eventTypeStorageFromCsv("特典"))
        assertEquals(EventType.TOKUTEN, EventLabels.eventTypeStorageFromCsv("特典会"))
        assertEquals(EventType.PERFORMANCE, EventLabels.eventTypeStorageFromCsv("PERFORMANCE"))
        assertEquals(EventType.PERFORMANCE, EventLabels.eventTypeStorageFromCsv("performance"))
        assertEquals(EventType.PERFORMANCE, EventLabels.eventTypeStorageFromCsv("演出"))
        assertEquals(EventType.PERFORMANCE, EventLabels.eventTypeStorageFromCsv("舞台演出"))
    }

    private fun performance() = ScheduleEvent(
        team = "StarDiary",
        startMinutes = 14 * 60,
        endMinutes = 15 * 60,
        eventType = EventType.PERFORMANCE.storage,
    )

    private fun tokuten(kind: TokutenKind?) = ScheduleEvent(
        team = "StarDiary",
        startMinutes = 17 * 60,
        endMinutes = 19 * 60,
        eventType = EventType.TOKUTEN.storage,
        tokutenKind = kind?.storage.orEmpty(),
    )
}
