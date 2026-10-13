package com.lamuier.scheduletimeline.ui.timeline

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lamuier.scheduletimeline.data.*
import com.lamuier.scheduletimeline.ui.theme.ScheduleTimelineTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w600dp-h900dp-mdpi")
class TimelineComposeTest(private val width: Int, private val font: Float, private val dark: Boolean) {
    @get:Rule val compose = createComposeRule()
    private val event = ScheduleEvent(id = 1, team = "团队 Alpha", dayKey = "2026-10-20", startMinutes = 600, endMinutes = 601)

    private fun content(block: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                ScheduleTimelineTheme(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light) {
                    Box(Modifier.width(width.dp).height(720.dp).testTag("fixture")) { block() }
                }
            }
        }
    }

    @Test fun axisLabelsFollowActualCardHeightAndTeamRemainsVisible() {
        content {
            TimelineNode(true, true, TimelineItem.Event(event)) {
                EventCard(TimelineItem.Event(event), null, {}, Modifier.testTag("card"))
            }
        }
        assertEquals(width.toFloat(), compose.onNodeWithTag("fixture").fetchSemanticsNode().boundsInRoot.width, 1f)
        compose.onNodeWithText("团队 Alpha", useUnmergedTree = true).assertIsDisplayed()
        val start = compose.onNodeWithText("10:00", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val end = compose.onNodeWithText("10:01", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
        assertEquals(card.top, start.top, 1f)
        assertEquals(card.bottom, end.bottom, 1f)
        assertTrue(end.top > start.bottom)
        assertTrue(card.height <= 320f)
    }

    @Test fun oneMinuteSliceKeepsAtLeastOneTimeAndTeamLine() {
        val events = listOf(event, event.copy(id = 2, team = "团队 Beta", eventType = EventType.TOKUTEN.storage, completed = true))
        content { TimelineList(TimelineBuilder.build(events), events, onSelectEvent = {}) }
        compose.onNodeWithText("团队 Alpha", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("团队 Beta", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("10:00 ~ 10:01", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test fun longDetailScrollsWhileActionsStayAccessible() {
        val tokuten = event.copy(eventType = EventType.TOKUTEN.storage, note = (1..80).joinToString("\n") { "备注 $it" })
        content { EventDetailSheet(TimelineItem.Event(tokuten, note = tokuten.note), listOf(tokuten), {}, {}, {}, sheetMaxWidth = width.dp) }
        compose.onNodeWithText("完成日程").assertIsDisplayed()
        compose.onNodeWithContentDescription("编辑日程").assertIsDisplayed()
        val scroll = compose.onAllNodes(hasScrollAction()).onFirst()
        scroll.performTouchInput { swipeUp() }
        compose.onNodeWithText("完成日程").assertIsDisplayed()
    }

    @Test fun nowLineUsesTheSameMeasuredSliceAnchorAsCards() {
        val events = listOf(event.copy(endMinutes = 602), event.copy(id = 2, team = "Beta",
            startMinutes = 601, endMinutes = 603, eventType = EventType.TOKUTEN.storage))
        content { TimelineList(TimelineBuilder.build(events), events, nowMinutes = 601, onSelectEvent = {}) }
        val card = compose.onNodeWithTag("event-card-1-601").fetchSemanticsNode().boundsInRoot
        val line = compose.onNodeWithTag("now-line").fetchSemanticsNode().boundsInRoot
        assertEquals(card.top, line.top, 2f)
    }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "width={0}, font={1}, dark={2}")
        fun cases(): List<Array<Any>> = listOf(320, 360, 600).flatMap { width ->
            listOf(1f, 1.5f, 2f).flatMap { font -> listOf(false, true).map { dark -> arrayOf<Any>(width, font, dark) } }
        }
    }
}
