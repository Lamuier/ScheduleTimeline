package com.lamuier.scheduletimeline.data

/**
 * 轨道的统一骨架布局（跨列共享时间轴）：
 *
 * - 调用方把所有列的日程合并成不重叠的骨架块 [LaneBlock]（块高已按两列内容
 *   需求与限高决定），本布局按时间顺序排列它们：
 *   - 时间衔接（前块结束 == 后块开始）的后块紧贴前块视觉底部；
 *   - 时间空隙按线性比例显示，超过 idleGapMaxDp 的部分压掉；
 *   - 首块之前的空隙按线性比例。
 * - 同一块在所有列里使用同一个 offset 与高度，跨列的先后顺序与同时刻对齐
 *   由此保证；块内含压缩不再全局线性，因此同时输出「时间 → y」映射供红线插值。
 */
object LaneFlow {
    /** 一个骨架块的占位：[offset] 是块顶 y，[height] 是块高。 */
    data class Slot(
        val index: Int,
        val offset: Float,
        val height: Float,
    )

    /** 骨架块：时间区间 + 已决定好的显示高度。 */
    data class LaneBlock(
        val start: Int,
        val end: Int,
        val height: Float,
    )

    data class Layout(
        val slots: List<Slot>,
        /** 整列布局总高。 */
        val height: Float,
        /** 时间 → y 的锚点（按追加顺序，时间非降序），供 [yFor] 插值。 */
        val marks: List<Pair<Int, Float>>,
    )

    fun place(
        blocks: List<LaneBlock>,
        groupStart: Int,
        scale: Float,
        idleGapMaxDp: Float,
    ): Layout {
        if (blocks.isEmpty()) return Layout(emptyList(), 0f, emptyList())

        val order = blocks.withIndex()
            .sortedWith(compareBy({ it.value.start }, { it.value.end }, { it.index }))

        val slots = mutableListOf<Slot>()
        val marks = mutableListOf<Pair<Int, Float>>()
        var cursor = 0f
        var prevEnd = Int.MIN_VALUE

        for ((index, block) in order) {
            val offset = when {
                prevEnd == Int.MIN_VALUE ->
                    // 首块：顶部空隙按线性比例。
                    (block.start - groupStart).coerceAtLeast(0) * scale
                block.start > prevEnd ->
                    // 时间有空隙：按比例显示，超过上限的部分压掉。
                    cursor + ((block.start - prevEnd) * scale).coerceAtMost(idleGapMaxDp)
                else ->
                    // 时间衔接：紧贴上一块的视觉底部，截断层被补偿掉。
                    cursor
            }

            marks += block.start to offset
            marks += block.end to offset + block.height
            slots += Slot(index, offset, block.height)
            cursor = offset + block.height
            prevEnd = block.end
        }

        return Layout(slots, cursor, marks)
    }

    /** [now] 在布局内的 y；时间在映射覆盖范围外时按端点钳制。 */
    fun yFor(layout: Layout, now: Int): Float? {
        val marks = layout.marks
        if (marks.isEmpty()) return null
        if (now <= marks.first().first) return marks.first().second
        for (i in 0 until marks.lastIndex) {
            val (t0, y0) = marks[i]
            val (t1, y1) = marks[i + 1]
            if (t1 <= t0) continue
            if (now in t0..t1) {
                return y0 + (now - t0).toFloat() / (t1 - t0) * (y1 - y0)
            }
        }
        return marks.last().second
    }
}