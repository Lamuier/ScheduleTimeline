package com.lamuier.scheduletimeline.data

/**
 * 「现在」红线在单个列表项里的纵向位置。
 * 普通项对齐左右两侧时间标签的中线；重叠组对齐轨道盒子（列标题下面），和卡片用的是同一条时间比例。
 */
object NowLinePlacement {
    fun inItem(
        itemHeightPx: Float,
        fraction: Float,
        labelLinePx: Float,
        bottomPadPx: Float,
    ): Float {
        val frac = fraction.coerceIn(0f, 1f)
        val startCenter = labelLinePx / 2f
        val endCenter = (itemHeightPx - bottomPadPx - labelLinePx / 2f).coerceAtLeast(startCenter)
        return startCenter + frac * (endCenter - startCenter)
    }

    fun inOverlapLane(headerPx: Float, laneHeightPx: Float, fraction: Float): Float {
        val frac = fraction.coerceIn(0f, 1f)
        return headerPx + frac * laneHeightPx.coerceAtLeast(1f)
    }

    /**
     * 正在进行的空闲段内容区最小高度。
     * 两侧时间标签中线之间的距离至少是「分钟数 × 理想比例尺」，
     * 这样 15:44 不会贴在 15:40 的字上。
     * [labelLineDp] 是起始时间标签的行高，要算进内容区，时间跨度才落在两条标签之间。
     */
    fun liveGapContentMinDp(minutes: Int, labelLineDp: Float = 16f): Float =
        minutes.coerceAtLeast(1) * LaneScale.IDEAL_PX_PER_MINUTE + labelLineDp
}
