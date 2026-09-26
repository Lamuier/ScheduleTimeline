package com.lamuier.scheduletimeline.data

/**
 * 把同一条轨道切成互不重叠的时间片。
 * 只有一段里同时落着多条日程时，才标成重叠片；前后只属于一条的部分保持独立。
 * 首尾相接不算重叠。
 */
object LaneSegments {
    sealed class Segment {
        abstract val startMinutes: Int
        abstract val endMinutes: Int

        data class Single(
            val index: Int,
            override val startMinutes: Int,
            override val endMinutes: Int,
        ) : Segment()

        data class Overlap(
            val indices: List<Int>,
            override val startMinutes: Int,
            override val endMinutes: Int,
        ) : Segment()
    }

    fun slice(ranges: List<Pair<Int, Int>>): List<Segment> {
        if (ranges.isEmpty()) return emptyList()
        val points = ranges.flatMap { listOf(it.first, it.second) }.distinct().sorted()
        val raw = mutableListOf<Segment>()
        for (i in 0 until points.lastIndex) {
            val start = points[i]
            val end = points[i + 1]
            if (end <= start) continue
            val covered = ranges.indices.filter { ranges[it].first < end && ranges[it].second > start }
            when {
                covered.isEmpty() -> Unit
                covered.size == 1 -> raw += Segment.Single(covered[0], start, end)
                else -> raw += Segment.Overlap(covered, start, end)
            }
        }
        return mergeAdjacent(raw)
    }

    private fun mergeAdjacent(raw: List<Segment>): List<Segment> {
        if (raw.isEmpty()) return raw
        val merged = mutableListOf<Segment>()
        for (segment in raw) {
            val previous = merged.lastOrNull()
            val combined = if (previous != null && previous.endMinutes == segment.startMinutes) {
                combine(previous, segment)
            } else {
                null
            }
            if (combined != null) {
                merged[merged.lastIndex] = combined
            } else {
                merged += segment
            }
        }
        return merged
    }

    private fun combine(previous: Segment, next: Segment): Segment? = when {
        previous is Segment.Single && next is Segment.Single && previous.index == next.index ->
            previous.copy(endMinutes = next.endMinutes)
        previous is Segment.Overlap && next is Segment.Overlap && previous.indices == next.indices ->
            previous.copy(endMinutes = next.endMinutes)
        else -> null
    }
}
