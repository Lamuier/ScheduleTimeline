package com.lamuier.scheduletimeline.data

/**
 * 同一条时间轨道里，把互相重叠的日程分到并排的栏，避免卡片叠在一起盖住点击。
 * 首尾相接不算重叠，可以共用一栏。不与任何日程重叠的卡片会横跨空出来的栏，尽量用满列宽。
 */
object LanePlacement {
    data class Slot(
        val column: Int,
        val span: Int,
        val columnCount: Int,
    )

    fun place(ranges: List<Pair<Int, Int>>): List<Slot> {
        if (ranges.isEmpty()) return emptyList()

        val columnOf = IntArray(ranges.size)
        val columnEnds = mutableListOf<Int>()
        val order = ranges.indices.sortedWith(
            compareBy<Int> { ranges[it].first }
                .thenBy { ranges[it].second }
                .thenBy { it },
        )
        for (index in order) {
            val (start, end) = ranges[index]
            val reusable = columnEnds.indexOfFirst { it <= start }
            val column = if (reusable >= 0) {
                columnEnds[reusable] = end
                reusable
            } else {
                columnEnds += end
                columnEnds.lastIndex
            }
            columnOf[index] = column
        }

        val columnCount = columnEnds.size
        return List(ranges.size) { index ->
            val range = ranges[index]
            val column = columnOf[index]
            var span = 1
            while (column + span < columnCount && !columnBlocked(ranges, columnOf, index, column + span)) {
                span++
            }
            Slot(column = column, span = span, columnCount = columnCount)
        }
    }

    private fun columnBlocked(
        ranges: List<Pair<Int, Int>>,
        columnOf: IntArray,
        index: Int,
        column: Int,
    ): Boolean {
        val range = ranges[index]
        return ranges.indices.any { other ->
            other != index &&
                columnOf[other] == column &&
                range.first < ranges[other].second &&
                ranges[other].first < range.second
        }
    }
}
