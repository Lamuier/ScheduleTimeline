package com.lamuier.scheduletimeline

import android.os.Bundle
import android.os.Parcel
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.lamuier.scheduletimeline.data.*
import com.lamuier.scheduletimeline.ui.edit.*
import com.lamuier.scheduletimeline.ui.theme.ScheduleTimelineTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

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

    @Test fun cachedDayAndSelectedDaySharePreparedTimelineAndKeepInvalidRecords() = runBlocking {
        val id = repository.save(event)
        val invalid = event.copy(id = 0, startMinutes = 660, endMinutes = 660)
        val invalidId = database.scheduleEventDao().upsert(invalid)
        val vm = model()
        val date = java.time.LocalDate.parse(event.dayKey)
        val pageLease = vm.acquireDayState(date)
        val page = pageLease.state
        vm.acquireDayState(date).use { assertSame(page, it.state) }
        val prepared = withTimeout(5000) { page.first { it.events.size == 2 } }
        assertEquals(listOf(id), prepared.items.filterIsInstance<TimelineItem.Event>().map { it.event.id })
        assertEquals(1, prepared.stats?.eventCount)
        assertEquals(listOf(invalidId), prepared.invalidEvents.map { it.id })
        vm.changeDate(date)
        val selected = withTimeout(5000) { vm.dayState.first { it.date == date && it.events.size == 2 } }
        assertSame(prepared, selected)
        repository.delete(id)
        val changed = withTimeout(5000) { page.first { it.events.size == 1 } }
        assertTrue(changed.items.isEmpty())
        assertNull(changed.stats)
        assertEquals(listOf(invalidId), changed.invalidEvents.map { it.id })
        pageLease.close()
    }

    @Test fun dynamicColorPreferenceTogglesAndPersists() {
        val vm = model()
        assertFalse(vm.dynamicColor.value)
        vm.setDynamicColor(true)
        assertTrue(vm.dynamicColor.value)
        val newVm = model()
        assertTrue(newVm.dynamicColor.value)
        newVm.setDynamicColor(false)
        assertFalse(newVm.dynamicColor.value)
    }

    private fun parcelRoundTrip(state: EditUiState): EditUiState {
        val parcel = Parcel.obtain()
        val bundle = Bundle().apply { putSerializable("state", state) }
        parcel.writeBundle(bundle)
        parcel.setDataPosition(0)
        @Suppress("DEPRECATION")
        val restored = parcel.readBundle(EditUiState::class.java.classLoader)!!
            .getSerializable("state") as EditUiState
        parcel.recycle()
        return restored
    }

    @Test fun dirtyBaselinePersistsAcrossRecreationForCleanAndEditedSessions() = runBlocking {
        val id = repository.save(event)

        // Clean existing session stays clean after SavedStateHandle recreation
        val cleanHandle = SavedStateHandle()
        val cleanVm = model(cleanHandle)
        cleanVm.prepareEdit(id, "clean-session"); loaded(cleanVm)
        assertFalse(cleanVm.isEditDirty.value)
        val restoredCleanVm = model(
            SavedStateHandle(
                mapOf(
                    "editDraft" to parcelRoundTrip(cleanHandle.get<EditUiState>("editDraft")!!),
                    "editBaseline" to parcelRoundTrip(cleanHandle.get<EditUiState>("editBaseline")!!),
                ),
            ),
        )
        restoredCleanVm.prepareEdit(id, "clean-session")
        assertFalse(restoredCleanVm.isEditDirty.value)

        // Time-only edit stays dirty across same-VM re-prepare and process recreation, then clears when reverted
        val dirtyHandle = SavedStateHandle()
        val dirtyVm = model(dirtyHandle)
        dirtyVm.prepareEdit(id, "dirty-time-session"); loaded(dirtyVm)
        dirtyVm.setStartMinutes(660)
        assertTrue(dirtyVm.isEditDirty.value)
        dirtyVm.prepareEdit(id, "dirty-time-session")
        assertTrue(dirtyVm.isEditDirty.value)

        val restoredDirtyVm = model(
            SavedStateHandle(
                mapOf(
                    "editDraft" to parcelRoundTrip(dirtyHandle.get<EditUiState>("editDraft")!!),
                    "editBaseline" to parcelRoundTrip(dirtyHandle.get<EditUiState>("editBaseline")!!),
                ),
            ),
        )
        restoredDirtyVm.prepareEdit(id, "dirty-time-session")
        assertTrue(restoredDirtyVm.isEditDirty.value)
        restoredDirtyVm.setStartMinutes(600)
        assertFalse(restoredDirtyVm.isEditDirty.value)
    }

    @Test fun deleteTeamSyncsBaselineWithoutFalseDirtyAndKeepsRealEditsDirty() = runBlocking {
        val id = repository.save(event.copy(title = "午场"))
        val handle = SavedStateHandle()
        val vm = model(handle)
        vm.prepareEdit(id, "delete-team-session"); loaded(vm)
        assertFalse(vm.isEditDirty.value)

        vm.deleteTeam("A"); written(vm)
        assertEquals(listOf("B"), vm.editUiState.value.teamNames)
        assertFalse(vm.isEditDirty.value)

        // Real edits on other fields and another unsaved team in input remain dirty after deleting B
        vm.updateEdit { it.copy(title = "晚场") }
        vm.setEditTeamInput("B / C")
        assertTrue(vm.isEditDirty.value)
        vm.deleteTeam("B"); written(vm)
        assertTrue(vm.editUiState.value.teamNames.isEmpty())
        assertEquals("C", vm.editUiState.value.teamInput)
        assertTrue(vm.isEditDirty.value)

        // Survives SavedStateHandle recreation and clears only if edits are reverted
        val recreated = model(
            SavedStateHandle(
                mapOf(
                    "editDraft" to parcelRoundTrip(handle.get<EditUiState>("editDraft")!!),
                    "editBaseline" to parcelRoundTrip(handle.get<EditUiState>("editBaseline")!!),
                ),
            ),
        )
        recreated.prepareEdit(id, "delete-team-session")
        assertTrue(recreated.isEditDirty.value)
        recreated.updateEdit { it.copy(title = "午场") }
        recreated.setEditTeamInput("")
        assertFalse(recreated.isEditDirty.value)
    }

    @Test fun deleteTeamPreservesDirtyWhenAnotherUnsavedTeamWasToggled() = runBlocking {
        val singleTeamEvent = event.copy(team = "A")
        val id = repository.save(singleTeamEvent)
        val vm = model()
        vm.prepareEdit(id, "tokuten-delete-session"); loaded(vm)
        vm.setEditEventType(EventType.TOKUTEN)
        vm.toggleEditTeam("C")
        assertEquals(listOf("A", "C"), vm.editUiState.value.teamNames)
        assertTrue(vm.isEditDirty.value)

        vm.deleteTeam("A"); written(vm)
        assertEquals(listOf("C"), vm.editUiState.value.teamNames)
        assertTrue(vm.isEditDirty.value)
    }

    @Test fun setStartMinutesHandlesInvalidDurationAndMidnightBounds() = runBlocking {
        val vm = model()
        vm.prepareEdit(null, "time-bounds"); loaded(vm)

        // Normal shift preserves duration
        vm.setStartMinutes(600)
        assertEquals(600, vm.editUiState.value.startMinutes)
        assertEquals(660, vm.editUiState.value.endMinutes)

        // Near 23:59 clamps endMinutes to 1439 while keeping valid interval
        vm.setStartMinutes(1410)
        assertEquals(1410, vm.editUiState.value.startMinutes)
        assertEquals(1439, vm.editUiState.value.endMinutes)

        // Exactly 23:59 (1439) or above clamps start to 1438 and end to 1439 so end > start holds
        vm.setStartMinutes(1439)
        assertEquals(1438, vm.editUiState.value.startMinutes)
        assertEquals(1439, vm.editUiState.value.endMinutes)
        vm.setStartMinutes(1500)
        assertEquals(1438, vm.editUiState.value.startMinutes)
        assertEquals(1439, vm.editUiState.value.endMinutes)

        // Zero duration fallback (e.g. invalid event 660..660) uses positive duration
        vm.updateEdit { it.copy(startMinutes = 660, endMinutes = 660) }
        vm.setStartMinutes(720)
        assertEquals(720, vm.editUiState.value.startMinutes)
        assertEquals(780, vm.editUiState.value.endMinutes)

        // Negative duration fallback (e.g. 700..600) uses positive duration
        vm.updateEdit { it.copy(startMinutes = 700, endMinutes = 600) }
        vm.setStartMinutes(100)
        assertEquals(100, vm.editUiState.value.startMinutes)
        assertEquals(160, vm.editUiState.value.endMinutes)

        // Invalid duration near 23:59 + negative input boundary
        vm.updateEdit { it.copy(startMinutes = 1439, endMinutes = 1439) }
        vm.setStartMinutes(1430)
        assertEquals(1430, vm.editUiState.value.startMinutes)
        assertEquals(1439, vm.editUiState.value.endMinutes)
        vm.setStartMinutes(-15)
        assertEquals(0, vm.editUiState.value.startMinutes)
        assertTrue(vm.editUiState.value.endMinutes > vm.editUiState.value.startMinutes)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScheduleApplication::class, qualifiers = "zh-rCN-w360dp-h900dp-mdpi")
class EditEventScreenComposeTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var database: ScheduleDatabase
    private lateinit var app: ScheduleApplication
    private val stores = mutableListOf<ViewModelStore>()
    private var busyGate: CompletableDeferred<Unit>? = null

    private class GateEventDao(
        private val delegate: ScheduleEventDao,
        private val gateProvider: () -> CompletableDeferred<Unit>?,
    ) : ScheduleEventDao by delegate {
        override suspend fun deleteById(id: Long) {
            if (id < 0L) {
                gateProvider()?.await()
                return
            }
            delegate.deleteById(id)
        }
    }

    @Before fun setup() {
        app = RuntimeEnvironment.getApplication() as ScheduleApplication
        database = Room.inMemoryDatabaseBuilder(app, ScheduleDatabase::class.java).build()
    }

    @After fun close() {
        busyGate?.complete(Unit)
        stores.forEach { it.clear() }
        database.close()
    }

    private fun createViewModel(handle: SavedStateHandle = SavedStateHandle()): Pair<ScheduleViewModel, ScheduleRepository> {
        val repository = ScheduleRepository(
            database = database,
            eventDao = GateEventDao(database.scheduleEventDao()) { busyGate },
        )
        val vm = ScheduleViewModel(app, repository, ThemePreferences(app), handle)
        stores += ViewModelStore().apply { put("test", vm) }
        return vm to repository
    }

    @Test fun busyStateDisablesControlsAndSwallowsSystemBack() {
        val (vm, repo) = createViewModel()
        val id = runBlocking {
            repo.save(ScheduleEvent(team = "StarDiary", dayKey = "2026-10-20", startMinutes = 600, endMinutes = 660))
        }
        var backed = false
        lateinit var backDispatcher: OnBackPressedDispatcher

        compose.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            ScheduleTimelineTheme {
                EditEventScreen(
                    eventId = id,
                    sessionId = "busy-edit",
                    viewModel = vm,
                    onBack = { backed = true },
                )
            }
        }
        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            vm.editUiState.value.error == null && vm.editUiState.value.loadedId == id
        }
        compose.waitForIdle()

        // Populate teamInput so + button would otherwise be enabled
        compose.onNodeWithText("例如：StarDiary").performTextInput("Silver")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("添加团队").assertIsEnabled()

        // Block an active write to keep writeState.busy == true
        val gate = CompletableDeferred<Unit>()
        busyGate = gate
        compose.runOnUiThread { vm.delete(-1L) }
        compose.waitUntil(5000) { vm.writeState.value.busy }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("返回").assertIsNotEnabled()
        compose.onNodeWithText("StarDiary").assertIsNotEnabled()
        compose.onNodeWithContentDescription("添加团队").assertIsNotEnabled()
        compose.onNodeWithText("开始").assertIsNotEnabled()
        compose.onNodeWithText("结束").assertIsNotEnabled()

        // System back must do nothing while writeState.busy is true (even when dirty)
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertFalse(backed)
        compose.onNodeWithText("放弃未保存的修改？").assertDoesNotExist()

        // Unblock write -> controls re-enable and back opens discard dialog since draft is dirty
        gate.complete(Unit)
        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            !vm.writeState.value.busy
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("返回").assertIsEnabled()
        compose.onNodeWithText("StarDiary").assertIsEnabled()
        compose.onNodeWithContentDescription("添加团队").assertIsEnabled()
        compose.onNodeWithText("开始").assertIsEnabled()
        compose.onNodeWithText("结束").assertIsEnabled()

        compose.runOnUiThread { backDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
        assertFalse(backed)
    }

    @Test fun busyStateDisablesDialogsAndPreventsClosingDeleteTeamDialog() {
        val (vm, repo) = createViewModel()
        val id = runBlocking {
            repo.save(ScheduleEvent(team = "StarDiary", dayKey = "2026-10-20", startMinutes = 600, endMinutes = 660))
        }
        var backed = false
        lateinit var backDispatcher: OnBackPressedDispatcher

        compose.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            ScheduleTimelineTheme {
                EditEventScreen(
                    eventId = id,
                    sessionId = "busy-dialogs",
                    viewModel = vm,
                    onBack = { backed = true },
                )
            }
        }
        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            vm.editUiState.value.error == null && vm.editUiState.value.loadedId == id
        }
        compose.waitForIdle()

        // Open teamToDelete dialog
        compose.onNodeWithText("StarDiary").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("删除团队").assertIsDisplayed()

        // Enter busy state while dialog is open
        val gate1 = CompletableDeferred<Unit>()
        busyGate = gate1
        compose.runOnUiThread { vm.delete(-1L) }
        compose.waitUntil(5000) { vm.writeState.value.busy }
        compose.waitForIdle()

        compose.onAllNodesWithText("删除")[1].assertIsNotEnabled()
        compose.onNodeWithText("取消").assertIsNotEnabled()
        compose.runOnUiThread { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("删除团队").assertIsDisplayed()

        gate1.complete(Unit)
        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            !vm.writeState.value.busy
        }
        compose.waitForIdle()
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("删除团队").assertDoesNotExist()

        // Open showDeleteConfirm dialog and verify busy disables its buttons too
        compose.onNodeWithText("删除").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("删除日程").assertIsDisplayed()

        val gate2 = CompletableDeferred<Unit>()
        busyGate = gate2
        compose.runOnUiThread { vm.delete(-1L) }
        compose.waitUntil(5000) { vm.writeState.value.busy }
        compose.waitForIdle()

        compose.onAllNodesWithText("删除")[1].assertIsNotEnabled()
        compose.onNodeWithText("取消").assertIsNotEnabled()
        compose.runOnUiThread { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("删除日程").assertIsDisplayed()

        gate2.complete(Unit)
        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            !vm.writeState.value.busy
        }
        compose.waitForIdle()
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()

        // Open showDiscardConfirm dialog and verify busy disables its buttons too
        compose.runOnUiThread { vm.updateEdit { it.copy(note = "edited") } }
        compose.waitForIdle()
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()

        val gate3 = CompletableDeferred<Unit>()
        busyGate = gate3
        compose.runOnUiThread { vm.delete(-1L) }
        compose.waitUntil(5000) { vm.writeState.value.busy }
        compose.waitForIdle()

        compose.onNodeWithText("放弃修改").assertIsNotEnabled()
        compose.onNodeWithText("继续编辑").assertIsNotEnabled()
        compose.runOnUiThread { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
        assertFalse(backed)

        gate3.complete(Unit)
        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            !vm.writeState.value.busy
        }
        compose.waitForIdle()
        compose.onNodeWithText("放弃修改").performClick()
        compose.waitForIdle()
        assertTrue(backed)
    }

    @Test fun recreatedScreenPreservesDirtyPromptOnBackAfterStateRestoration() {
        val handle = SavedStateHandle()
        val (vm, repo) = createViewModel(handle)
        val id = runBlocking {
            repo.save(ScheduleEvent(team = "StarDiary", dayKey = "2026-10-20", startMinutes = 600, endMinutes = 660))
        }
        var showFirst by mutableStateOf(true)
        var activeVm by mutableStateOf(vm)
        lateinit var backDispatcher: OnBackPressedDispatcher
        var backed = false

        compose.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            ScheduleTimelineTheme {
                if (showFirst) {
                    EditEventScreen(
                        eventId = id,
                        sessionId = "recreate-session",
                        viewModel = activeVm,
                        onBack = { backed = true },
                    )
                } else {
                    EditEventScreen(
                        eventId = id,
                        sessionId = "recreate-session",
                        viewModel = activeVm,
                        onBack = { backed = true },
                    )
                }
            }
        }
        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            vm.editUiState.value.error == null && vm.editUiState.value.loadedId == id
        }
        compose.waitForIdle()

        // Make a change and simulate full ViewModel + Screen recreation with restored SavedStateHandle
        compose.runOnUiThread { vm.setStartMinutes(660) }
        compose.waitForIdle()
        val restoredHandle = SavedStateHandle(
            mapOf(
                "editDraft" to handle.get<EditUiState>("editDraft"),
                "editBaseline" to handle.get<EditUiState>("editBaseline"),
            ),
        )
        val (recreatedVm, _) = createViewModel(restoredHandle)
        compose.runOnUiThread {
            activeVm = recreatedVm
            showFirst = false
        }
        compose.waitForIdle()

        compose.runOnUiThread { backDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
        assertFalse(backed)
    }

    @Test fun missingEventDisablesSaveAndDeleteButtons() {
        val (vm, _) = createViewModel()
        compose.setContent {
            ScheduleTimelineTheme {
                EditEventScreen(
                    eventId = 999999L,
                    sessionId = "missing-event",
                    viewModel = vm,
                    onBack = {},
                )
            }
        }
        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            vm.editUiState.value.error == EditValidationError.MissingEvent
        }
        compose.waitForIdle()

        assertEquals(0L, vm.editUiState.value.loadedId)
        compose.onNodeWithText("这项日程已不存在，请返回时间轴。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("保存日程").assertIsNotEnabled()
        compose.onNodeWithText("删除").performScrollTo().assertIsNotEnabled()
    }
}
