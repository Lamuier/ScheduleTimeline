package com.lamuier.scheduletimeline.ui.timeline

import android.app.Application
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.lamuier.scheduletimeline.data.*
import com.lamuier.scheduletimeline.ui.theme.ScheduleTimelineTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w360dp-h900dp-mdpi")
class TimelinePerformanceComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun borderKeepsAnimationCurveWithoutRecomposingItsContentEveryFrame() {
        compose.mainClock.autoAdvance = false
        lateinit var alpha: State<Float>
        var compositions = 0
        compose.setContent {
            alpha = rememberBlinkAlpha()
            Box(Modifier.size(80.dp).animatedProgressBorder(alpha, Color.Red)) {
                SideEffect { compositions++ }
            }
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        val baseline = compositions
        compose.mainClock.advanceTimeBy(736)
        compose.waitForIdle()
        assertEquals(1f, alpha.value, 0.04f)
        compose.mainClock.advanceTimeBy(752)
        compose.waitForIdle()
        assertEquals(0.28f, alpha.value, 0.04f)
        assertEquals(baseline, compositions)
    }

    @Test fun thousandRowImportPreviewStaysBoundedAtLargeFontAndLastRowCanBeToggled() {
        val events = List(1000) { index -> ScheduleEvent(team = "Team$index", dayKey = "2026-10-15",
            startMinutes = index, endMinutes = index + 1) }
        var toggled: Int? = null
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                ScheduleTimelineTheme {
                    Box(Modifier.width(320.dp).height(360.dp)) {
                        PreviewSection(events, events.indices.toSet(), { toggled = it }, {}, "", false)
                    }
                }
            }
        }
        assertTrue(compose.onAllNodes(hasText("Team", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().size < 20)
        val output = File("build/reports/performance/ui/import-preview-font2.png")
        output.parentFile?.mkdirs()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("Team999").assertDoesNotExist()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(999)
        compose.onNodeWithText("Team999").performClick()
        assertEquals(999, toggled)
    }

    @Test fun cappedDenseOverlapOnlyComposesVisibleRowsAndLastRowRemainsSelectable() {
        val events = List(400) { index -> ScheduleEvent(id = index + 1L, team = "Team$index",
            dayKey = "2026-10-15", startMinutes = 600, endMinutes = 660) }
        var selected: Long? = null
        compose.setContent {
            ScheduleTimelineTheme {
                Box(Modifier.width(360.dp).height(720.dp)) {
                    TimelineList(TimelineBuilder.build(events), events, onSelectEvent = { selected = it.event.id })
                }
            }
        }
        assertTrue(compose.onAllNodes(hasText("Team", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().size < 30)
        val output = File("build/reports/performance/ui/dense-overlap.png")
        output.parentFile?.mkdirs()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("Team399").assertDoesNotExist()
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToIndex(399)
        compose.onNodeWithText("Team399").performClick()
        assertEquals(400L, selected)
    }
}
