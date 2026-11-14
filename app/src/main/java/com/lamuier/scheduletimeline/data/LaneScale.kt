package com.lamuier.scheduletimeline.data

/**
 * 并行轨道的比例尺常量与卡片内容降级模型。
 */
object LaneScale {
    /** 时长足够时，每分钟对应的 dp。 */
    const val IDEAL_PX_PER_MINUTE = 4f

    /**
     * 两张卡之间时间空隙的显示高度上限：空隙按线性比例，
     * 超过该值的部分压掉——大段空闲不再拉出数屏空白。
     */
    const val IDLE_GAP_MAX_DP = 80f

    const val CARD_GAP_DP = 4f

    /** 卡片内容降级方案：时间显示几行（2 / 1 / 0），标题显示几行（0 = 不显示），副标题是否显示。 */
    data class CardContentPlan(
        val timeLines: Int,
        val titleLines: Int,
        val showSubtitle: Boolean,
    )
}
