package com.lamuier.scheduletimeline.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "schedule_events",
    indices = [
        Index(value = ["dayKey"]),
        Index(value = ["linkedPerformanceId"]),
        Index(value = ["team"]),
    ],
)
data class ScheduleEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 可选场次说明（如午场、加演）。 */
    val title: String = "",
    /** 遗留分类列；新建恒为空，仅兼容旧数据。 */
    val category: String = "",
    val location: String = "",
    /** Minutes from midnight, inclusive. */
    val startMinutes: Int,
    /** Minutes from midnight; must be > start. */
    val endMinutes: Int,
    val note: String = "",
    val dayKey: String = "default",
    /** 团队名集合，由 [TeamNames] 编解码；旧数据仍是单个名称。 */
    val team: String = "",
    /** [EventType.storage] */
    val eventType: String = EventType.PERFORMANCE.storage,
    /** [TokutenKind.storage]；非特典为空。 */
    val tokutenKind: String = "",
    /** 遗留关联字段；新逻辑按同日团队名称自动关联，新写入恒为 null。 */
    val linkedPerformanceId: Long? = null,
)
