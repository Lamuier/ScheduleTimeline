package com.lamuier.scheduletimeline.ui.timeline

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.lamuier.scheduletimeline.R
import com.lamuier.scheduletimeline.ScheduleApplication
import com.lamuier.scheduletimeline.ScheduleViewModel
import com.lamuier.scheduletimeline.data.EventType
import com.lamuier.scheduletimeline.data.ImportDraft
import com.lamuier.scheduletimeline.data.ImportError
import com.lamuier.scheduletimeline.data.ImportIssue
import com.lamuier.scheduletimeline.data.ScheduleDatabase
import com.lamuier.scheduletimeline.data.ScheduleEvent
import com.lamuier.scheduletimeline.data.ScheduleRepository
import com.lamuier.scheduletimeline.data.ThemePreferences
import com.lamuier.scheduletimeline.ui.theme.ScheduleTimelineTheme
import java.time.LocalDate
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScheduleApplication::class, qualifiers = "zh-rCN-w360dp-h900dp-mdpi")
class ManageDataSheetComposeTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var app: ScheduleApplication
    private lateinit var database: ScheduleDatabase
    private lateinit var repository: ScheduleRepository
    private val stores = mutableListOf<ViewModelStore>()
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val dbGate = PausableExecutor(backgroundExecutor)

    @Before
    fun setup() {
        app = RuntimeEnvironment.getApplication() as ScheduleApplication
        database = Room.inMemoryDatabaseBuilder(app, ScheduleDatabase::class.java)
            .setQueryCoroutineContext(dbGate.asCoroutineDispatcher())
            .allowMainThreadQueries()
            .build()
        repository = ScheduleRepository(database)
    }

    @After
    fun teardown() {
        dbGate.release()
        stores.forEach { it.clear() }
        database.close()
        backgroundExecutor.shutdownNow()
    }

    private fun createViewModel(): ScheduleViewModel {
        val vm = ScheduleViewModel(app, repository, ThemePreferences(app), SavedStateHandle())
        stores += ViewModelStore().apply { put("vm", vm) }
        return vm
    }

    @Test
    fun importViewPlacesSheetHeaderAboveParseErrors() {
        compose.setContent {
            ScheduleTimelineTheme {
                ImportView(
                    state = ImportUiState(
                        text = "bad-row",
                        fallbackDayKey = "2026-10-20",
                        issues = listOf(
                            ImportIssue(line = 1, error = ImportError.Fields),
                            ImportIssue(line = 2, error = ImportError.Time),
                        ),
                    ),
                    onTextChange = {},
                    onToggle = {},
                    onSetAll = {},
                    onBack = {},
                    onImport = {},
                )
            }
        }

        compose.onNodeWithText("团队（必填，可多项）", substring = true).assertIsDisplayed()
        compose.onNodeWithText("均可按列留空").assertDoesNotExist()
        val headerBounds = compose.onNodeWithText("批量导入").fetchSemanticsNode().boundsInRoot
        val firstIssueBounds = compose.onNodeWithText("第 1 行：列数不正确").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Header ($headerBounds) should appear above parse error ($firstIssueBounds)",
            headerBounds.bottom <= firstIssueBounds.top,
        )
    }

    @Test
    fun importViewDisablesAllInteractiveControlsWhileBusy() {
        var backCalls = 0
        var setAllCalls = 0
        var toggleCalls = 0
        var importCalls = 0
        val event = ScheduleEvent(
            team = "TeamBusy",
            dayKey = "2026-10-20",
            startMinutes = 600,
            endMinutes = 660,
        )
        val state = ImportUiState(
            text = "2026-10-20, TeamBusy, 演出, , , , 10:00, 11:00, ",
            fallbackDayKey = "2026-10-20",
            inputVersion = 1L,
            parsedVersion = 1L,
            drafts = listOf(ImportDraft(event, line = 1)),
            previewEvents = listOf(event),
            selectedIndexes = setOf(0),
        )
        compose.setContent {
            ScheduleTimelineTheme {
                ImportView(
                    state = state,
                    busy = true,
                    onTextChange = {},
                    onToggle = { toggleCalls++ },
                    onSetAll = { setAllCalls++ },
                    onBack = { backCalls++ },
                    onImport = { importCalls++ },
                )
            }
        }

        compose.onNodeWithContentDescription("返回").assertIsNotEnabled().performClick()
        compose.onNode(hasText("2026-10-20, TeamBusy", substring = true)).assertIsNotEnabled()
        compose.onNodeWithText("全不选").assertIsNotEnabled().performClick()
        compose.onNodeWithText("TeamBusy").assertIsNotEnabled().performClick()
        compose.onNodeWithText("开始导入").assertIsNotEnabled().performClick()

        assertEquals(0, backCalls)
        assertEquals(0, setAllCalls)
        assertEquals(0, toggleCalls)
        assertEquals(0, importCalls)
    }

    @Test
    fun clearConfirmViewDisablesAllButtonsWhileBusy() {
        var backCalls = 0
        var confirmCalls = 0
        compose.setContent {
            ScheduleTimelineTheme {
                ClearConfirmView(
                    title = "清空当日日程？",
                    message = "将删除 2026年10月20日 的 2 项日程",
                    confirmText = "确认清空",
                    busy = true,
                    onBack = { backCalls++ },
                    onConfirm = { confirmCalls++ },
                )
            }
        }

        compose.onNodeWithContentDescription("返回").assertIsNotEnabled().performClick()
        compose.onNodeWithText("确认清空").assertIsNotEnabled().performClick()
        compose.onNodeWithText("取消").assertIsNotEnabled().performClick()

        assertEquals(0, backCalls)
        assertEquals(0, confirmCalls)
    }

    @Test
    fun manageDataSheetClearsStaleWriteErrorOnOpenAndDisplaysFreshFailure() {
        val vm = createViewModel()
        val date = LocalDate.of(2026, 10, 20)
        vm.changeDate(date)
        runBlocking {
            repository.save(
                ScheduleEvent(
                    team = "Alpha",
                    dayKey = date.toString(),
                    startMinutes = 600,
                    endMinutes = 660,
                ),
            )
        }
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_delete BEFORE DELETE ON schedule_events BEGIN SELECT RAISE(ABORT, 'boom'); END",
        )
        vm.clearAllData()
        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            !vm.writeState.value.busy
        }
        assertTrue(vm.writeState.value.failed)

        compose.setContent {
            ScheduleTimelineTheme {
                ManageDataSheet(
                    viewModel = vm,
                    onRequestNotificationPermission = {},
                    onDismiss = {},
                )
            }
        }

        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            !vm.writeState.value.failed
        }
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.operation_failed)).assertDoesNotExist()

        compose.onNodeWithText(app.getString(R.string.cd_clear_all)).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.clear_all_confirm)).performClick()
        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            !vm.writeState.value.busy && vm.writeState.value.failed
        }
        compose.onNodeWithText(app.getString(R.string.operation_failed)).assertIsDisplayed()
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_delete")
    }

    @Test
    fun exportEmptyDayShowsEmptyToastWithoutLaunchingShareSheet() {
        val vm = createViewModel()
        vm.changeDate(LocalDate.of(2026, 10, 21))
        var dismissed = false

        compose.setContent {
            ScheduleTimelineTheme {
                ManageDataSheet(
                    viewModel = vm,
                    onRequestNotificationPermission = {},
                    onDismiss = { dismissed = true },
                )
            }
        }

        compose.onNodeWithText(app.getString(R.string.cd_export)).performScrollTo().performClick()
        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            ShadowToast.getTextOfLatestToast() != null
        }

        assertEquals(app.getString(R.string.export_empty), ShadowToast.getTextOfLatestToast())
        assertFalse(dismissed)
        assertNull(Shadows.shadowOf(app).nextStartedActivity)
    }

    @Test
    fun exportNonEmptyDayGuardsDoubleTapAndLaunchesChooserOnce() {
        val vm = createViewModel()
        val date = LocalDate.of(2026, 10, 22)
        vm.changeDate(date)
        runBlocking {
            repository.save(
                ScheduleEvent(
                    team = "StarDiary",
                    eventType = EventType.PERFORMANCE.storage,
                    dayKey = date.toString(),
                    startMinutes = 840,
                    endMinutes = 900,
                ),
            )
        }
        var dismissCount = 0

        compose.setContent {
            ScheduleTimelineTheme {
                ManageDataSheet(
                    viewModel = vm,
                    onRequestNotificationPermission = {},
                    onDismiss = { dismissCount++ },
                )
            }
        }

        compose.waitForIdle()
        val exportNode = compose.onNodeWithText(app.getString(R.string.cd_export)).performScrollTo()
        dbGate.pause()
        exportNode.performClick()
        exportNode.assertIsNotEnabled().performClick()
        dbGate.release()

        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            dismissCount > 0
        }
        assertEquals(1, dismissCount)
        val firstIntent = Shadows.shadowOf(app).nextStartedActivity
        assertNotNull(firstIntent)
        assertEquals(Intent.ACTION_CHOOSER, firstIntent.action)
        assertNull(Shadows.shadowOf(app).nextStartedActivity)
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun clearDayConfirmKeepsStaticEventCountSnapshotWhileDeletingAndDisablesButtons() {
        val vm = createViewModel()
        val date = LocalDate.of(2026, 10, 23)
        vm.changeDate(date)
        runBlocking {
            repository.saveAll(
                listOf(
                    ScheduleEvent(team = "A", dayKey = date.toString(), startMinutes = 600, endMinutes = 660),
                    ScheduleEvent(team = "B", dayKey = date.toString(), startMinutes = 700, endMinutes = 760),
                ),
            )
        }
        var dismissed by mutableStateOf(false)

        compose.setContent {
            ScheduleTimelineTheme {
                // Keep sheet mounted after onDismiss to verify displayed count does not flicker to 0.
                ManageDataSheet(
                    viewModel = vm,
                    onRequestNotificationPermission = {},
                    onDismiss = { dismissed = true },
                )
            }
        }

        compose.onNodeWithText(app.getString(R.string.cd_clear_day)).performScrollTo().performClick()
        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            compose.onAllNodes(
                hasText("2 项日程", substring = true),
            ).fetchSemanticsNodes().isNotEmpty()
        }

        dbGate.pause()
        compose.onNodeWithText(app.getString(R.string.clear_day_confirm)).performClick()
        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            vm.writeState.value.busy
        }
        compose.onNodeWithText(app.getString(R.string.clear_day_confirm)).assertIsNotEnabled()
        compose.onNodeWithText(app.getString(R.string.action_cancel)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(app.getString(R.string.cd_back)).assertIsNotEnabled()
        compose.onNodeWithText("2 项日程", substring = true).assertIsDisplayed()

        dbGate.release()
        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            dismissed && runBlocking { repository.eventsForDay(date.toString()).isEmpty() }
        }
        compose.waitForIdle()

        compose.onNodeWithText("2 项日程", substring = true).assertIsDisplayed()
        compose.onNodeWithText("0 项日程", substring = true).assertDoesNotExist()
    }
}

private class PausableExecutor(private val delegate: Executor) : Executor {
    private val lock = Any()
    private var paused = false
    private val queue = ArrayDeque<Runnable>()

    fun pause() {
        synchronized(lock) { paused = true }
    }

    fun release() {
        val pending = synchronized(lock) {
            paused = false
            buildList {
                while (queue.isNotEmpty()) add(queue.removeFirst())
            }
        }
        pending.forEach { delegate.execute(it) }
    }

    override fun execute(command: Runnable) {
        val runNow = synchronized(lock) {
            if (paused) {
                queue.addLast(command)
                false
            } else {
                true
            }
        }
        if (runNow) delegate.execute(command)
    }
}
