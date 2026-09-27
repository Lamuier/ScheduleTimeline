package com.lamuier.scheduletimeline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NowLinePlacementTest {

    @Test
    fun inItem_placesFractionBetweenLabelCenters() {
        val y = NowLinePlacement.inItem(
            itemHeightPx = 200f,
            fraction = 0.2f,
            labelLinePx = 16f,
            bottomPadPx = 16f,
        )
        val startCenter = 8f
        val endCenter = 200f - 16f - 8f
        assertEquals(startCenter + 0.2f * (endCenter - startCenter), y, 0.01f)
    }

    @Test
    fun inItem_startAndEndSitOnTheLabelCenters() {
        assertEquals(8f, NowLinePlacement.inItem(200f, 0f, 16f, 16f), 0.01f)
        assertEquals(176f, NowLinePlacement.inItem(200f, 1f, 16f, 16f), 0.01f)
    }

    @Test
    fun inOverlapLane_usesTheLaneNotTheRowTop() {
        // 列标题 24px，轨道 400px。现在在 60% 处，应落在轨道内而不是整行顶部。
        val y = NowLinePlacement.inOverlapLane(headerPx = 24f, laneHeightPx = 400f, fraction = 0.6f)
        assertEquals(24f + 240f, y, 0.01f)
        assertTrue(y > 24f)
    }

    @Test
    fun liveGap_fourMinutesIntoTwentyIsClearlyBelowTheStartLabel() {
        val minutes = 20
        val elapsed = 4
        val label = 16f
        val bottomPad = 16f
        val contentMin = NowLinePlacement.liveGapContentMinDp(minutes, label)
        val itemHeight = contentMin + bottomPad
        val y = NowLinePlacement.inItem(
            itemHeightPx = itemHeight,
            fraction = elapsed.toFloat() / minutes,
            labelLinePx = label,
            bottomPadPx = bottomPad,
        )
        // 起始标签占 0..label。红线要落到标签下方，不能盖住 15:40。
        assertTrue(y > label)
        val startCenter = label / 2f
        assertEquals(startCenter + elapsed * LaneScale.IDEAL_PX_PER_MINUTE, y, 0.01f)
    }
}
