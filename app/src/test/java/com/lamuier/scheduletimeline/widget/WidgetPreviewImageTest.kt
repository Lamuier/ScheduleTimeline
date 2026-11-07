package com.lamuier.scheduletimeline.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.lamuier.scheduletimeline.R
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 小组件选择器预览图。HyperOS 等桌面只认静态 previewImage、不渲染 previewLayout，
 * 所以把两份预览布局按深浅色各画成一张 PNG，放进 drawable-nodpi / drawable-night-nodpi。
 *
 * 平时只校验能画出来；改了预览布局或配色后重新生成：
 * 设环境变量 WIDGET_PREVIEW_OUT 为 app/src/main/res 的绝对路径，再单跑本测试。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "notnight-xxhdpi")
class WidgetPreviewImageTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun renderPickerPreviewImages() {
        val out = System.getenv("WIDGET_PREVIEW_OUT")?.let(::File)
        for (night in listOf(false, true)) {
            if (night) RuntimeEnvironment.setQualifiers("+night")
            PREVIEWS.forEach { preview ->
                val bitmap = render(preview)
                assertTrue("${preview.fileName} 画出来是空白", bitmap.hasVisibleContent())
                if (out != null) {
                    val dir = File(out, if (night) "drawable-night-nodpi" else "drawable-nodpi").apply { mkdirs() }
                    File(dir, preview.fileName + ".png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
        }
    }

    private fun render(preview: Preview): Bitmap {
        val density = app.resources.displayMetrics.density
        val width = (preview.widthDp * density).toInt()
        val height = (preview.heightDp * density).toInt()
        val root = RemoteViews(app.packageName, preview.layout).apply(app, FrameLayout(app))
        // 单测用的是 Debug 资源（应用名带「Debug」）；预览图要用正式版名称
        root.textViews().filter { it.text.toString() == app.getString(R.string.app_name) }
            .forEach { it.text = releaseAppName() }
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
    }

    private fun releaseAppName(): String {
        val strings = File("src/main/res/values/strings.xml").readText()
        return Regex("""<string name="app_name">([^<]+)</string>""").find(strings)!!.groupValues[1]
    }

    private fun View.textViews(): List<TextView> = when (this) {
        is TextView -> listOf(this)
        is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).textViews() }
        else -> emptyList()
    }

    private fun Bitmap.hasVisibleContent(): Boolean {
        val center = getPixel(width / 2, height / 2)
        return (0 until width step 8).any { x -> (0 until height step 8).any { y -> getPixel(x, y) != center } }
    }

    private data class Preview(val layout: Int, val fileName: String, val widthDp: Int, val heightDp: Int)

    private companion object {
        val PREVIEWS = listOf(
            Preview(R.layout.widget_schedule_small_preview, "widget_preview_small", 180, 70),
            Preview(R.layout.widget_schedule_large_preview, "widget_preview_large", 320, 230),
        )
    }
}
