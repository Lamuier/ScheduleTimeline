package com.lamuier.scheduletimeline

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon

/**
 * 桌面快捷方式（Dynamic App Shortcuts）：长按图标提供
 * 「新增日程 / 今日日程 / 最近日程 / 批量导入」四个入口。
 *
 * 不用静态 shortcuts.xml 的原因：其 `<intent>` 无法同时兼容 Release 与
 * Debug（`.debug` 后缀）两个包名——res/xml 不支持 `${applicationId}` 占位符，
 * 写死包名则 Debug 变体点击无响应；省略 targetPackage 在部分启动器上同样
 * 解析失败。故改为进程启动时动态注册，用 packageName 构造显式 intent，
 * 任意构建变体下均可可靠启动。
 */
object AppShortcuts {
    const val ACTION_ADD_EVENT =
        "com.lamuier.scheduletimeline.action.ADD_EVENT"
    const val ACTION_TODAY =
        "com.lamuier.scheduletimeline.action.TODAY"
    const val ACTION_NEXT_SCHEDULED =
        "com.lamuier.scheduletimeline.action.NEXT_SCHEDULED"
    const val ACTION_IMPORT =
        "com.lamuier.scheduletimeline.action.IMPORT"

    private const val ID_ADD_EVENT = "add_event"
    private const val ID_TODAY = "today"
    private const val ID_NEXT_SCHEDULED = "next_scheduled"
    private const val ID_IMPORT = "import_csv"

    /** 每次进程启动刷新注册，保证标签 / 图标 / intent 与当前构建一致。 */
    fun register(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        manager.dynamicShortcuts = listOf(
            shortcut(
                context = context,
                id = ID_ADD_EVENT,
                shortLabelRes = R.string.shortcut_add_short,
                longLabelRes = R.string.shortcut_add_long,
                iconRes = R.drawable.ic_shortcut_add,
                action = ACTION_ADD_EVENT,
            ),
            shortcut(
                context = context,
                id = ID_TODAY,
                shortLabelRes = R.string.shortcut_today_short,
                longLabelRes = R.string.shortcut_today_long,
                iconRes = R.drawable.ic_shortcut_today,
                action = ACTION_TODAY,
            ),
            shortcut(
                context = context,
                id = ID_NEXT_SCHEDULED,
                shortLabelRes = R.string.shortcut_next_short,
                longLabelRes = R.string.shortcut_next_long,
                iconRes = R.drawable.ic_shortcut_next,
                action = ACTION_NEXT_SCHEDULED,
            ),
            shortcut(
                context = context,
                id = ID_IMPORT,
                shortLabelRes = R.string.shortcut_import_short,
                longLabelRes = R.string.shortcut_import_long,
                iconRes = R.drawable.ic_shortcut_import,
                action = ACTION_IMPORT,
            ),
        )
    }

    /** 上报用户从桌面长按触发快捷方式，供启动器排序与满足 Android Lint `ReportShortcutUsage`。 */
    fun reportUsed(context: Context, action: String?) {
        val id = when (action) {
            ACTION_ADD_EVENT -> ID_ADD_EVENT
            ACTION_TODAY -> ID_TODAY
            ACTION_NEXT_SCHEDULED -> ID_NEXT_SCHEDULED
            ACTION_IMPORT -> ID_IMPORT
            else -> return
        }
        context.getSystemService(ShortcutManager::class.java)?.reportShortcutUsed(id)
    }

    private fun shortcut(
        context: Context,
        id: String,
        shortLabelRes: Int,
        longLabelRes: Int,
        iconRes: Int,
        action: String,
    ): ShortcutInfo = ShortcutInfo.Builder(context, id)
        .setShortLabel(context.getString(shortLabelRes))
        .setLongLabel(context.getString(longLabelRes))
        .setIcon(Icon.createWithResource(context, iconRes))
        // 显式 intent：组件直接指向本变体的 MainActivity，不依赖启动器解析
        .setIntent(Intent(context, MainActivity::class.java).setAction(action))
        .build()
}
