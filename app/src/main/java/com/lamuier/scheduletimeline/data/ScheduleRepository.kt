package com.lamuier.scheduletimeline.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ScheduleRepository(
    private val database: ScheduleDatabase,
    private val eventDao: ScheduleEventDao = database.scheduleEventDao(),
    private val categoryDao: CategoryDao = database.categoryDao(),
) {
    fun observeDay(dayKey: String): Flow<List<ScheduleEvent>> = eventDao.observeByDay(dayKey)

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
            if (normalized.id == 0L) {
                eventDao.upsert(normalized)
            } else {
                eventDao.update(normalized)
                normalized.id
            }
        }
    }

    suspend fun saveAll(events: List<ScheduleEvent>) {
        if (events.isEmpty()) return
        database.withTransaction {
            val normalized = events.map(::normalizeEvent)
            saveTeamCategories(normalized)
            eventDao.upsertAll(normalized)
        }
    }

    /** 导入草稿；演出 / 特典关联由同日团队名称交集自动计算。 */
    suspend fun importDrafts(drafts: List<ImportDraft>) {
        if (drafts.isEmpty()) return
        database.withTransaction {
            val events = drafts.map { normalizeEvent(it.event.copy(id = 0)) }
            saveTeamCategories(events)
            eventDao.upsertAll(events)
        }
    }

    suspend fun delete(id: Long) {
        database.withTransaction {
            eventDao.clearLinkedPerformance(id)
            eventDao.deleteById(id)
        }
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

    suspend fun eventsForDay(dayKey: String): List<ScheduleEvent> = eventDao.getByDay(dayKey)

    suspend fun allEvents(): List<ScheduleEvent> = eventDao.getAll()

    suspend fun distinctDayKeys(): List<String> = eventDao.distinctDayKeys()

    /** 仅做 dayKey 遗留迁移；不写入样例日程。 */
    suspend fun seedIfEmpty() {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        eventDao.reassignDayKey("default", today)
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
