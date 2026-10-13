package com.lamuier.scheduletimeline.data

/** Text heights in dp measured by Compose, or deterministic test metrics. */
data class TimelineTextMetrics(val time: Float = 22f, val title: Float = 28f,
    val completed: Float = 22f, val subtitle: Float = 24f) {
    fun height(completed: Boolean, subtitle: Boolean, titles: Int, times: Int): Float {
        val children = listOf(times > 0, titles > 0, completed, subtitle).count { it }
        return 16f + time * times + title * titles + (if (completed) this.completed else 0f) +
            (if (subtitle) this.subtitle else 0f) + 6f * (children - 1).coerceAtLeast(0)
    }
    fun regularContentPlan(available: Float, subtitle: Boolean): LaneScale.CardContentPlan {
        for (titles in 3 downTo 1) for (show in if (subtitle) listOf(true, false) else listOf(false)) {
            val height = 32f + time + title * titles + (if (show) this.subtitle * 2 else 0f) +
                8f * (if (show) 2 else 1)
            if (height <= available + 0.5f) return LaneScale.CardContentPlan(1, titles, show)
        }
        return LaneScale.CardContentPlan(1, 1, false)
    }
    fun contentPlan(available: Float, completed: Boolean, subtitle: Boolean): LaneScale.CardContentPlan {
        for (titles in 4 downTo 1) for (times in 2 downTo 1) {
            for (show in if (subtitle) listOf(true, false) else listOf(false)) {
                if (height(completed, show, titles, times) <= available + 0.5f)
                    return LaneScale.CardContentPlan(times, titles, show)
            }
        }
        return LaneScale.CardContentPlan(1, 1, false)
    }
}

data class TimelineColumnPlan(val type: EventType, val eventOffset: Int, val eventCount: Int,
    val startFraction: Float, val widthFraction: Float)

data class TimelineLayoutPlan(val segments: List<LaneSegments.Segment>, val flow: LaneFlow.Layout,
    val columns: List<TimelineColumnPlan>) {
    fun yFor(minute: Int): Float? = LaneFlow.yFor(flow, minute)
    companion object {
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
}
