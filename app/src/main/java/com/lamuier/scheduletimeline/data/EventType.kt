package com.lamuier.scheduletimeline.data

/** 日程类型：演出 / 特典。 */
enum class EventType(val storage: String) {
    PERFORMANCE("PERFORMANCE"),
    TOKUTEN("TOKUTEN"),
    ;

    companion object {
        fun fromStorage(value: String): EventType =
            entries.find { it.storage == value } ?: PERFORMANCE
    }
}

/** 特典种类：前特 / 平特 / 终特。 */
enum class TokutenKind(val storage: String) {
    PRE("PRE"),
    PARALLEL("PARALLEL"),
    FINAL("FINAL"),
    ;

    companion object {
        fun fromStorage(value: String): TokutenKind? =
            entries.find { it.storage == value }
    }
}

object EventLabels {
    fun typeChip(event: ScheduleEvent): String {
        return when (EventType.fromStorage(event.eventType)) {
            EventType.PERFORMANCE -> "演出"
            EventType.TOKUTEN -> {
                val kind = when (TokutenKind.fromStorage(event.tokutenKind)) {
                    TokutenKind.PRE -> "前特"
                    TokutenKind.PARALLEL -> "平特"
                    TokutenKind.FINAL -> "终特"
                    null -> null
                }
                if (kind == null) "特典" else "特典·$kind"
            }
        }
    }

    fun displayLabel(event: ScheduleEvent): String {
        val team = event.teamDisplay.ifBlank { event.title }.ifBlank { "?" }
        return "$team${typeChip(event)}"
    }

    fun eventTypeStorageFromCsv(label: String): EventType = when (label.trim()) {
        "特典" -> EventType.TOKUTEN
        "演出" -> EventType.PERFORMANCE
        "舞台演出" -> EventType.PERFORMANCE
        else -> if (label.contains("特典")) EventType.TOKUTEN else EventType.PERFORMANCE
    }

    fun tokutenKindStorageFromCsv(label: String): String = when (label.trim()) {
        "前特" -> TokutenKind.PRE.storage
        "平特" -> TokutenKind.PARALLEL.storage
        "终特" -> TokutenKind.FINAL.storage
        else -> ""
    }

    fun eventTypeToCsv(type: EventType): String = when (type) {
        EventType.PERFORMANCE -> "演出"
        EventType.TOKUTEN -> "特典"
    }

    fun tokutenKindToCsv(kind: String): String = when (TokutenKind.fromStorage(kind)) {
        TokutenKind.PRE -> "前特"
        TokutenKind.PARALLEL -> "平特"
        TokutenKind.FINAL -> "终特"
        null -> ""
    }
}
