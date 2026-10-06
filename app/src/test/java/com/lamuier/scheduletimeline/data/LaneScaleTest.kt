package com.lamuier.scheduletimeline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // 组时长适中（未触 MAX_LANE_DP 上限）时，可读性仍会把比例尺抬起来。
        val scale = LaneScale.resolve(
            laneMinutes = 50,
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

        // 可读性优先：短块不被压矮；过长的单卡由 MAX_CARD_DP 单独封顶。
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
    fun cardContentPlan_prefersFullContentWhenTallEnough() {
        val plan = LaneScale.cardContentPlan(
            availableDp = 240f,
            completed = false,
            hasSubtitle = true,
        )
        assertEquals(2, plan.timeLines)
        assertEquals(LaneScale.MAX_TITLE_LINES, plan.titleLines)
        assertTrue(plan.showSubtitle)
    }

    @Test
    fun cardContentPlan_dropsSubtitleBeforeTitleLines() {
        // 可用 96dp：副标题先让位，保住两行时间 + 一行标题。
        val plan = LaneScale.cardContentPlan(
            availableDp = 96f,
            completed = false,
            hasSubtitle = true,
        )
        assertEquals(2, plan.timeLines)
        assertEquals(1, plan.titleLines)
        assertFalse(plan.showSubtitle)
    }

    @Test
    fun cardContentPlan_fallsBackToSingleLineTimeWhenVeryShort() {
        // 可用 62dp:放不下「单行时间 + 标题」,退到单行时间兜底。
        val plan = LaneScale.cardContentPlan(
            availableDp = 62f,
            completed = false,
            hasSubtitle = true,
        )
        assertEquals(1, plan.timeLines)
        assertEquals(0, plan.titleLines)
        assertFalse(plan.showSubtitle)
    }

    @Test
    fun cardContentPlan_fallsBackToBareBarWhenTooShort() {
        val plan = LaneScale.cardContentPlan(
            availableDp = 4f,
            completed = false,
            hasSubtitle = true,
        )
        assertEquals(0, plan.timeLines)
        assertEquals(0, plan.titleLines)
        assertFalse(plan.showSubtitle)
    }

    @Test
    fun cardContentPlan_minSlotGuaranteesTeamNameVisible() {
        // minSlotDp 的可用高度必须放得下「单行时间 + 两行团队名」。
        val available = LaneScale.minSlotDp(completed = false, hasSubtitle = false) -
            LaneScale.CARD_GAP_DP
        val plan = LaneScale.cardContentPlan(
            availableDp = available,
            completed = false,
            hasSubtitle = false,
        )
        assertEquals(1, plan.timeLines)
        assertEquals(2, plan.titleLines)
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
