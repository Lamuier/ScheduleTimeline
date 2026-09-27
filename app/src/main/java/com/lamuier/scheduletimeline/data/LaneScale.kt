package com.lamuier.scheduletimeline.data

/**
 * 并行轨道的时间比例尺。
 * 卡片高度仍跟时长成正比，但最短的一块也不能矮到裁掉时间或标题。
 * 左右两列必须共用同一个比例尺，时间才对得齐。
 */
object LaneScale {
    /** 时长足够时，每分钟对应的 dp。 */
    const val IDEAL_PX_PER_MINUTE = 4f

    /**
     * 整组偏长时希望压到的高度。只在压完之后每块仍然放得下内容时才生效。
     */
    const val MAX_LANE_DP = 600f

    /**
     * 比例尺上限。再高的话，一两分钟的切片会把整天拉得过长。
     * 5 分钟大约还能放下时间和一行标题；更短的切片用省略号收住，不再拆字。
     */
    const val MAX_PX_PER_MINUTE = 20f

    const val CARD_GAP_DP = 4f
    const val NARROW_PADDING_V_DP = 8f
    const val BLOCK_SPACING_DP = 6f
    const val TIME_LINE_DP = 22f
    const val TIME_BLOCK_DP = TIME_LINE_DP * 2
    const val TITLE_LINE_DP = 28f
    const val COMPLETED_LINE_DP = 22f
    const val SUBTITLE_LINE_DP = 24f
    const val SLACK_DP = 8f

    /** 重叠块里每一行：内边距 + 最多两行标题。 */
    const val OVERLAP_ROW_DP = 68f

    const val MAX_TITLE_LINES = 4

    data class Block(
        val minutes: Int,
        val minHeightDp: Float,
    )

    fun contentHeightDp(completed: Boolean, hasSubtitle: Boolean, titleLines: Int): Float {
        val lines = titleLines.coerceIn(1, MAX_TITLE_LINES)
        var children = 2
        var height = NARROW_PADDING_V_DP * 2 + TIME_BLOCK_DP + TITLE_LINE_DP * lines
        if (completed) {
            children += 1
            height += COMPLETED_LINE_DP
        }
        if (hasSubtitle) {
            children += 1
            height += SUBTITLE_LINE_DP
        }
        height += BLOCK_SPACING_DP * (children - 1)
        return height
    }

    fun singleSlotDp(completed: Boolean, hasSubtitle: Boolean, titleLines: Int = 1): Float =
        contentHeightDp(completed, hasSubtitle, titleLines) + CARD_GAP_DP + SLACK_DP

    fun overlapSlotDp(itemCount: Int): Float {
        val count = itemCount.coerceAtLeast(1)
        val gaps = count + 1
        return NARROW_PADDING_V_DP * 2 +
            TIME_LINE_DP * 2 +
            BLOCK_SPACING_DP * gaps +
            OVERLAP_ROW_DP * count +
            CARD_GAP_DP +
            SLACK_DP
    }

    /**
     * 卡片内部高度（不含轨道间隙）里，标题最多能排几行而不被裁切。
     * [fontScale] 与 [resolve] 使用同一套放大，避免大字号时多算出行数。
     */
    fun titleLinesThatFit(
        cardInnerDp: Float,
        completed: Boolean,
        hasSubtitle: Boolean,
        fontScale: Float = 1f,
    ): Int {
        val font = fontScale.coerceIn(1f, 1.5f)
        for (lines in MAX_TITLE_LINES downTo 1) {
            val needed = contentHeightDp(completed, hasSubtitle, lines) * font
            if (cardInnerDp + 0.5f >= needed) return lines
        }
        return 1
    }

    fun resolve(laneMinutes: Int, blocks: List<Block>, fontScale: Float = 1f): Float {
        val span = laneMinutes.coerceAtLeast(1).toFloat()
        val font = fontScale.coerceIn(1f, 1.5f)
        val preferred = minOf(IDEAL_PX_PER_MINUTE, MAX_LANE_DP / span)
        val readable = blocks.maxOfOrNull { block ->
            (block.minHeightDp * font) / block.minutes.coerceAtLeast(1)
        } ?: preferred
        return minOf(MAX_PX_PER_MINUTE, maxOf(preferred, readable))
    }
}
