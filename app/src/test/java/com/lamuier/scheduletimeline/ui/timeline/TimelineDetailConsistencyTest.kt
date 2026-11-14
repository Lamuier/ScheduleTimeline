package com.lamuier.scheduletimeline.ui.timeline

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.lamuier.scheduletimeline.R
import com.lamuier.scheduletimeline.ScheduleApplication
import com.lamuier.scheduletimeline.ScheduleViewModel
import com.lamuier.scheduletimeline.data.EventType
import com.lamuier.scheduletimeline.data.ScheduleDatabase
import com.lamuier.scheduletimeline.data.ScheduleEvent
import com.lamuier.scheduletimeline.data.ScheduleRepository
import com.lamuier.scheduletimeline.data.ThemePreferences
import com.lamuier.scheduletimeline.data.TokutenKind
import com.lamuier.scheduletimeline.ui.theme.ScheduleTimelineTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScheduleApplication::class, qualifiers = "zh-rCN-w360dp-h900dp-mdpi")
class TimelineDetailConsistencyTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var app: ScheduleApplication
    private lateinit var database: ScheduleDatabase
    private lateinit var repository: ScheduleRepository
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun setup() {
        app = RuntimeEnvironment.getApplication() as ScheduleApplication
        database = Room.inMemoryDatabaseBuilder(app, ScheduleDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ScheduleRepository(database)
    }

    @After
    fun teardown() {
        stores.forEach { it.clear() }
        database.close()
    }

    private fun createViewModel(): ScheduleViewModel {
        val vm = ScheduleViewModel(app, repository, ThemePreferences(app), SavedStateHandle())
        stores += ViewModelStore().apply { put("vm", vm) }
        return vm
    }

    @Test
    fun detailSheetReadsClickedPageEventsBeforeOuterDateSettles() {
        val day1 = LocalDate.of(2026, 10, 20)
        val day2 = day1.plusDays(1)
        val vm = createViewModel()
        vm.changeDate(day1)

        var tokutenId = 0L
        runBlocking {
            repository.save(
                ScheduleEvent(
                    team = "Day1Team",
                    eventType = EventType.PERFORMANCE.storage,
                    dayKey = day1.toString(),
                    startMinutes = 540,
                    endMinutes = 600,
                ),
            )
            repository.save(
                ScheduleEvent(
                    team = "Day2Team",
                    eventType = EventType.PERFORMANCE.storage,
                    dayKey = day2.toString(),
                    startMinutes = 840,
                    endMinutes = 900,
                ),
            )
            tokutenId = repository.save(
                ScheduleEvent(
                    team = "Day2Team",
                    eventType = EventType.TOKUTEN.storage,
                    tokutenKind = TokutenKind.FINAL.storage,
                    dayKey = day2.toString(),
                    startMinutes = 930,
                    endMinutes = 990,
                ),
            )
        }

        val fixedClock = Clock.fixed(Instant.parse("2026-10-20T01:00:00Z"), ZoneOffset.UTC)
        compose.setContent {
            ScheduleTimelineTheme {
                TimelineScreen(
                    viewModel = vm,
                    onAdd = {},
                    onEditEvent = {},
                    onRequestNotificationPermission = {},
                    clock = fixedClock,
                )
            }
        }

        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            compose.onAllNodesWithText("Day1Team", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Hold drag on day2 without lifting so outer viewModel.currentDate remains on day1.
        val pager = compose.onNodeWithTag("date-pager")
        pager.performTouchInput {
            down(Offset(width * 0.9f, centerY))
            moveBy(Offset(-width * 0.65f, 0f), 120)
        }
        val day2CardTag = "event-card-$tokutenId-930"
        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            compose.onAllNodesWithTag(day2CardTag)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(day1, vm.currentDate.value)

        compose.onNodeWithTag(day2CardTag).performSemanticsAction(SemanticsActions.OnClick)

        val expectedLinked = app.getString(R.string.event_linked_performance, "Day2Team", "14:00 ~ 15:00")
        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            compose.onAllNodesWithText(expectedLinked).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(expectedLinked).assertIsDisplayed()
        pager.performTouchInput { cancel() }
    }

    @Test
    fun openingManageSheetFromTopBarClearsStaleWriteError() {
        val vm = createViewModel()
        val day = LocalDate.of(2026, 10, 20)
        vm.changeDate(day)
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_insert BEFORE INSERT ON schedule_events BEGIN SELECT RAISE(ABORT, 'fail'); END",
        )
        vm.prepareEdit(null, "err-test")
        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            vm.editUiState.value.error == null
        }
        vm.setEditTeamInput("Alpha")
        vm.saveEdit()
        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            !vm.writeState.value.busy
        }
        assertTrue(vm.writeState.value.failed)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_insert")

        compose.setContent {
            ScheduleTimelineTheme {
                TimelineScreen(
                    viewModel = vm,
                    onAdd = {},
                    onEditEvent = {},
                    onRequestNotificationPermission = {},
                )
            }
        }

        compose.onNodeWithContentDescription(app.getString(R.string.cd_more)).performClick()
        compose.waitUntil(5000) {
            ShadowLooper.idleMainLooper()
            !vm.writeState.value.failed
        }
        compose.waitForIdle()

        assertFalse(vm.writeState.value.failed)
        compose.onNodeWithText(app.getString(R.string.manage_data_title)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.operation_failed)).assertDoesNotExist()
    }
}
