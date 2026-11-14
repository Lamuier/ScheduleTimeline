package com.lamuier.scheduletimeline.data

import java.util.PriorityQueue

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

    fun stats(events: List<ScheduleEvent>): DayStats? {
        val valid = events.filter { it.hasValidTime }
        if (valid.isEmpty()) return null
        val sorted = valid.sortedBy { it.startMinutes }
        val merged = mergeSortedIntervals(sorted)
        val busy = merged.sumOf { it.second - it.first }
        val first = merged.first().first
        val last = merged.last().second

        var conflicts = 0
        val activeEnds = PriorityQueue<Int>()
        for (event in sorted) {
            while (activeEnds.isNotEmpty() && activeEnds.peek()!! <= event.startMinutes) activeEnds.remove()
            conflicts += activeEnds.size
            activeEnds.add(event.endMinutes)
        }

        return DayStats(
            eventCount = valid.size,
            busyMinutes = busy,
            freeMinutes = (last - first) - busy,
            conflictCount = conflicts,
            firstStart = first,
            lastEnd = last,
        )
    }

    private fun mergeSortedIntervals(sorted: List<ScheduleEvent>): List<Pair<Int, Int>> {
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
