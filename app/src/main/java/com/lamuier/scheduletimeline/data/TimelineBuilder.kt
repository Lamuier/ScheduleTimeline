package com.lamuier.scheduletimeline.data

sealed class TimelineItem {
    abstract val startMinutes: Int
    abstract val endMinutes: Int

    data class Event(
        val event: ScheduleEvent,
        /** 用户手动填写的备注，独立于自动冲突提示展示。 */
        val note: String? = null,
        val overlapNotes: List<String> = emptyList(),
    ) : TimelineItem() {
        override val startMinutes: Int get() = event.startMinutes
        override val endMinutes: Int get() = event.endMinutes
    }

    data class OverlapGroup(
        val events: List<Event>,
    ) : TimelineItem() {
        /** 演出固定在左侧时间轴列。 */
        val performanceEvents: List<Event>
            get() = events.filter {
                EventType.fromStorage(it.event.eventType) == EventType.PERFORMANCE
            }

        /** 特典固定在右侧时间轴列。 */
        val tokutenEvents: List<Event>
            get() = events.filter {
                EventType.fromStorage(it.event.eventType) == EventType.TOKUTEN
            }

        override val startMinutes: Int get() = events.minOf { it.startMinutes }
        override val endMinutes: Int get() = events.maxOf { it.endMinutes }
    }

    data class Gap(
        override val startMinutes: Int,
        override val endMinutes: Int,
        val ongoingNotes: List<String> = emptyList(),
    ) : TimelineItem()
}

object TimelineBuilder {
    /**
     * Events sorted by start time. Free-time gaps are inserted when a previous
     * event ends before the next begins. Each cluster contains all transitively
     * overlapping events, so the interval between clusters is entirely free.
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
                items += TimelineItem.Gap(previousEnd, clusterStart)
            }
            val labels = eventsInCluster.map { EventLabels.displayLabel(it) }
            val eventItems = eventsInCluster.map { event ->
                TimelineItem.Event(
                    event = event,
                    note = event.note.takeIf { it.isNotBlank() },
                    overlapNotes = overlapNotesFor(event, eventsInCluster, labels),
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

    /** 仅自动生成的时间冲突提示；用户备注走 [TimelineItem.Event.note] 单独展示。 */
    private fun overlapNotesFor(
        event: ScheduleEvent,
        all: List<ScheduleEvent>,
        labels: List<String>,
    ): List<String> = buildSet {
        all.forEachIndexed { index, other ->
            if (other.id != event.id && overlaps(event, other)) add("与${labels[index]}重叠")
        }
    }.toList()

    private fun overlaps(a: ScheduleEvent, b: ScheduleEvent): Boolean {
        return a.startMinutes < b.endMinutes && b.startMinutes < a.endMinutes
    }
}
