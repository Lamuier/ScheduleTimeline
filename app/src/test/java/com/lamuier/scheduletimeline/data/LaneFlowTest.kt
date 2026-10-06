package com.lamuier.scheduletimeline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaneFlowTest {

    private fun place(
        blocks: List<LaneFlow.LaneBlock>,
        scale: Float = 2f,
        groupStart: Int = 0,
    ): LaneFlow.Layout = LaneFlow.place(
        blocks = blocks,
        groupStart = groupStart,
        scale = scale,
        idleGapMaxDp = LaneScale.IDLE_GAP_MAX_DP,
    )

    @Test
    fun place_keepsLinearScaleForUntouchedBlocks() {
        val layout = place(
            listOf(
                LaneFlow.LaneBlock(0, 20, 40f),
                LaneFlow.LaneBlock(50, 80, 60f),
            ),
        )

        // 未被压缩时完全是线性比例:offset = 时间 × 比例尺。
        assertEquals(0f, layout.slots[0].offset, 0.01f)
        // 30 分钟空隙按比例:40 + 60 = 100。
        assertEquals(100f, layout.slots[1].offset, 0.01f)
        assertEquals(160f, layout.height, 0.01f)
    }

    @Test
    fun place_linksAdjacentBlocksFlush() {
        val layout = place(
            listOf(
                LaneFlow.LaneBlock(0, 20, 40f),
                LaneFlow.LaneBlock(20, 60, 80f),
            ),
        )

        // 时间衔接:后块贴住前块视觉底部(4dp 视觉缝由卡内 padding 承担)。
        assertEquals(0f, layout.slots[0].offset, 0.01f)
        assertEquals(40f, layout.slots[1].offset, 0.01f)
        assertEquals(120f, layout.height, 0.01f)
    }

    @Test
    fun place_compressesLongGaps() {
        val layout = place(
            listOf(
                LaneFlow.LaneBlock(0, 20, 40f),
                LaneFlow.LaneBlock(400, 410, 20f),
            ),
        )

        // 380 分钟空隙按比例是 760dp,压缩到上限 80:offset = 40 + 80 = 120。
        assertEquals(120f, layout.slots[1].offset, 0.01f)
        assertEquals(140f, layout.height, 0.01f)
    }

    @Test
    fun place_sortsByStartTime() {
        val layout = place(
            listOf(
                LaneFlow.LaneBlock(60, 80, 20f),
                LaneFlow.LaneBlock(0, 20, 40f),
            ),
        )

        assertEquals(0f, layout.slots[0].offset, 0.01f)
        assertEquals(1, layout.slots[0].index)
        assertEquals(0, layout.slots[1].index)
    }

    @Test
    fun place_empty() {
        val layout = place(emptyList())

        assertTrue(layout.slots.isEmpty())
        assertEquals(0f, layout.height, 0.01f)
        assertNull(LaneFlow.yFor(layout, 10))
    }

    @Test
    fun yFor_interpolatesWithinBlock() {
        val layout = place(listOf(LaneFlow.LaneBlock(0, 20, 40f)))

        assertEquals(20f, LaneFlow.yFor(layout, 10)!!, 0.01f)
        assertEquals(0f, LaneFlow.yFor(layout, 0)!!, 0.01f)
        assertEquals(40f, LaneFlow.yFor(layout, 20)!!, 0.01f)
    }

    @Test
    fun yFor_interpolatesCompressedGapLinearly() {
        val layout = place(
            listOf(
                LaneFlow.LaneBlock(0, 20, 40f),
                LaneFlow.LaneBlock(220, 410, 20f),
            ),
        )

        // 空隙(20~220)按比例是 400dp,压缩为 80dp:y 从 40 走到 120。
        assertEquals(80f, LaneFlow.yFor(layout, 120)!!, 0.01f)
        assertEquals(120f, LaneFlow.yFor(layout, 220)!!, 0.01f)
    }

    @Test
    fun yFor_clampsOutsideCoverage() {
        val layout = place(listOf(LaneFlow.LaneBlock(0, 20, 40f)))

        assertEquals(0f, LaneFlow.yFor(layout, -5)!!, 0.01f)
        assertEquals(40f, LaneFlow.yFor(layout, 999)!!, 0.01f)
    }
}