package com.lamuier.scheduletimeline

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.lamuier.scheduletimeline.data.ScheduleEvent
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScheduleApplication::class, qualifiers = "zh-rCN-w360dp-h800dp-mdpi")
class SystemEntryComposeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: org.robolectric.android.controller.ActivityController<MainActivity>
    private val activity get() = controller.get()
    @Before fun setup() {
        controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java)
        runBlocking { (org.robolectric.RuntimeEnvironment.getApplication() as ScheduleApplication).repository.clearAll() }
        controller.setup()
    }
    @After fun close() { controller.pause().stop().destroy() }
    private fun vm() = ViewModelProvider(activity, ScheduleViewModel.factory(activity.application))[ScheduleViewModel::class.java]
    private fun dateText(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
    private fun intent(action: String) = compose.runOnIdle {
        controller.newIntent(Intent(activity, MainActivity::class.java).setAction(action))
    }

    @Test fun consecutiveArrowsGestureAndFarDateKeepActionsOnVisibleDate() {
        val start = LocalDate.now()
        compose.mainClock.autoAdvance = false
        repeat(3) {
            compose.onNodeWithContentDescription("后一天").performClick()
            compose.mainClock.advanceTimeBy(32)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText(dateText(start.plusDays(3))).assertIsDisplayed()
        compose.onNodeWithTag("date-pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(start.plusDays(4), vm().currentDate.value) }
        val far = LocalDate.of(2100, 5, 6)
        compose.runOnIdle { vm().changeDate(far) }
        compose.waitForIdle()
        compose.onNodeWithText(dateText(far)).assertIsDisplayed()
        compose.onNodeWithContentDescription("添加日程").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(far.toString(), vm().editUiState.value.dayKey) }
    }

    @Test fun todayNextAndImportShortcutsNavigateOutOfEditor() {
        intent(AppShortcuts.ACTION_ADD_EVENT)
        compose.onNodeWithContentDescription("返回").assertIsDisplayed()
        intent(AppShortcuts.ACTION_TODAY)
        compose.onNodeWithTag("date-pager").assertIsDisplayed()
        val future = LocalDate.now().plusDays(4)
        runBlocking { (activity.application as ScheduleApplication).repository.save(ScheduleEvent(
            team = "Future", dayKey = future.toString(), startMinutes = 600, endMinutes = 660)) }
        intent(AppShortcuts.ACTION_ADD_EVENT)
        compose.onNodeWithContentDescription("返回").assertIsDisplayed()
        assertEquals(future.toString(), runBlocking { (activity.application as ScheduleApplication).repository.firstDayKeyOnOrAfter(LocalDate.now().toString()) })
        intent(AppShortcuts.ACTION_NEXT_SCHEDULED)
        compose.waitUntil(5000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm().currentDate.value == future
        }
        compose.onNodeWithText(dateText(future)).assertIsDisplayed()
        intent(AppShortcuts.ACTION_ADD_EVENT)
        intent(AppShortcuts.ACTION_IMPORT)
        compose.onNodeWithText("批量导入").assertIsDisplayed()
        // Import can fill the dialog; activate the scrim's accessible close action.
        compose.onNodeWithContentDescription("Close sheet")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("批量导入").assertDoesNotExist()
        intent(AppShortcuts.ACTION_IMPORT)
        compose.onNodeWithText("批量导入").assertIsDisplayed()
        compose.runOnIdle { org.robolectric.shadows.ShadowDialog.getLatestDialog().onBackPressed() }
        compose.onNodeWithText("批量导入").assertDoesNotExist()
    }
}
