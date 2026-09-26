package com.lamuier.scheduletimeline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaneSegmentsTest {

    @Test
    fun slice_keepsExclusiveHeadAndTailAroundTheOverlap() {
        val segments = LaneSegments.slice(
            listOf(
                13 * 60 to 14 * 60 + 30,
                13 * 60 + 30 to 15 * 60,
            ),
        )

        assertEquals(3, segments.size)
        val head = segments[0] as LaneSegments.Segment.Single
        assertEquals(0, head.index)
        assertEquals(13 * 60, head.startMinutes)
        assertEquals(13 * 60 + 30, head.endMinutes)

        val overlap = segments[1] as LaneSegments.Segment.Overlap
        assertEquals(listOf(0, 1), overlap.indices)
        assertEquals(13 * 60 + 30, overlap.startMinutes)
        assertEquals(14 * 60 + 30, overlap.endMinutes)

        val tail = segments[2] as LaneSegments.Segment.Single
        assertEquals(1, tail.index)
        assertEquals(14 * 60 + 30, tail.startMinutes)
        assertEquals(15 * 60, tail.endMinutes)
    }

    @Test
    fun slice_leavesNonOverlappingEventsIntact() {
        val segments = LaneSegments.slice(
            listOf(
                12 * 60 + 45 to 13 * 60 + 10,
                14 * 60 to 14 * 60 + 25,
            ),
        )

        assertEquals(2, segments.size)
        assertTrue(segments.all { it is LaneSegments.Segment.Single })
        assertEquals(12 * 60 + 45, segments[0].startMinutes)
        assertEquals(13 * 60 + 10, segments[0].endMinutes)
        assertEquals(14 * 60, segments[1].startMinutes)
        assertEquals(14 * 60 + 25, segments[1].endMinutes)
    }

    @Test
    fun slice_touchingEndpointsAreNotAnOverlap() {
        val segments = LaneSegments.slice(
            listOf(
                13 * 60 to 14 * 60,
                14 * 60 to 15 * 60,
            ),
        )

        assertEquals(2, segments.size)
        assertTrue(segments.all { it is LaneSegments.Segment.Single })
    }

    @Test
    fun slice_splitsAContainedEventOutOfTheMiddle() {
        val segments = LaneSegments.slice(
            listOf(
                13 * 60 to 15 * 60,
                13 * 60 + 30 to 14 * 60 + 30,
            ),
        )

        assertEquals(3, segments.size)
        assertTrue(segments[0] is LaneSegments.Segment.Single)
        assertTrue(segments[1] is LaneSegments.Segment.Overlap)
        assertTrue(segments[2] is LaneSegments.Segment.Single)
        assertEquals(0, (segments[0] as LaneSegments.Segment.Single).index)
        assertEquals(0, (segments[2] as LaneSegments.Segment.Single).index)
    }

    @Test
    fun slice_emptyInput_returnsEmpty() {
        assertTrue(LaneSegments.slice(emptyList()).isEmpty())
    }
}
