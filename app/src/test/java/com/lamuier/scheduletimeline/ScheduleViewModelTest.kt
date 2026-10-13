package com.lamuier.scheduletimeline

import android.os.Bundle
import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.lamuier.scheduletimeline.data.*
import com.lamuier.scheduletimeline.ui.edit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScheduleApplication::class)
class ScheduleViewModelTest {
    private lateinit var database: ScheduleDatabase
    private lateinit var repository: ScheduleRepository
    private lateinit var app: ScheduleApplication
    private val stores = mutableListOf<ViewModelStore>()
    private val event = ScheduleEvent(team = TeamNames.encode(listOf("A", "B")), dayKey = "2026-10-20",
        startMinutes = 600, endMinutes = 660)
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = RuntimeEnvironment.getApplication() as ScheduleApplication
        database = Room.inMemoryDatabaseBuilder(app, ScheduleDatabase::class.java).build()
        repository = ScheduleRepository(database)
    }
    private fun model(handle: SavedStateHandle = SavedStateHandle()): ScheduleViewModel {
        val vm = ScheduleViewModel(app, repository, ThemePreferences(app), handle)
        stores += ViewModelStore().apply { put("test", vm) }
        return vm
    }
    private suspend fun loaded(vm: ScheduleViewModel) = withTimeout(5000) {
        vm.editUiState.first { it.error != EditValidationError.Loading }
    }
    private suspend fun written(vm: ScheduleViewModel) = withTimeout(5000) {
        vm.writeState.first { !it.busy }
    }
    @After fun close() { stores.forEach { it.clear() }; database.close(); Dispatchers.resetMain() }

    @Test fun restoredSessionKeepsDraftAndOriginalDateWithoutReloading() = runBlocking {
        val id = repository.save(event)
        val handle = SavedStateHandle()
        val vm = model(handle)
        vm.changeDate(java.time.LocalDate.parse("2030-01-01"))
        vm.prepareEdit(id, "navigation-entry-1"); loaded(vm)
        vm.updateEdit { it.copy(note = "未保存草稿", title = "输入中") }
        vm.prepareEdit(id, "navigation-entry-1")
        assertEquals("未保存草稿", vm.editUiState.value.note)
        val parcel = Parcel.obtain()
        val bundle = Bundle().apply { putSerializable("draft", handle.get<EditUiState>("editDraft")) }
        parcel.writeBundle(bundle); parcel.setDataPosition(0)
        @Suppress("DEPRECATION") val restored = parcel.readBundle(EditUiState::class.java.classLoader)!!.getSerializable("draft") as EditUiState
        parcel.recycle()
        val newVm = model(SavedStateHandle(mapOf("editDraft" to restored, "selectedDate" to "2030-01-01")))
        newVm.prepareEdit(id, "navigation-entry-1")
        assertEquals("未保存草稿", newVm.editUiState.value.note)
        assertEquals("2026-10-20", newVm.editUiState.value.dayKey)
        assertEquals(java.time.LocalDate.parse("2030-01-01"), newVm.currentDate.value)
    }

    @Test fun sameTypeIsNoOpAndDirectInputMatchesAddTeamInput() = runBlocking {
        val vm = model()
        vm.prepareEdit(repository.save(event), "existing"); loaded(vm)
        vm.setEditEventType(EventType.PERFORMANCE)
        assertEquals(listOf("A", "B"), vm.editUiState.value.teamNames)
        vm.prepareEdit(null, "new"); loaded(vm)
        vm.setEditTeamInput("C / D")
        assertEquals(listOf("C"), vm.editUiState.value.effectiveTeamNames())
        vm.addEditTeamInput()
        assertEquals(listOf("C"), vm.editUiState.value.effectiveTeamNames())
    }

    @Test fun transactionFailureLeavesScreenDraftAndAllowsRetry() = runBlocking {
        val vm = model()
        vm.changeDate(java.time.LocalDate.parse("2026-10-20"))
        vm.prepareEdit(null, "new"); loaded(vm); vm.setEditTeamInput("A")
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER prevent_insert BEFORE INSERT ON schedule_events BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        var exited = false
        vm.saveEdit { exited = true }
        val failed = written(vm)
        assertTrue(failed.failed); assertFalse(exited)
        assertEquals("A", vm.editUiState.value.teamInput)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER prevent_insert")
        vm.saveEdit { exited = true }; written(vm)
        assertTrue(exited)
        assertEquals(1, database.scheduleEventDao().count())
    }

    @Test fun deletedRecordCannotBeRecreatedByStaleDraft() = runBlocking {
        val vm = model()
        val id = repository.save(event)
        vm.prepareEdit(id, "existing"); loaded(vm)
        repository.delete(id)
        var exited = false
        vm.saveEdit { exited = true }; written(vm)
        assertTrue(vm.writeState.value.failed); assertFalse(exited)
        assertEquals(0, database.scheduleEventDao().count())
    }
}
