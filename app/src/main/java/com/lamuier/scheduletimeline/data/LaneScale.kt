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
     * 卡片限高：短卡按比例，长卡封顶；封顶产生的「无用空间」由
     * [LaneFlow] 的截断补偿消除（时间衔接的后卡贴上来）。
     * 卡内文字内容按封顶后的高度完整排布，长卡下方为纯色延伸。
     */
    const val MAX_CARD_DP = 320f

    /**
     * 组内没有更高可读需求时，整组希望压到的高度；可读性需求更大时以可读性优先，
     * 过长的单卡再由 [MAX_CARD_DP] 封顶。
     */
    const val MAX_LANE_DP = 600f

    /**
     * 比例尺上限。再高的话，一两分钟的切片会把整组位置拉得过开，
     * 空隙和 offset 都爆炸。10dp/分钟下 5 分钟切片还有单行时间可读；
     * 更短的切片退化为纯色条，信息进详情 Sheet。
     */
    const val MAX_PX_PER_MINUTE = 10f

    /**
     * 两张卡之间时间空隙的显示高度上限：空隙按线性比例，
     * 超过该值的部分压掉——大段空闲不再拉出数屏空白。
     */
    const val IDLE_GAP_MAX_DP = 80f

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

    /** 卡片内容降级方案：时间显示几行（2 / 1 / 0），标题显示几行（0 = 不显示），副标题是否显示。 */
    data class CardContentPlan(
        val timeLines: Int,
        val titleLines: Int,
        val showSubtitle: Boolean,
    )

    fun contentHeightDp(
        completed: Boolean,
        hasSubtitle: Boolean,
        titleLines: Int,
        timeLines: Int = 2,
    ): Float {
        val lines = titleLines.coerceIn(0, MAX_TITLE_LINES)
        var children = 0
        var height = NARROW_PADDING_V_DP * 2
        if (timeLines > 0) {
            children += 1
            height += TIME_LINE_DP * timeLines
        }
        if (lines > 0) {
            children += 1
            height += TITLE_LINE_DP * lines
        }
        if (completed) {
            children += 1
            height += COMPLETED_LINE_DP
        }
        if (hasSubtitle) {
            children += 1
            height += SUBTITLE_LINE_DP
        }
        if (children > 1) height += BLOCK_SPACING_DP * (children - 1)
        return height
    }

    fun singleSlotDp(completed: Boolean, hasSubtitle: Boolean, titleLines: Int = 1): Float =
        contentHeightDp(completed, hasSubtitle, titleLines) + CARD_GAP_DP + SLACK_DP

    /** 单卡最低可读高度：单行时间 + 两行团队名放得下,名字不丢。 */
    fun minSlotDp(completed: Boolean, hasSubtitle: Boolean): Float =
        contentHeightDp(completed, hasSubtitle, titleLines = 2, timeLines = 1) +
            CARD_GAP_DP + SLACK_DP

    /** 重叠块最低可读高度：单行时间头部 + 一条条目放得下。 */
    fun minOverlapSlotDp(): Float =
        NARROW_PADDING_V_DP * 2 + TIME_LINE_DP + BLOCK_SPACING_DP +
            OVERLAP_ROW_DP + CARD_GAP_DP + SLACK_DP

    /** 重叠块完整展开高度：头部 + 全部条目都放得下(块高仍受 [MAX_CARD_DP] 上限约束)。 */
    fun overlapFullSlotDp(itemCount: Int): Float =
        overlapSlotDp(itemCount).coerceAtMost(MAX_CARD_DP)

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

    /**
     * 按可用高度 [availableDp] 给卡片挑内容降级方案。让位顺序:
     * 副标题(场次说明)先让位;标题行数尽量多(团队名优先于时间的第二行);
     * 标题保到 1 行后,时间两行才降单行;实在放不下退到单行时间;最后纯色条。
     */
    fun cardContentPlan(
        availableDp: Float,
        completed: Boolean,
        hasSubtitle: Boolean,
        fontScale: Float = 1f,
    ): CardContentPlan {
        val available = availableDp
        val font = fontScale.coerceIn(1f, 1.5f)
        for (titleLines in MAX_TITLE_LINES downTo 1) {
            for (timeLines in listOf(2, 1)) {
                for (showSubtitle in (if (hasSubtitle) listOf(true, false) else listOf(false))) {
                    val needed = contentHeightDp(
                        completed = completed,
                        hasSubtitle = hasSubtitle && showSubtitle,
                        titleLines = titleLines,
                        timeLines = timeLines,
                    ) * font
                    if (available + 0.5f >= needed) {
                        return CardContentPlan(timeLines, titleLines, showSubtitle)
                    }
                }
            }
        }
        // 单行时间兜底:时间至少可见,团队名进详情 Sheet。
        if (available + 0.5f >=
            contentHeightDp(completed, false, titleLines = 0, timeLines = 1) * font
        ) {
            return CardContentPlan(timeLines = 1, titleLines = 0, showSubtitle = false)
        }
        return CardContentPlan(timeLines = 0, titleLines = 0, showSubtitle = false)
    }

    fun resolve(laneMinutes: Int, blocks: List<Block>, fontScale: Float = 1f): Float {
        val span = laneMinutes.coerceAtLeast(1).toFloat()
        val font = fontScale.coerceIn(1f, 1.5f)
        val preferred = minOf(IDEAL_PX_PER_MINUTE, MAX_LANE_DP / span)
        val readable = blocks.maxOfOrNull { block ->
            (block.minHeightDp * font) / block.minutes.coerceAtLeast(1)
        } ?: preferred
        // 可读性优先；个别卡片过长时不压缩整组，由 MAX_CARD_DP 单卡封顶。
        return minOf(MAX_PX_PER_MINUTE, maxOf(preferred, readable))
    }
}
