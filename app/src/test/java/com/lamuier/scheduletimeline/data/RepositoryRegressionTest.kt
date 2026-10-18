package com.lamuier.scheduletimeline.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RepositoryRegressionTest {
    private lateinit var database: ScheduleDatabase
    private lateinit var repository: ScheduleRepository
    private fun event(team: String = "A") = ScheduleEvent(team = team, dayKey = "2026-10-20", startMinutes = 600, endMinutes = 660)
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ScheduleDatabase::class.java).build()
        repository = ScheduleRepository(database)
    }
    @After fun close() { database.close() }

    @Test fun equalDayRowsAreSuppressedBeforeConsumersPrepareTimeline() = runBlocking {
        val row = event().copy(id = 1)
        val changed = row.copy(note = "changed")
        val realDao = database.scheduleEventDao()
        val dao = object : ScheduleEventDao by realDao {
            override fun observeByDay(dayKey: String) = flowOf(listOf(row), listOf(row.copy()), listOf(changed))
        }
        val emissions = ScheduleRepository(database, dao).observeDay(row.dayKey).toList()
        assertEquals(listOf(listOf(row), listOf(changed)), emissions)
    }

    @Test fun candidateQueryMatchesFullMergeIncludingLegacyDefaultsAndUnknownTypes() = runBlocking {
        val dao = database.scheduleEventDao()
        val today = java.time.LocalDate.now().toString()
        val rows = (1..200).map { id -> event("Team$id").copy(id = id.toLong(),
            dayKey = if (id < 15) "default" else today,
            startMinutes = (id % 30) * 20, endMinutes = (id % 30) * 20 + 10,
            eventType = listOf("PERFORMANCE", "TOKUTEN", "unknown")[id % 3],
            tokutenKind = listOf("", "PRE", "unknown", "PARALLEL")[id % 4],
            completed = id % 5 == 0, linkedPerformanceId = if (id % 7 == 0) 1L else null,
            note = "note$id") } + event("invalid").copy(id = 201, dayKey = today,
                startMinutes = 20, endMinutes = 10)
        dao.upsertAll(rows)
        val normalized = rows.map { if (it.dayKey == "default") it.copy(dayKey = today) else it }
        val plan = EventMerge.plan(normalized)
        val updates = plan.updates.associateBy { it.id }
        val expected = normalized.filterNot { it.id in plan.deleteIds }.map {
            val updated = updates[it.id] ?: it
            if (updated.linkedPerformanceId in plan.deleteIds) updated.copy(linkedPerformanceId = null) else updated
        }.sortedBy { it.id }
        repository.seedIfEmpty()
        assertEquals(expected, dao.getAll().sortedBy { it.id })
        assertTrue(dao.getMergeCandidates().all { candidate ->
            expected.count { it.dayKey == candidate.dayKey && it.startMinutes == candidate.startMinutes && it.endMinutes == candidate.endMinutes } > 1
        })
        // A fresh repository must still repair a restored or externally populated database.
        dao.upsert(expected.first().copy(id = 0, team = "restored"))
        ScheduleRepository(database).seedIfEmpty()
        assertTrue(EventMerge.plan(dao.getAll()).isEmpty)
    }

    @Test fun invalidBatchRollsBackEventsAndTeamCandidates() = runBlocking {
        try {
            repository.importDrafts(listOf(ImportDraft(event()), ImportDraft(event("B").copy(endMinutes = 0))))
            fail("Invalid rows must fail the whole transaction")
        } catch (_: IllegalArgumentException) { }
        assertEquals(0, database.scheduleEventDao().count())
        assertTrue(database.categoryDao().observeAll().first().isEmpty())
    }

    @Test fun actualSqlFailureRollsBackClearAndImport() = runBlocking {
        val id = repository.save(event())
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER prevent_delete BEFORE DELETE ON schedule_events BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { repository.clearAll(); fail("Expected SQL failure") } catch (_: Exception) { }
        assertNotNull(repository.get(id))
        try { repository.importDrafts(listOf(ImportDraft(event("B")))); fail("Merge delete must fail") } catch (_: Exception) { }
        assertEquals(1, database.scheduleEventDao().count())
        assertEquals(listOf("A"), database.categoryDao().observeAll().first().map { it.name })
    }

    @Test fun completionIsAtomicAndStaleEditCannotUndoIt() = runBlocking {
        val id = repository.save(event().copy(eventType = EventType.TOKUTEN.storage, tokutenKind = TokutenKind.PARALLEL.storage))
        val stale = repository.get(id)!!
        repository.setTokutenCompleted(id, true)
        repository.save(stale.copy(note = "new edit"))
        assertEquals("new edit", repository.get(id)!!.note)
        assertTrue(repository.get(id)!!.completed)
        assertFalse(repository.setTokutenCompleted(repository.save(event().copy(startMinutes = 700, endMinutes = 720)), true))
    }

    @Test fun repeatedImportIsStableAndDeletingTeamUpdatesEveryEvent() = runBlocking {
        val a = event().copy(title = "一场", note = "原始\n备注")
        val b = event("B").copy(title = "二场", note = "新备注")
        repeat(3) { repository.importDrafts(listOf(ImportDraft(a), ImportDraft(b))) }
        val merged = repository.eventsForDay(a.dayKey).single()
        assertEquals("一场 / 二场", merged.title)
        assertEquals("原始\n备注\n新备注", merged.note)
        repository.deleteTeam("B")
        assertEquals(listOf("A"), repository.get(merged.id)!!.teamNames)
    }
}
