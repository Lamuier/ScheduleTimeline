package com.lamuier.scheduletimeline.data

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class TimelinePerformanceRegressionTest {
    @Test fun optimizedAlgorithmsMatchPublishedBehaviorAcrossShapesAndInvalidRows() {
        val random = Random(1141)
        repeat(250) { round ->
            val events = List(random.nextInt(1, 55)) { index ->
                val start = random.nextInt(0, 1440)
                val end = when (round % 4) {
                    0 -> (start + random.nextInt(1, 8)).coerceAtMost(1439)
                    1 -> 1300
                    else -> random.nextInt(0, 1440)
                }
                ScheduleEvent(id = random.nextLong(0, 25), team = "Team${index % 7}",
                    title = "Title${index % 3}", note = if (index % 4 == 0) "note" else "",
                    dayKey = "2026-10-15", startMinutes = start, endMinutes = end,
                    eventType = listOf("PERFORMANCE", "TOKUTEN", "legacy")[index % 3],
                    completed = index % 5 == 0)
            }.shuffled(random)
            assertEquals("timeline round=$round", ReferenceTimelineBuilder.build(events), TimelineBuilder.build(events))
            assertEquals("stats round=$round", ReferenceChartLayout.stats(events), ChartLayout.stats(events))
            val items = events.filter { it.hasValidTime }.map { TimelineItem.Event(it) }
            if (items.isNotEmpty()) {
                val group = TimelineItem.OverlapGroup(items)
                val scale = listOf(1f, 1.5f, 2f, 3f)[round % 4]
                val metrics = TimelineTextMetrics(22f * scale, 28f * scale, 22f * scale, 24f * scale)
                val expected = ReferenceLayoutPlan.build(group, metrics)
                val actual = TimelineLayoutPlan.build(group, metrics)
                assertEquals("layout round=$round", expected, actual)
                for (minute in group.startMinutes..group.endMinutes step 7) {
                    assertEquals(expected.yFor(minute), actual.yFor(minute))
                }
            }
        }
    }

    @Test fun denseLayoutAndTouchingEventsRemainExact() {
        val events = (0 until 100).map { i -> ScheduleEvent(id = i.toLong(), team = "Team$i",
            startMinutes = 480 + i, endMinutes = 900 + i, dayKey = "2026-10-15",
            eventType = EventType.entries[i % 2].storage) }
        val group = TimelineItem.OverlapGroup(events.map { TimelineItem.Event(it) })
        assertEquals(ReferenceLayoutPlan.build(group, TimelineTextMetrics()), TimelineLayoutPlan.build(group, TimelineTextMetrics()))
        val touching = events.take(20).mapIndexed { i, event -> event.copy(startMinutes = i * 10, endMinutes = (i + 1) * 10) }
        assertEquals(0, ChartLayout.stats(touching)!!.conflictCount)
        assertTrue(TimelineBuilder.build(touching).all { it is TimelineItem.Event })
    }
}

// Frozen pre-optimization implementations serve as the behavior oracle.
private object ReferenceTimelineBuilder {
    /**
     * Events sorted by start time. Free-time gaps are inserted when a previous
     * event ends before the next begins. Gaps are split at boundaries of any
     * overlapping events so "仍在进行" notes only cover the true overlap.
     */
    fun build(events: List<ScheduleEvent>): List<TimelineItem> {
        if (events.isEmpty()) return emptyList()

        val sorted = events.filter { it.hasValidTime }.sortedWith(
            compareBy<ScheduleEvent> { it.startMinutes }
                .thenBy { it.endMinutes }
                .thenBy { it.id },
        )

        if (sorted.isEmpty()) return emptyList()

        val clusters = mutableListOf<List<ScheduleEvent>>()
        var cluster = mutableListOf<ScheduleEvent>()
        var clusterEnd = Int.MIN_VALUE
        sorted.forEach { event ->
            if (cluster.isNotEmpty() && event.startMinutes >= clusterEnd) {
                clusters += cluster
                cluster = mutableListOf()
                clusterEnd = Int.MIN_VALUE
            }
            cluster += event
            clusterEnd = maxOf(clusterEnd, event.endMinutes)
        }
        if (cluster.isNotEmpty()) clusters += cluster

        val items = mutableListOf<TimelineItem>()
        var previousEnd = sorted.first().startMinutes
        clusters.forEach { eventsInCluster ->
            val clusterStart = eventsInCluster.first().startMinutes
            if (clusterStart > previousEnd) {
                items += gapsBetween(previousEnd, clusterStart, sorted)
            }
            val eventItems = eventsInCluster.map { event ->
                TimelineItem.Event(
                    event = event,
                    note = event.note.takeIf { it.isNotBlank() },
                    overlapNotes = overlapNotesFor(event, sorted),
                )
            }
            if (shouldShowParallel(eventsInCluster)) {
                items += TimelineItem.OverlapGroup(eventItems)
            } else {
                items += eventItems
            }
            previousEnd = maxOf(previousEnd, eventsInCluster.maxOf { it.endMinutes })
        }

        return items
    }

