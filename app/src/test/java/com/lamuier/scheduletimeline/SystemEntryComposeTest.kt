package com.lamuier.scheduletimeline

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.geometry.Offset
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

    @Test fun heldDragMovesWithFingerAndUpdatesDateBeforeRelease() {
        val today = LocalDate.now()
        runBlocking { (activity.application as ScheduleApplication).repository.saveAll(listOf(
            ScheduleEvent(team = "Drag today", dayKey = today.toString(), startMinutes = 600, endMinutes = 660),
            ScheduleEvent(team = "Drag tomorrow", dayKey = today.plusDays(1).toString(), startMinutes = 600, endMinutes = 660),
        )) }
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Drag today", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val pager = compose.onNodeWithTag("date-pager")
        val page = compose.onNodeWithTag("day-page-$today")
        val startRight = page.fetchSemanticsNode().boundsInRoot.right
        pager.performTouchInput { down(center); moveBy(Offset(-80f, 0f), 32) }
        compose.mainClock.advanceTimeBy(32)
        val firstRight = page.fetchSemanticsNode().boundsInRoot.right
        assertTrue("The page must move before the finger is released", startRight - firstRight > 50f)
        pager.performTouchInput { moveBy(Offset(-80f, 0f), 32) }
        compose.mainClock.advanceTimeBy(32)
        val secondRight = page.fetchSemanticsNode().boundsInRoot.right
        assertEquals("Further movement follows the finger", 80f, firstRight - secondRight, 3f)
        pager.performTouchInput { moveBy(Offset(-70f, 0f), 32) }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText(dateText(today.plusDays(1))).assertIsDisplayed()
        compose.onNodeWithContentDescription("返回今天").assertIsDisplayed()
        pager.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(today.plusDays(1), vm().currentDate.value) }
    }

    @Test fun nearestAndTodayJumpsFinishOnNextFramesWithoutPageAnimation() {
        val today = LocalDate.now()
        val future = today.plusDays(2)
        runBlocking { (activity.application as ScheduleApplication).repository.save(ScheduleEvent(
            team = "Future", dayKey = future.toString(), startMinutes = 600, endMinutes = 660)) }
        val hint = activity.getString(R.string.empty_nearest_future, 2)
        compose.waitUntil(5000) { compose.onAllNodesWithText(hint).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(hint).performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText(dateText(future)).assertIsDisplayed()
        val viewport = compose.onNodeWithTag("date-pager").fetchSemanticsNode().boundsInRoot
        assertEquals(viewport, compose.onNodeWithTag("day-page-$future").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("返回今天").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText(dateText(today)).assertIsDisplayed()
        assertEquals(viewport, compose.onNodeWithTag("day-page-$today").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("返回今天").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test fun returnTodayCancelsAnUnfinishedArrowAnimation() {
        val today = LocalDate.now()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("后一天").performClick()
        compose.mainClock.advanceTimeBy(128)
        compose.onNodeWithContentDescription("返回今天").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText(dateText(today)).assertIsDisplayed()
        val viewport = compose.onNodeWithTag("date-pager").fetchSemanticsNode().boundsInRoot
        assertEquals(viewport, compose.onNodeWithTag("day-page-$today").fetchSemanticsNode().boundsInRoot)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(today, vm().currentDate.value) }
    }

    @Test fun shortDragSnapsBackAndReturnTodayInterruptsAFling() {
        val today = LocalDate.now()
        val pager = compose.onNodeWithTag("date-pager")
        pager.performTouchInput {
            down(center)
            moveBy(Offset(-60f, 0f), 200)
            advanceEventTime(200)
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithText(dateText(today)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(today, vm().currentDate.value) }
        compose.mainClock.autoAdvance = false
        pager.performTouchInput { swipeLeft(durationMillis = 100) }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithContentDescription("返回今天").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText(dateText(today)).assertIsDisplayed()
        assertEquals(pager.fetchSemanticsNode().boundsInRoot,
            compose.onNodeWithTag("day-page-$today").fetchSemanticsNode().boundsInRoot)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(today, vm().currentDate.value) }
    }

    @Test fun nearestHintRefreshesWhenSchedulesChangeOnTheSameEmptyDate() {
        val today = LocalDate.now()
        val repository = (activity.application as ScheduleApplication).repository
        val id = runBlocking { repository.save(ScheduleEvent(
            team = "First", dayKey = today.plusDays(2).toString(), startMinutes = 600, endMinutes = 660)) }
        val firstHint = activity.getString(R.string.empty_nearest_future, 2)
        compose.waitUntil(5000) { compose.onAllNodesWithText(firstHint).fetchSemanticsNodes().isNotEmpty() }
        runBlocking {
            repository.delete(id)
            repository.save(ScheduleEvent(team = "Replacement", dayKey = today.plusDays(4).toString(),
                startMinutes = 600, endMinutes = 660))
        }
        val newHint = activity.getString(R.string.empty_nearest_future, 4)
        compose.waitUntil(5000) { compose.onAllNodesWithText(newHint).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(firstHint).assertDoesNotExist()
        compose.onNodeWithText(newHint).assertIsDisplayed()
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
