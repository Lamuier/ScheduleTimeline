package com.lamuier.scheduletimeline.data

/**
 * 同一天、起止时间完全相同、类型相同的日程合并成一条。
 *
 * 类型包含演出 / 特典，特典再按前特 / 平特 / 终特区分。
 * 团队名按原记录 id 顺序去重合并；地点、场次说明、备注保留彼此不同的内容。
 * 保留 id 最小的那条，避免通知和小组件指向被删掉的记录。
 */
object EventMerge {
    data class Slot(
        val dayKey: String,
        val startMinutes: Int,
        val endMinutes: Int,
        val eventType: String,
        val tokutenKind: String,
    )

    data class Plan(
        val updates: List<ScheduleEvent> = emptyList(),
        val deleteIds: List<Long> = emptyList(),
    ) {
        val isEmpty: Boolean get() = updates.isEmpty() && deleteIds.isEmpty()
    }

    fun slotOf(event: ScheduleEvent): Slot {
        val type = EventType.fromStorage(event.eventType)
        val kind = if (type == EventType.TOKUTEN) {
            TokutenKind.fromStorage(event.tokutenKind)?.storage.orEmpty()
        } else {
            ""
        }
        return Slot(
            dayKey = event.dayKey,
            startMinutes = event.startMinutes,
            endMinutes = event.endMinutes,
            eventType = type.storage,
            tokutenKind = kind,
        )
    }

    fun plan(events: List<ScheduleEvent>): Plan {
        if (events.size < 2) return Plan()

        val updates = mutableListOf<ScheduleEvent>()
        val deleteIds = mutableListOf<Long>()
        events.groupBy(::slotOf).values.forEach { group ->
            if (group.size < 2) return@forEach
            val ordered = group.sortedWith(compareBy<ScheduleEvent> { it.id == 0L }.thenBy { it.id })
            val keeper = ordered.first()
            val merged = mergeGroup(keeper, ordered)
            if (merged != keeper) updates += merged
            deleteIds += ordered.drop(1).map { it.id }.filter { it > 0L }
        }
        return Plan(updates, deleteIds)
    }

    private fun mergeGroup(keeper: ScheduleEvent, ordered: List<ScheduleEvent>): ScheduleEvent {
        val slot = slotOf(keeper)
        val type = EventType.fromStorage(slot.eventType)
        val teams = TeamNames.normalize(ordered.flatMap(::namesOf))
        val teamSet = teams.toSet()
        val titles = ordered
            .map { it.title.trim() }
            .filter { it.isNotEmpty() && it !in teamSet }
            .distinct()
        val locations = ordered.map { it.location.trim() }.filter { it.isNotEmpty() }.distinct()
        val notes = ordered.map { it.note.trim() }.filter { it.isNotEmpty() }.distinct()
        val category = ordered.firstOrNull { it.category.isNotBlank() }?.category.orEmpty()
        return keeper.copy(
            team = TeamNames.encode(teams),
            eventType = type.storage,
            tokutenKind = slot.tokutenKind,
            title = titles.joinToString(" / "),
            location = locations.joinToString(" / "),
            note = notes.joinToString("\n"),
            category = category,
            linkedPerformanceId = null,
            completed = type == EventType.TOKUTEN && ordered.all { it.completed },
        )
    }

    /** 没有团队名时，用场次说明兜底，避免旧数据合并后名字丢失。 */
    private fun namesOf(event: ScheduleEvent): List<String> {
        val teams = event.teamNames
        if (teams.isNotEmpty()) return teams
        val title = event.title.trim()
        return if (title.isEmpty()) emptyList() else listOf(title)
    }
}
