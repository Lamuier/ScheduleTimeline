package com.lamuier.scheduletimeline.data

object SampleData {
    /** 示例日程；演出 / 特典按同日团队名称自动关联。 */
    suspend fun seedInto(dayKey: String, insert: suspend (ScheduleEvent) -> Long) {
        insert(
            ScheduleEvent(
                team = "StarDiary",
                eventType = EventType.PERFORMANCE.storage,
                startMinutes = 14 * 60 + 20,
                endMinutes = 14 * 60 + 40,
                dayKey = dayKey,
            ),
        )
        insert(
            ScheduleEvent(
                team = "银烁花火",
                eventType = EventType.TOKUTEN.storage,
                tokutenKind = TokutenKind.PARALLEL.storage,
                location = "昨日世界酒馆C区",
                startMinutes = 14 * 60 + 50,
                endMinutes = 16 * 60 + 50,
                dayKey = dayKey,
            ),
        )
        insert(
            ScheduleEvent(
                team = "StarDiary",
                eventType = EventType.TOKUTEN.storage,
                tokutenKind = TokutenKind.PARALLEL.storage,
                location = "声音共和吧台A区",
                startMinutes = 17 * 60,
                endMinutes = 19 * 60,
                dayKey = dayKey,
            ),
        )
        insert(
            ScheduleEvent(
                team = "StarCandy",
                eventType = EventType.PERFORMANCE.storage,
                startMinutes = 17 * 60 + 5,
                endMinutes = 17 * 60 + 25,
                dayKey = dayKey,
            ),
        )
        insert(
            ScheduleEvent(
                team = TeamNames.encode(listOf("StarCandy", "ReaLume")),
                eventType = EventType.TOKUTEN.storage,
                tokutenKind = TokutenKind.FINAL.storage,
                location = "场内",
                startMinutes = 20 * 60 + 45,
                endMinutes = 22 * 60 + 30,
                note = "可与 ReaLume 交叉",
                dayKey = dayKey,
            ),
        )
    }
}
