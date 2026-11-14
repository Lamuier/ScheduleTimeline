package com.lamuier.scheduletimeline.ui.timeline

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import com.lamuier.scheduletimeline.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN")
class BackgroundRestrictionHelperTest {

    @Test
    fun meizuGuideUsesShowAppsecActionWithPackageName() {
        val guide = BackgroundRestrictionHelper.guideFor(
            manufacturer = "Meizu",
            packageName = "com.lamuier.scheduletimeline",
        )
        val intent = guide.intent
        assertNotNull(intent)
        assertEquals("com.meizu.safe.security.SHOW_APPSEC", intent?.action)
        assertNull(intent?.component)
        assertEquals(
            "com.lamuier.scheduletimeline",
            intent?.getStringExtra("packageName"),
        )
        assertEquals("https://dontkillmyapp.com/meizu", guide.webUrl)
    }

    @Test
    fun openOnMeizuLaunchesShowAppsecWhenResolved() {
        ShadowBuild.setManufacturer("Meizu")
        val app = RuntimeEnvironment.getApplication()
        @Suppress("DEPRECATION")
        Shadows.shadowOf(app.packageManager).apply {
            val component = android.content.ComponentName("com.meizu.safe", "AppSecActivity")
            addActivityIfNotPresent(component)
            addIntentFilterForActivity(
                component,
                IntentFilter("com.meizu.safe.security.SHOW_APPSEC").apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                },
            )
        }
        Shadows.shadowOf(app).checkActivities(true)

        BackgroundRestrictionHelper.open(app, R.string.background_keep_alive_fallback_toast)

        val started = Shadows.shadowOf(app).nextStartedActivity
        assertNotNull(started)
        assertEquals("com.meizu.safe.security.SHOW_APPSEC", started.action)
        assertNull(started.component)
        assertEquals(app.packageName, started.getStringExtra("packageName"))
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun openOnMeizuFallsBackToDontKillMyAppWhenNoActivityHandlesAction() {
        ShadowBuild.setManufacturer("Meizu")
        val app = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(app).checkActivities(true)

        BackgroundRestrictionHelper.open(app, R.string.background_keep_alive_fallback_toast)

        assertEquals(
            app.getString(R.string.background_keep_alive_fallback_toast),
            ShadowToast.getTextOfLatestToast(),
        )
        val fallback = Shadows.shadowOf(app).nextStartedActivity
        assertNull(fallback)
    }

    @Test
    fun openOnMeizuFallsBackToWebUrlWhenBrowserExists() {
        ShadowBuild.setManufacturer("Meizu")
        val app = RuntimeEnvironment.getApplication()
        @Suppress("DEPRECATION")
        Shadows.shadowOf(app.packageManager).apply {
            val browser = android.content.ComponentName("com.android.browser", "BrowserActivity")
            addActivityIfNotPresent(browser)
            addIntentFilterForActivity(
                browser,
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme("https")
                },
            )
        }
        Shadows.shadowOf(app).checkActivities(true)

        BackgroundRestrictionHelper.open(app, R.string.background_keep_alive_fallback_toast)

        assertEquals(
            app.getString(R.string.background_keep_alive_fallback_toast),
            ShadowToast.getTextOfLatestToast(),
        )
        val started = Shadows.shadowOf(app).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://dontkillmyapp.com/meizu", started.dataString)
    }
}