    private fun shouldShowParallel(events: List<ScheduleEvent>): Boolean {
        return events.size > 1
    }

    private fun gapsBetween(
        start: Int,
        end: Int,
        all: List<ScheduleEvent>,
    ): List<TimelineItem.Gap> {
        if (end <= start) return emptyList()

        val points = (
            all.flatMap { listOf(it.startMinutes, it.endMinutes) } + start + end
            )
            .filter { it in start..end }
            .distinct()
            .sorted()

        return points.zipWithNext().mapNotNull { (a, b) ->
            if (b <= a) return@mapNotNull null
            val ongoing = all
                .filter { it.startMinutes < b && it.endMinutes > a }
                .map(::displayLabel)
                .distinct()
                .map { "${it}仍在进行" }
            TimelineItem.Gap(
                startMinutes = a,
                endMinutes = b,
                ongoingNotes = ongoing,
            )
        }
    }

    /** 仅自动生成的时间冲突提示；用户备注走 [TimelineItem.Event.note] 单独展示。 */
    private fun overlapNotesFor(
        event: ScheduleEvent,
        all: List<ScheduleEvent>,
    ): List<String> {
        return all
            .filter { it.id != event.id && overlaps(event, it) }
            .map { "与${displayLabel(it)}重叠" }
            .distinct()
    }

    private fun overlaps(a: ScheduleEvent, b: ScheduleEvent): Boolean {
        return a.startMinutes < b.endMinutes && b.startMinutes < a.endMinutes
    }

    private fun displayLabel(event: ScheduleEvent): String = EventLabels.displayLabel(event)
}

private object ReferenceChartLayout {

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
        val valid = events.filter { it.hasValidTime }
        if (valid.isEmpty()) return null
        val merged = mergedBusyIntervals(valid)
        val busy = merged.sumOf { it.second - it.first }
        val first = merged.first().first
        val last = merged.last().second

        var conflicts = 0
        val sorted = valid.sortedBy { it.startMinutes }
        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                if (sorted[j].startMinutes >= sorted[i].endMinutes) break
                conflicts++
            }
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

    /** 合并后的忙碌区间（升序、互不重叠）。 */
    private fun mergedBusyIntervals(events: List<ScheduleEvent>): List<Pair<Int, Int>> {
        val valid = events.filter { it.hasValidTime }
        if (valid.isEmpty()) return emptyList()
        val sorted = valid.sortedBy { it.startMinutes }
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

private object ReferenceLayoutPlan {
        fun build(group: TimelineItem.OverlapGroup, metrics: TimelineTextMetrics): TimelineLayoutPlan {
            val ordered = group.performanceEvents + group.tokutenEvents
            val segments = LaneSegments.slice(ordered.map { it.startMinutes to it.endMinutes })
            val blocks = segments.map { segment ->
                val indices = when (segment) {
                    is LaneSegments.Segment.Single -> listOf(segment.index)
                    is LaneSegments.Segment.Overlap -> segment.indices
                }
                val needs = listOf(indices.filter { it < group.performanceEvents.size },
                    indices.filter { it >= group.performanceEvents.size }).map { lane ->
                    when (lane.size) {
                        0 -> 0f
                        1 -> metrics.height(ordered[lane[0]].event.completed, false, 1, 1) + 12f
                        else -> 16f + metrics.time + 12f + lane.size * (24f + metrics.title * 2) + 4f
                    }
                }
                LaneFlow.LaneBlock(segment.startMinutes, segment.endMinutes,
                    maxOf((segment.endMinutes - segment.startMinutes) * 4f, needs.max()).coerceAtMost(320f))
            }
            val mixed = group.performanceEvents.isNotEmpty() && group.tokutenEvents.isNotEmpty()
            val width = if (mixed) 0.5f else 1f
            val columns = buildList {
                if (group.performanceEvents.isNotEmpty()) add(TimelineColumnPlan(EventType.PERFORMANCE,
                    0, group.performanceEvents.size, 0f, width))
                if (group.tokutenEvents.isNotEmpty()) add(TimelineColumnPlan(EventType.TOKUTEN,
                    group.performanceEvents.size, group.tokutenEvents.size, if (mixed) 0.5f else 0f, width))
            }
            return TimelineLayoutPlan(segments, LaneFlow.place(blocks, group.startMinutes, 4f, 80f), columns)
        }
}
