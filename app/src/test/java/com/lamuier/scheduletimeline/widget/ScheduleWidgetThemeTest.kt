package com.lamuier.scheduletimeline.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.lamuier.scheduletimeline.R
import com.lamuier.scheduletimeline.data.EventType
import com.lamuier.scheduletimeline.data.NotificationSchedule
import com.lamuier.scheduletimeline.data.ScheduleEvent
import com.lamuier.scheduletimeline.data.TokutenKind
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow

/**
 * 桌面切换深浅色时，应用进程不一定在跑：桌面只会拿已有的 RemoteViews 重新套用。
 * 文字颜色必须在套用时按当前主题解析，不能是生成时写死的值。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, shadows = [RecordingWidgetManager::class],
    qualifiers = "notnight")
class ScheduleWidgetThemeTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun summaryWidgetTextFollowsNightModeWithoutRedraw() = runBlocking {
        val shadow = Shadow.extract<RecordingWidgetManager>(AppWidgetManager.getInstance(app))
        shadow.addBoundWidget(1, info(ScheduleWidgetProviderSmall::class.java))
        shadow.addBoundWidget(2, info(ScheduleWidgetProviderLarge::class.java))
        val events = listOf(ScheduleEvent(id = 1, team = "A", dayKey = "2026-10-15",
            startMinutes = 600, endMinutes = 660))
        val snapshot = NotificationSchedule.snapshot(events,
            Instant.parse("2026-10-15T10:01:00Z").toEpochMilli(), ZoneOffset.UTC)
        ScheduleWidgetUpdater.refresh(app, events, snapshot, ScheduleWidgetUpdater.targets(app), force = true)
        // 只在浅色下生成一次，之后不再刷新
        val small = shadow.lastViews.getValue(1)
        val large = shadow.lastViews.getValue(2)

        forEachTheme {
            small.assertTextColors(
                R.id.widget_small_status to R.color.widget_primary,
                R.id.widget_small_date to R.color.widget_on_surface_variant,
                R.id.widget_small_title to R.color.widget_on_surface,
                R.id.widget_small_subtitle to R.color.widget_on_surface_variant,
            )
            large.assertTextColors(
                R.id.widget_large_title to R.color.widget_primary,
                R.id.widget_large_date to R.color.widget_on_surface,
                R.id.widget_large_summary to R.color.widget_on_surface_variant,
                R.id.widget_empty to R.color.widget_on_surface_variant,
            )
        }
    }

    @Test fun listItemTextAndTypeChipFollowNightModeWithoutRedraw() {
        val performance = scheduleWidgetItem(app, ScheduleEvent(id = 1, team = "A", dayKey = "2026-10-15",
            startMinutes = 600, endMinutes = 660))
        val tokuten = scheduleWidgetItem(app, ScheduleEvent(id = 2, team = "B", dayKey = "2026-10-15",
            startMinutes = 700, endMinutes = 720, eventType = EventType.TOKUTEN.storage,
            tokutenKind = TokutenKind.PRE.storage))

        forEachTheme {
            performance.assertTextColors(
                R.id.widget_item_time to R.color.widget_primary,
                R.id.widget_item_title to R.color.widget_on_surface,
                R.id.widget_item_chip_performance to R.color.widget_chip_performance,
            )
            tokuten.assertTextColors(R.id.widget_item_chip_tokuten to R.color.widget_chip_tokuten)
        }
    }

    @Test fun recycledListItemShowsOnlyTheChipOfItsOwnType() {
        val root = scheduleWidgetItem(app, ScheduleEvent(id = 1, team = "A", dayKey = "2026-10-15",
            startMinutes = 600, endMinutes = 660)).apply(app, FrameLayout(app))
        assertChips(root, visible = R.id.widget_item_chip_performance, gone = R.id.widget_item_chip_tokuten, mark = "演")
        // 宿主复用同一个列表项视图时，只重放新一项的操作
        scheduleWidgetItem(app, ScheduleEvent(id = 2, team = "B", dayKey = "2026-10-15",
            startMinutes = 700, endMinutes = 720, eventType = EventType.TOKUTEN.storage,
            tokutenKind = TokutenKind.FINAL.storage)).reapply(app, root)
        assertChips(root, visible = R.id.widget_item_chip_tokuten, gone = R.id.widget_item_chip_performance, mark = "特")
    }

    @Test fun pickerPreviewsInflateAsRemoteViewsAndFollowNightMode() {
        // 选择器按 RemoteViews 规则套用 previewLayout，不受支持的 View 会让预览整块空白
        val previews = listOf(R.layout.widget_schedule_small_preview, R.layout.widget_schedule_large_preview)
        forEachTheme {
            previews.forEach { layout ->
                val root = RemoteViews(app.packageName, layout).apply(app, FrameLayout(app))
                val texts = root.allTextViews()
                assertTrue(texts.isNotEmpty())
                assertTrue(texts.any { it.currentTextColor == app.getColor(R.color.widget_on_surface) })
            }
        }
    }

    private fun View.allTextViews(): List<TextView> = when (this) {
        is TextView -> listOf(this)
        is android.view.ViewGroup -> (0 until childCount).flatMap { getChildAt(it).allTextViews() }
        else -> emptyList()
    }

    /** 先浅色后深色各跑一遍；并确认两套颜色确实不同，避免切换没生效时测试空过。 */
    private fun forEachTheme(check: () -> Unit) {
        check()
        val light = app.getColor(R.color.widget_on_surface)
        RuntimeEnvironment.setQualifiers("+night")
        assertNotEquals(light, app.getColor(R.color.widget_on_surface))
        check()
    }

    /** 模拟桌面换主题后重新套用同一份 RemoteViews。 */
    private fun RemoteViews.assertTextColors(vararg expected: Pair<Int, Int>) {
        val root = apply(app, FrameLayout(app))
        expected.forEach { (viewId, colorRes) ->
            assertEquals(app.resources.getResourceEntryName(viewId),
                app.getColor(colorRes), root.findViewById<TextView>(viewId).currentTextColor)
        }
    }

    private fun assertChips(root: View, visible: Int, gone: Int, mark: String) {
        assertEquals(View.VISIBLE, root.findViewById<View>(visible).visibility)
        assertEquals(View.GONE, root.findViewById<View>(gone).visibility)
        assertEquals(mark, root.findViewById<TextView>(visible).text.toString())
    }

    private fun info(provider: Class<*>) = AppWidgetProviderInfo().apply {
        this.provider = ComponentName(app, provider)
    }
}
