package com.lamuier.scheduletimeline.data

/**
 * 图形化时间轴的布局结果：事件被分配到第 [column] 列（共 [columns] 列），
 * 用于让时间上重叠的事件在水平方向并排显示而不互相遮挡。
 */
data class PositionedEvent(
    val item: TimelineItem.Event,
    val column: Int,
    val columns: Int,
)

/** 两个事件之间完全无安排的空闲区间。 */
data class FreeSlot(
    val startMinutes: Int,
    val endMinutes: Int,
) {
    val durationMinutes: Int get() = endMinutes - startMinutes
}

/** 当天日程的汇总统计。 */
data class DayStats(
    val eventCount: Int,
    val busyMinutes: Int,
    val freeMinutes: Int,
    val conflictCount: Int,
    val firstStart: Int,
    val lastEnd: Int,
)

object ChartLayout {

    /**
     * 经典日历分列算法：把时间上相互（传递）重叠的事件聚成一簇，
     * 簇内按开始时间贪心分配到最早空出来的列，簇的总列数决定每个事件的宽度。
     */
    fun position(events: List<TimelineItem.Event>): List<PositionedEvent> {
        if (events.isEmpty()) return emptyList()

        val sorted = events.sortedWith(
            compareBy({ it.startMinutes }, { it.endMinutes }),
        )

        val result = mutableListOf<PositionedEvent>()
        val cluster = mutableListOf<Pair<TimelineItem.Event, Int>>()
        val columnEnds = mutableListOf<Int>()
        var clusterEnd = Int.MIN_VALUE

        fun flushCluster() {
            val columns = columnEnds.size
            cluster.forEach { (event, column) ->
                result += PositionedEvent(event, column, columns)
            }
            cluster.clear()
            columnEnds.clear()
        }

        sorted.forEach { event ->
            if (cluster.isNotEmpty() && event.startMinutes >= clusterEnd) {
                flushCluster()
            }
            var column = columnEnds.indexOfFirst { it <= event.startMinutes }
            if (column == -1) {
                column = columnEnds.size
                columnEnds += event.endMinutes
            } else {
                columnEnds[column] = event.endMinutes
            }
            cluster += event to column
            clusterEnd = maxOf(clusterEnd, event.endMinutes)
        }
        flushCluster()

        return result
    }

    /** 第一个事件开始到最后一个事件结束之间，完全没有任何安排的区间。 */
    fun freeSlots(events: List<ScheduleEvent>): List<FreeSlot> {
        val merged = mergedBusyIntervals(events)
        if (merged.size < 2) return emptyList()
        return merged.zipWithNext().mapNotNull { (a, b) ->
            if (b.first > a.second) FreeSlot(a.second, b.first) else null
        }
    }

    fun stats(events: List<ScheduleEvent>): DayStats? {
        if (events.isEmpty()) return null
        val merged = mergedBusyIntervals(events)
        val busy = merged.sumOf { it.second - it.first }
        val first = merged.first().first
        val last = merged.last().second

        var conflicts = 0
        val sorted = events.sortedBy { it.startMinutes }
        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                if (sorted[j].startMinutes >= sorted[i].endMinutes) break
                conflicts++
            }
        }

        return DayStats(
            eventCount = events.size,
            busyMinutes = busy,
            freeMinutes = (last - first) - busy,
            conflictCount = conflicts,
            firstStart = first,
            lastEnd = last,
        )
    }

    /** 合并后的忙碌区间（升序、互不重叠）。 */
    private fun mergedBusyIntervals(events: List<ScheduleEvent>): List<Pair<Int, Int>> {
        if (events.isEmpty()) return emptyList()
        val sorted = events.sortedBy { it.startMinutes }
        val merged = mutableListOf(sorted.first().startMinutes to sorted.first().endMinutes)
        sorted.drop(1).forEach { event ->
            val last = merged.last()
            if (event.startMinutes <= last.second) {
                merged[merged.lastIndex] = last.first to maxOf(last.second, event.endMinutes)
            } else {
                merged += event.startMinutes to event.endMinutes
            }
        }
        return merged
    }
}
