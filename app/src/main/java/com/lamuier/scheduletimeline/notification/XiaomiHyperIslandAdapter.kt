package com.lamuier.scheduletimeline.notification

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import com.lamuier.scheduletimeline.R
import com.lamuier.scheduletimeline.data.EventLabels
import com.lamuier.scheduletimeline.data.ScheduleEvent
import com.lamuier.scheduletimeline.data.teamDisplay
import org.json.JSONObject

/** Adds Xiaomi HyperIsland OS3 extras while retaining the normal Android notification. */
object XiaomiHyperIslandAdapter {
    private const val BUSINESS_NAME = "schedule_timeline"
    private const val PICTURE_KEY = "schedule"
    private const val PICTURE_REF = "miui.focus.pic_schedule"
    private const val PARAM_KEY = "miui.focus.param"
    // Keep the payload protocol aligned with HyperIsland-ToolKit's OS3 template.
    private const val PARAM_V2_PROTOCOL = 3

    fun applyIfSupported(
        context: Context,
        notification: Notification,
        title: String,
        content: String,
        events: List<ScheduleEvent>,
        startMillis: Long,
        upcoming: Boolean,
    ): Boolean {
        if (events.isEmpty()) return false
        val first = events.first()
        // Capsule (摘要态) follows the official template "图文组件1 + 等宽数字文本组件":
        // area A shows icon + team name + type chip, area B shows the timer digits
        // with a short suffix. Keep the A-area title within a few characters.
        val islandTitle: String
        val islandContent: String
        if (events.size == 1) {
            islandTitle = first.teamDisplay.ifBlank { first.title }.ifBlank { title }
            islandContent = EventLabels.typeChip(first)
        } else {
            islandTitle = context.getString(R.string.notification_island_multi_title, events.size)
            islandContent = context.getString(R.string.notification_short_text)
        }
        return apply(
            context = context,
            notification = notification,
            title = title,
            content = content,
            subTitle = EventLabels.typeChip(first),
            islandTitle = islandTitle,
            islandContent = islandContent,
            timerSuffix = context.getString(
                if (upcoming) {
                    R.string.notification_island_suffix_opening
                } else {
                    R.string.notification_island_suffix_started
                },
            ),
        )
    }

    private fun apply(
        context: Context,
        notification: Notification,
        title: String,
        content: String,
        subTitle: String,
        islandTitle: String,
        islandContent: String,
        timerSuffix: String,
    ): Boolean {
        if (!XiaomiHyperIslandCapabilityReader.isSupported(context)) {
            return false
        }

        return runCatching {
            val appContext = context.applicationContext
            notification.extras.putAll(buildResourceBundle(appContext))
            notification.extras.putString(
                PARAM_KEY,
                buildJsonParam(
                    title = title,
                    content = content,
                    subTitle = subTitle,
                    islandTitle = islandTitle,
                    islandContent = islandContent,
                    timerSuffix = timerSuffix,
                ),
            )
            true
        }.getOrDefault(false)
    }

    private fun buildResourceBundle(context: Context): Bundle = Bundle().apply {
        // Standard template mode expects image and action bundles under these keys.
        // Only register pictures the payload actually references; unreferenced
        // entries (e.g. a ticker icon) are dead weight the system never reads.
        // The island renders this icon on a dark capsule, so use the colored
        // launcher bitmap — the monochrome status-bar icon reads as a white blob.
        putBundle("miui.focus.actions", Bundle())
        putBundle("miui.focus.pics", Bundle().apply {
            val icon = Icon.createWithResource(context, R.drawable.ic_island)
            putParcelable(
                PICTURE_REF,
                icon,
            )
        })
    }

    /**
     * Minimal ParamV2 payload matching HyperIsland-ToolKit's standard template output.
     * Follows the official "info" big-island template (setBigIslandInfo): only
     * imageTextInfoLeft carries content, no right-area timer digit component.
     */
    internal fun buildJsonParam(
        title: String,
        content: String,
        subTitle: String,
        islandTitle: String,
        islandContent: String,
        timerSuffix: String,
    ): String {
        // 纯图文大岛（对齐 HyperIsland-ToolKit 的 setBigIslandInfo）：
        // 仅 A 区(摄像头左侧)图标 + 团队名 + 类型 / 倒计时说明，不挂右侧计时
        // 组件。实测 HyperOS 3 在同时挂右侧计时组件时会压缩 A 区、丢弃左文字
        // （issue #2），故去掉右区计时，倒计时说明并入左区 content，秒级精度
        // 由 Status Chip 的 setWhen() 提供。enableFloat=true 让大岛可展开显示
        // 左文字（islandFirstFloat=false 不强制首次自动展开，避免打扰）。
        val picInfo = JSONObject().apply {
            put("type", 1)
            put("pic", PICTURE_REF)
        }
        val leftContent = buildString {
            if (islandContent.isNotEmpty()) append(islandContent)
            if (timerSuffix.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append(timerSuffix)
            }
        }
        val paramIsland = JSONObject().apply {
            put("islandProperty", 1)
            put("islandPriority", 2)
            put("islandOrder", false)
            put("dismissIsland", false)
            put("maxSize", false)
            put("needCloseAnimation", true)
            put("bigIslandArea", JSONObject().apply {
                // 纯图文：仅 A 区(摄像头左侧)图标 + 团队名大字 + 类型/倒计时小字。
                // 不挂任何右区计时组件，避免系统压缩 A 区导致左文字丢失。
                put("imageTextInfoLeft", JSONObject().apply {
                    put("type", 1)
                    put("picInfo", picInfo)
                    put("textInfo", JSONObject().apply {
                        put("title", islandTitle)
                        if (leftContent.isNotEmpty()) {
                            put("content", leftContent)
                        }
                    })
                })
            })
            put("smallIslandArea", JSONObject().apply {
                put("picInfo", picInfo)
            })
        }
        val baseInfo = JSONObject().apply {
            put("type", 1)
            put("title", title)
            put("subTitle", subTitle)
            put("content", content)
            put("picFunction", PICTURE_REF)
        }
        val paramV2 = JSONObject().apply {
            put("protocol", PARAM_V2_PROTOCOL)
            put("business", BUSINESS_NAME)
            put("updatable", true)
            put("ticker", title)
            put("enableFloat", true)
            put("isShowNotification", true)
            put("islandFirstFloat", false)
            put("param_island", paramIsland)
            put("baseInfo", baseInfo)
        }
        return JSONObject().apply {
            put("param_v2", paramV2)
            put("isShowNotification", true)
        }.toString()
    }
}
