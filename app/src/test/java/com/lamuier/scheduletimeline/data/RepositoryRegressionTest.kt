package com.lamuier.scheduletimeline.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
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
