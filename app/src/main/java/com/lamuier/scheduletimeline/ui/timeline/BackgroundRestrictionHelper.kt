package com.lamuier.scheduletimeline.ui.timeline

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast

/**
 * 各厂商「后台保活 / 自启动 / 电池优化」设置页引导。
 *
 * 数据综合自开源项目 backgroundable-android（厂商 Intent 清单）与
 * Don't Kill My App（dontkillmyapp.com，厂商杀后台评级与步骤教程）。
 *
 * 策略：按 [Build.MANUFACTURER] 匹配厂商，优先用 Intent 直接跳到该厂商
 * 的「自启动 / 应用启动管理 / 电池」设置页；Intent 失败（ROM 变更或非该厂商）
 * 时回退到 dontkillmyapp.com 对应厂商页，里面含详细截图与步骤。
 *
 * 「下一项通知不更新 / 进度条不动 / 开机后通知不恢复」多半是厂商杀后台
 * 导致 AlarmManager 被冻结——用户进对应设置页把本应用加白名单即可恢复。
 */
object BackgroundRestrictionHelper {

    /** 厂商匹配结果：含本地化标签、可跳转的设置页 Intent、回退网页 URL。 */
    private data class Guide(
        val label: String,
        val intent: Intent? = null,
        val webUrl: String,
    )

    /** 当前设备的厂商引导，未识别时 webUrl 指向 dontkillmyapp 首页。 */
    private fun guideFor(manufacturer: String = Build.MANUFACTURER): Guide {
        val m = manufacturer.lowercase().trim()
        return when {
            m == "xiaomi" || m == "redmi" -> Guide(
                label = "小米 / Redmi",
                intent = Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity",
                    ),
                ),
                webUrl = "https://dontkillmyapp.com/xiaomi",
            )

            m == "huawei" -> Guide(
                label = "华为",
                intent = Intent().setComponent(
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                    ),
                ),
                webUrl = "https://dontkillmyapp.com/huawei",
            )

            m == "honor" -> Guide(
                label = "荣耀",
                // MagicOS / Magic UI 沿用华为系统管家包名
                intent = Intent().setComponent(
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                    ),
                ),
                webUrl = "https://dontkillmyapp.com/honor",
            )

            m == "samsung" -> Guide(
                label = "三星",
                intent = Intent().setComponent(
                    ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity",
                    ),
                ),
                webUrl = "https://dontkillmyapp.com/samsung",
            )

            m == "oppo" || m == "realme" -> Guide(
                label = if (m == "realme") "realme" else "OPPO",
                intent = Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                    ),
                ),
                webUrl = "https://dontkillmyapp.com/realme",
            )

            m == "vivo" || m == "iqoo" -> Guide(
                label = "vivo / iQOO",
                intent = Intent().setComponent(
                    ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.BgStartUpAppsManagerActivity",
                    ),
                ),
                webUrl = "https://dontkillmyapp.com/vivo",
            )

            m == "oneplus" -> Guide(
                label = "一加",
                // ColorOS 体系，沿用 OPPO 安全中心
                intent = Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                    ),
                ),
                webUrl = "https://dontkillmyapp.com/oneplus",
            )

            m == "meizu" -> Guide(
                label = "魅族",
                intent = Intent().setComponent(
                    ComponentName(
                        "com.meizu.safe",
                        "com.meizu.safe.security.SHOW_APPSEC",
                    ),
                ),
                webUrl = "https://dontkillmyapp.com/meizu",
            )

            else -> Guide(
                label = manufacturer,
                webUrl = "https://dontkillmyapp.com",
            )
        }
    }

    /**
     * 打开厂商后台设置页：优先 startActivity(intent)，失败时用浏览器打开网页回退。
     *
     * @param fallbackToastResId Intent 跳转失败、回退到网页前显示的提示文案资源 ID
     *                           （如「未识别到厂商设置页，已打开在线教程」）
     */
    fun open(
        context: Context,
        fallbackToastResId: Int,
    ) {
        val guide = guideFor()
        val intent = guide.intent?.apply {
            // 跳到的是系统设置页，不需要本应用额外声明权限
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val jumped = intent != null && runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)

        if (jumped) return

        // Intent 失败或未识别 → 浏览器打开网页
        Toast.makeText(context, fallbackToastResId, Toast.LENGTH_SHORT).show()
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(guide.webUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(webIntent) }
    }
}
