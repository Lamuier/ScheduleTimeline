package com.lamuier.scheduletimeline.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ScheduleRepository(
    private val database: ScheduleDatabase,
    private val eventDao: ScheduleEventDao = database.scheduleEventDao(),
    private val categoryDao: CategoryDao = database.categoryDao(),
) {
    @Volatile
    private var scheduleReady = false
    private val readyMutex = Mutex()

    fun observeDay(dayKey: String): Flow<List<ScheduleEvent>> = flow {
        ensureScheduleReady()
        emitAll(eventDao.observeByDay(dayKey))
    }

    fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll()

    suspend fun addCategory(name: String) = categoryDao.insert(Category(name))

    /** 删除团队候选，并从所有事件的团队集合中移除同名团队。 */
    suspend fun deleteTeam(name: String) {
        database.withTransaction {
            val changedEvents = eventDao.getAll().mapNotNull { event ->
                val nextTeam = TeamNames.encode(event.teamNames.filterNot { it == name })
                event.copy(team = nextTeam).takeIf { nextTeam != event.team }
            }
            if (changedEvents.isNotEmpty()) {
                eventDao.updateAll(changedEvents)
            }
            categoryDao.delete(Category(name))
        }
    }

    @Deprecated("Use deleteTeam", ReplaceWith("deleteTeam(name)"))
    suspend fun deleteCategory(name: String) = deleteTeam(name)

    suspend fun get(id: Long): ScheduleEvent? = eventDao.getById(id)

    suspend fun save(event: ScheduleEvent): Long {
        return database.withTransaction {
            val normalized = normalizeEvent(event)
            saveTeamCategories(listOf(normalized))
            val id = if (normalized.id == 0L) {
                eventDao.upsert(normalized)
            } else {
                eventDao.update(normalized)
                normalized.id
            }
            val saved = eventDao.getById(id) ?: normalized.copy(id = id)
            applyMerge(eventDao.getByDay(saved.dayKey))
            eventDao.getByDay(saved.dayKey)
                .firstOrNull { EventMerge.slotOf(it) == EventMerge.slotOf(saved) }
                ?.id
                ?: id
        }
    }

    suspend fun saveAll(events: List<ScheduleEvent>) {
        if (events.isEmpty()) return
        database.withTransaction {
            val normalized = events.map(::normalizeEvent)
            saveTeamCategories(normalized)
            eventDao.upsertAll(normalized)
            mergeDays(normalized.map { it.dayKey })
        }
    }

    /** 导入草稿；同日同时段同类型会合并成一条，演出 / 特典关联仍按团队名称计算。 */
    suspend fun importDrafts(drafts: List<ImportDraft>) {
        if (drafts.isEmpty()) return
        database.withTransaction {
            val events = drafts.map { normalizeEvent(it.event.copy(id = 0)) }
            saveTeamCategories(events)
            eventDao.upsertAll(events)
            mergeDays(events.map { it.dayKey })
        }
    }

    suspend fun delete(id: Long) {
        database.withTransaction {
            eventDao.clearLinkedPerformance(id)
            eventDao.deleteById(id)
        }
    }

    /** 仅特典可标记完成；完成后通知不再提醒该日程。 */
    suspend fun setTokutenCompleted(id: Long, completed: Boolean): Boolean {
        val event = eventDao.getById(id) ?: return false
        if (EventType.fromStorage(event.eventType) != EventType.TOKUTEN) return false
        if (event.completed == completed) return true
        eventDao.update(event.copy(completed = completed))
        return true
    }

    suspend fun clearAll() {
        database.withTransaction {
            eventDao.deleteAll()
            categoryDao.deleteAll()
        }
    }

    /** 清空单日日程；同时清理旧版本可能遗留的关联 id。 */
    suspend fun clearDay(dayKey: String) {
        database.withTransaction {
            val ids = eventDao.getByDay(dayKey).map { it.id }
            if (ids.isNotEmpty()) {
                eventDao.clearLinkedPerformances(ids)
                eventDao.deleteByDay(dayKey)
            }
        }
    }

    suspend fun eventsForDay(dayKey: String): List<ScheduleEvent> {
        ensureScheduleReady()
        return eventDao.getByDay(dayKey)
    }

    suspend fun allEvents(): List<ScheduleEvent> {
        ensureScheduleReady()
        return eventDao.getAll()
    }

    suspend fun distinctDayKeys(): List<String> = eventDao.distinctDayKeys()

    /** 从指定日期（含当日）起第一个有日程的 dayKey；无则 null。 */
    suspend fun firstDayKeyOnOrAfter(fromDayKey: String): String? =
        eventDao.firstDayKeyOnOrAfter(fromDayKey)

    /**
     * 遗留 dayKey 迁移，并合并已有的同时段同类型日程。
     * 时间轴、小组件和通知读数据前都会走到这里，避免先画出拆开的卡片。
     */
    suspend fun seedIfEmpty() {
        ensureScheduleReady()
    }

    private suspend fun ensureScheduleReady() {
        if (scheduleReady) return
        readyMutex.withLock {
            if (scheduleReady) return@withLock
            database.withTransaction {
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                eventDao.reassignDayKey("default", today)
                applyMerge(eventDao.getAll())
            }
            scheduleReady = true
        }
    }

    private suspend fun mergeDays(dayKeys: Collection<String>) {
        dayKeys.distinct().forEach { dayKey ->
            applyMerge(eventDao.getByDay(dayKey))
        }
    }

    private suspend fun applyMerge(events: List<ScheduleEvent>) {
        val plan = EventMerge.plan(events)
        if (plan.updates.isNotEmpty()) {
            saveTeamCategories(plan.updates)
            eventDao.updateAll(plan.updates)
        }
        if (plan.deleteIds.isNotEmpty()) {
            eventDao.clearLinkedPerformances(plan.deleteIds)
            eventDao.deleteByIds(plan.deleteIds)
        }
    }

    private fun normalizeEvent(event: ScheduleEvent): ScheduleEvent = event.copy(
        team = TeamNames.encode(event.teamNames),
        linkedPerformanceId = null,
    )

    private suspend fun saveTeamCategories(events: List<ScheduleEvent>) {
        val categories = events
            .flatMap { it.teamNames }
            .distinct()
            .map(::Category)
        if (categories.isNotEmpty()) {
            categoryDao.insertAll(categories)
        }
    }
}
