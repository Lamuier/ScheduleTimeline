package com.lamuier.scheduletimeline.ui.edit

import com.lamuier.scheduletimeline.data.EventType
import com.lamuier.scheduletimeline.data.TokutenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass

class EditUiStateTest {

    @Test
    fun tokutenEffectiveTeams_mergesSelectedAndPendingNames() {
        val state = EditUiState(
            eventType = EventType.TOKUTEN,
            teamNames = listOf("StarDiary"),
            teamInput = "银烁花火 / StarDiary",
        )

        assertEquals(listOf("StarDiary", "银烁花火"), state.effectiveTeamNames())
    }

    @Test
    fun performanceEffectiveTeams_keepsMergedSelectionWhenInputIsBlank() {
        val state = EditUiState(
            eventType = EventType.PERFORMANCE,
            teamNames = listOf("StarDiary", "空色轨迹"),
        )

        assertEquals(listOf("StarDiary", "空色轨迹"), state.effectiveTeamNames())
    }

    @Test
    fun performanceEffectiveTeams_keepsOnlyLatestPendingName() {
        val state = EditUiState(
            eventType = EventType.PERFORMANCE,
            teamNames = listOf("StarDiary"),
            teamInput = "银烁花火",
        )

        assertEquals(listOf("银烁花火"), state.effectiveTeamNames())
    }

    @Test
    fun serialVersionUid_isFixedAndJavaSerializationRoundTripsEnums() {
        val descriptor = ObjectStreamClass.lookup(EditUiState::class.java)
        assertEquals(1L, descriptor.serialVersionUID)

        val original = EditUiState(
            sessionId = "s-1",
            dayKey = "2026-10-20",
            loadedId = 42L,
            teamNames = listOf("StarDiary", "银烁花火"),
            teamInput = "空色轨迹",
            eventType = EventType.TOKUTEN,
            tokutenKind = TokutenKind.FINAL,
            title = "晚场",
            location = "Livehouse",
            startMinutes = 18 * 60,
            endMinutes = 19 * 60 + 30,
            note = "备注",
            error = EditValidationError.EndBeforeStart,
            isNew = false,
        )

        val bytes = ByteArrayOutputStream().use { out ->
            ObjectOutputStream(out).use { it.writeObject(original) }
            out.toByteArray()
        }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use {
            it.readObject() as EditUiState
        }

        assertEquals(original, restored)
    }

    @Test
    fun hasUnsavedChanges_comparesAgainstMatchingBaselineAndIgnoresLoadingOrMissing() {
        val baseline = EditUiState(
            sessionId = "s-1",
            dayKey = "2026-10-20",
            loadedId = 10L,
            teamNames = listOf("StarDiary"),
            eventType = EventType.PERFORMANCE,
            startMinutes = 600,
            endMinutes = 660,
            isNew = false,
        )

        assertFalse(baseline.hasUnsavedChanges(baseline))
        assertTrue(baseline.copy(startMinutes = 630, endMinutes = 690).hasUnsavedChanges(baseline))
        assertTrue(baseline.copy(eventType = EventType.TOKUTEN).hasUnsavedChanges(baseline))
        assertTrue(baseline.copy(tokutenKind = TokutenKind.PRE).hasUnsavedChanges(baseline))
        assertTrue(baseline.copy(teamInput = "银烁花火").hasUnsavedChanges(baseline))
        assertTrue(baseline.copy(note = "draft").hasUnsavedChanges(baseline))
        assertFalse(baseline.copy(note = "draft", error = EditValidationError.Loading).hasUnsavedChanges(baseline))
        assertFalse(baseline.copy(note = "draft", error = EditValidationError.MissingEvent).hasUnsavedChanges(baseline))

        val noBaseline = EditUiState(sessionId = "s-2")
        assertFalse(noBaseline.hasUnsavedChanges(null))
        assertTrue(noBaseline.copy(note = "unsaved").hasUnsavedChanges(null))
        assertTrue(noBaseline.copy(note = "unsaved").hasUnsavedChanges(baseline))
    }

    @Test
    fun withoutTeam_removesTargetFromSelectionAndInputWhileKeepingOtherInput() {
        val state = EditUiState(
            teamNames = listOf("A", "B"),
            teamInput = "A / C",
        )
        val updated = state.withoutTeam("A")
        assertEquals(listOf("B"), updated.teamNames)
        assertEquals("C", updated.teamInput)

        val untouchedInput = EditUiState(
            teamNames = listOf("A", "B"),
            teamInput = "C / ",
        ).withoutTeam("A")
        assertEquals(listOf("B"), untouchedInput.teamNames)
        assertEquals("C / ", untouchedInput.teamInput)
    }
}
