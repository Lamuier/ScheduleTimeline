package com.lamuier.scheduletimeline.ui.edit

import com.lamuier.scheduletimeline.data.EventType
import org.junit.Assert.assertEquals
import org.junit.Test

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
    fun performanceEffectiveTeams_keepsOnlyLatestPendingName() {
        val state = EditUiState(
            eventType = EventType.PERFORMANCE,
            teamNames = listOf("StarDiary"),
            teamInput = "银烁花火",
        )

        assertEquals(listOf("银烁花火"), state.effectiveTeamNames())
    }
}
