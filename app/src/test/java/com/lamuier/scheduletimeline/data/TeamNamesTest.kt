package com.lamuier.scheduletimeline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamNamesTest {

    @Test
    fun encodeDecode_preservesOrderedDistinctNames() {
        val stored = TeamNames.encode(listOf("StarDiary", "银烁花火", "StarDiary", " "))

        assertEquals(listOf("StarDiary", "银烁花火"), TeamNames.decode(stored))
        assertEquals("StarDiary / 银烁花火", TeamNames.display(stored))
    }

    @Test
    fun fromCsv_acceptsReadableSeparators() {
        val stored = TeamNames.fromCsv("StarDiary / 银烁花火、Demo|Guest")

        assertEquals(
            listOf("StarDiary", "银烁花火", "Demo", "Guest"),
            TeamNames.decode(stored),
        )
    }

    @Test
    fun shareAny_matchesAtLeastOneExactTeamName() {
        val tokutenTeams = TeamNames.encode(listOf("StarDiary", "银烁花火"))

        assertTrue(TeamNames.shareAny(tokutenTeams, TeamNames.encode(listOf("银烁花火"))))
        assertFalse(TeamNames.shareAny(tokutenTeams, TeamNames.encode(listOf("Star"))))
        assertFalse(TeamNames.shareAny(tokutenTeams, ""))
    }
}
