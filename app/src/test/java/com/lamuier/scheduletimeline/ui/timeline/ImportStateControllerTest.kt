package com.lamuier.scheduletimeline.ui.timeline

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.lamuier.scheduletimeline.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ImportStateControllerTest {
    @Test fun cancelledSlowInputCannotReplaceNewPreviewOrBeSubmitted() = runTest {
        val gate = CompletableDeferred<Unit>()
        val controller = ImportStateController(backgroundScope, SavedStateHandle(), "2026-10-15",
            StandardTestDispatcher(testScheduler)) { text, day ->
            if (text == "old") withContext(NonCancellable) { gate.await() }
            ImportParseResult(listOf(ImportDraft(ScheduleEvent(team = text, dayKey = day,
                startMinutes = 600, endMinutes = 660))), emptyList())
        }
        controller.updateText("old"); runCurrent()
        val oldVersion = controller.state.value.inputVersion
        assertNull(controller.selectedDrafts(oldVersion))
        controller.updateText("new"); runCurrent()
        assertEquals("new", controller.state.value.previewEvents.single().team)
        assertNull(controller.selectedDrafts(oldVersion))
        gate.complete(Unit); runCurrent()
        assertEquals("new", controller.selectedDrafts(controller.state.value.inputVersion)!!.single().event.team)
        controller.updateDay("2026-10-16")
        assertFalse(controller.state.value.canImport)
        runCurrent()
        assertEquals("2026-10-16", controller.state.value.drafts.single().event.dayKey)
    }

    @Test fun thousandRowsKeepDiagnosticsSelectionAndRestoredDraft() = runTest {
        val handle = SavedStateHandle()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = ImportStateController(backgroundScope, handle, "2026-10-15", dispatcher)
        val events = List(1000) { ScheduleEvent(team = "Team$it", dayKey = "2026-10-15",
            startMinutes = it, endMinutes = it + 1) }
        controller.updateText(ScheduleExport.toCsv(events) + "\n2026-10-15,B,演出,,,,11:00,,")
        runCurrent()
        assertEquals(1000, controller.state.value.drafts.size)
        assertEquals(1, controller.state.value.issues.size)
        controller.toggle(12)
        assertEquals(999, controller.selectedDrafts(controller.state.value.inputVersion)!!.size)
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })
        val restored = ImportStateController(backgroundScope, restoredHandle, "2026-10-15", dispatcher)
        assertFalse(restored.state.value.canImport)
        runCurrent()
        assertEquals(controller.state.value.text, restored.state.value.text)
        assertEquals(controller.state.value.selectedIndexes, restored.state.value.selectedIndexes)
        restored.selectAll(false)
        assertFalse(restored.state.value.canImport)
    }
}
