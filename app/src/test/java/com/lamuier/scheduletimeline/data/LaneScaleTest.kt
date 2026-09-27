package com.lamuier.scheduletimeline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaneScaleTest {

    @Test
    fun resolve_keepsIdealScaleWhenEveryBlockIsAlreadyTallEnough() {
        val block = LaneScale.Block(
            minutes = 60,
            minHeightDp = LaneScale.singleSlotDp(completed = false, hasSubtitle = false),
        )

        val scale = LaneScale.resolve(laneMinutes = 100, blocks = listOf(block))

        assertEquals(LaneScale.IDEAL_PX_PER_MINUTE, scale, 0.01f)
    }

    @Test
    fun resolve_raisesScaleSoShortBlocksStayReadable() {
        val short = LaneScale.singleSlotDp(completed = false, hasSubtitle = false)
        val completed = LaneScale.singleSlotDp(completed = true, hasSubtitle = true)
        val scale = LaneScale.resolve(
            laneMinutes = 100,
            blocks = listOf(
                LaneScale.Block(15, short),
                LaneScale.Block(30, completed),
            ),
        )

        assertTrue(scale > LaneScale.IDEAL_PX_PER_MINUTE)
        assertTrue(15 * scale >= short)
        assertTrue(30 * scale >= completed)
    }

    @Test
    fun resolve_doesNotShrinkBelowReadableHeightForALongLane() {
        val minHeight = LaneScale.singleSlotDp(completed = false, hasSubtitle = false)
        val scale = LaneScale.resolve(
            laneMinutes = 600,
            blocks = listOf(LaneScale.Block(15, minHeight)),
        )

        assertTrue(15 * scale >= minHeight)
        assertTrue(scale > LaneScale.MAX_LANE_DP / 600f)
    }

    @Test
    fun resolve_capsExtremeShortSlices() {
        val minHeight = LaneScale.singleSlotDp(completed = false, hasSubtitle = false)
        val scale = LaneScale.resolve(
            laneMinutes = 120,
            blocks = listOf(LaneScale.Block(1, minHeight)),
        )

        assertEquals(LaneScale.MAX_PX_PER_MINUTE, scale, 0.01f)
    }

    @Test
    fun resolve_growsWithFontScaleButStaysCapped() {
        val minHeight = LaneScale.singleSlotDp(completed = false, hasSubtitle = false)
        val normal = LaneScale.resolve(100, listOf(LaneScale.Block(15, minHeight)), fontScale = 1f)
        val larger = LaneScale.resolve(100, listOf(LaneScale.Block(15, minHeight)), fontScale = 1.4f)
        val huge = LaneScale.resolve(100, listOf(LaneScale.Block(1, minHeight)), fontScale = 3f)

        assertTrue(larger > normal)
        assertEquals(LaneScale.MAX_PX_PER_MINUTE, huge, 0.01f)
    }

    @Test
    fun resolve_emptyBlocks_usesPreferredScale() {
        assertEquals(LaneScale.IDEAL_PX_PER_MINUTE, LaneScale.resolve(90, emptyList()), 0.01f)
        assertEquals(LaneScale.MAX_LANE_DP / 300f, LaneScale.resolve(300, emptyList()), 0.01f)
    }

    @Test
    fun titleLines_atMinimumSlot_staysOneLine() {
        val inner = LaneScale.singleSlotDp(completed = true, hasSubtitle = true) - LaneScale.CARD_GAP_DP
        assertEquals(1, LaneScale.titleLinesThatFit(inner, completed = true, hasSubtitle = true))
    }

    @Test
    fun titleLines_usesExtraHeightWithoutCountingAPartialLine() {
        val oneLine = LaneScale.contentHeightDp(completed = false, hasSubtitle = false, titleLines = 1)
        val almostTwo = oneLine + LaneScale.TITLE_LINE_DP - 1f
        val two = oneLine + LaneScale.TITLE_LINE_DP

        assertEquals(1, LaneScale.titleLinesThatFit(almostTwo, completed = false, hasSubtitle = false))
        assertEquals(2, LaneScale.titleLinesThatFit(two, completed = false, hasSubtitle = false))
    }

    @Test
    fun titleLines_largeFontDoesNotPretendExtraLinesFit() {
        val font = 1.5f
        val inner = LaneScale.contentHeightDp(false, false, 1) * font + LaneScale.SLACK_DP
        assertEquals(1, LaneScale.titleLinesThatFit(inner, false, false, fontScale = font))
    }
}
