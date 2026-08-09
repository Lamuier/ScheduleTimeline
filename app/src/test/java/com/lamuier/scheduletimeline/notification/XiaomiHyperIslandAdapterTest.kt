package com.lamuier.scheduletimeline.notification

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiHyperIslandAdapterTest {
    @Test
    fun os3RequiresDeviceProtocolAndIslandButNotFocusWhitelist() {
        assertTrue(
            XiaomiHyperIslandCapability(
                isXiaomiDevice = true,
                protocol = 3,
                islandFeatureEnabled = true,
                focusPermissionGranted = true,
            ).isOs3Supported,
        )
        assertFalse(
            XiaomiHyperIslandCapability(
                isXiaomiDevice = true,
                protocol = 2,
                islandFeatureEnabled = true,
                focusPermissionGranted = true,
            ).isOs3Supported,
        )
        assertFalse(
            XiaomiHyperIslandCapability(
                isXiaomiDevice = true,
                protocol = 3,
                islandFeatureEnabled = false,
                focusPermissionGranted = true,
            ).isOs3Supported,
        )
        assertTrue(
            XiaomiHyperIslandCapability(
                isXiaomiDevice = true,
                protocol = 3,
                islandFeatureEnabled = true,
                focusPermissionGranted = false,
            ).isOs3Supported,
        )
        assertTrue(
            XiaomiHyperIslandCapability(
                isXiaomiDevice = true,
                protocol = 0,
                islandFeatureEnabled = true,
                focusPermissionGranted = false,
            ).isOs3Supported,
        )
    }

    @Test
    fun payloadIsImageTextOnlyWithoutTimer() {
        val root = JSONObject(
            XiaomiHyperIslandAdapter.buildJsonParam(
                title = "下一项：StarDiary演出",
                content = "2026-08-05 10:00 开始",
                subTitle = "演出",
                islandTitle = "StarDiary",
                islandContent = "演出",
                timerSuffix = "后开场",
            ),
        )
        val param = root.getJSONObject("param_v2")

        assertEquals(3, param.getInt("protocol"))
        assertTrue(param.getBoolean("updatable"))
        assertEquals("演出", param.getJSONObject("baseInfo").getString("subTitle"))
        // Fields that HyperIsland-ToolKit does not emit must be absent so the
        // system parser does not reject the payload over unknown keys.
        assertFalse(param.has("notifyId"))
        assertFalse(param.has("orderId"))
        assertFalse(param.has("sequence"))
        assertFalse(param.has("tickerPic"))

        val big = param.getJSONObject("param_island").getJSONObject("bigIslandArea")
        // 右区不再挂任何计时组件，避免压缩左区文字（issue #2）
        assertFalse(big.has("fixedWidthDigitInfo"))
        assertFalse(big.has("sameWidthDigitInfo"))
        // 倒计时说明并入左区 content
        val areaAText = big.getJSONObject("imageTextInfoLeft").getJSONObject("textInfo")
        assertEquals("StarDiary", areaAText.getString("title"))
        assertEquals("演出 · 后开场", areaAText.getString("content"))
    }

    @Test
    fun capsuleAreasFollowOfficialTemplate() {
        val param = JSONObject(
            XiaomiHyperIslandAdapter.buildJsonParam(
                title = "下一项：StarDiary演出",
                content = "2026-08-05 10:00 开始",
                subTitle = "演出",
                islandTitle = "StarDiary",
                islandContent = "演出",
                timerSuffix = "后开场",
            ),
        ).getJSONObject("param_v2").getJSONObject("param_island")
        val big = param.getJSONObject("bigIslandArea")

        // A 区图文组件：图标 + 大字 + 后置小字（含倒计时说明）
        val areaA = big.getJSONObject("imageTextInfoLeft")
        assertEquals(1, areaA.getInt("type"))
        assertEquals("miui.focus.pic_schedule", areaA.getJSONObject("picInfo").getString("pic"))
        val areaAText = areaA.getJSONObject("textInfo")
        assertEquals("StarDiary", areaAText.getString("title"))
        assertEquals("演出 · 后开场", areaAText.getString("content"))

        // 右区计时组件已移除
        assertFalse(big.has("fixedWidthDigitInfo"))
        assertFalse(big.has("sameWidthDigitInfo"))

        // 小岛：应用图标
        assertEquals(
            "miui.focus.pic_schedule",
            param.getJSONObject("smallIslandArea").getJSONObject("picInfo").getString("pic"),
        )
    }

    @Test
    fun emptyOptionalCapsuleTextsAreOmitted() {
        val param = JSONObject(
            XiaomiHyperIslandAdapter.buildJsonParam(
                title = "超级岛测试",
                content = "5 分钟倒计时测试，结束后自动消失",
                subTitle = "上岛测试",
                islandTitle = "上岛测试",
                islandContent = "",
                timerSuffix = "",
            ),
        ).getJSONObject("param_v2").getJSONObject("param_island")
        val big = param.getJSONObject("bigIslandArea")

        assertFalse(
            big.getJSONObject("imageTextInfoLeft").getJSONObject("textInfo").has("content"),
        )
        // 无计时组件
        assertFalse(big.has("fixedWidthDigitInfo"))
        assertFalse(big.has("sameWidthDigitInfo"))
    }

    @Test
    fun activeScheduleFoldsStartedSuffixIntoContent() {
        val param = JSONObject(
            XiaomiHyperIslandAdapter.buildJsonParam(
                title = "StarDiary正在进行",
                content = "至 11:00",
                subTitle = "演出",
                islandTitle = "StarDiary",
                islandContent = "演出",
                timerSuffix = "已开场",
            ),
        ).getJSONObject("param_v2")

        val areaAText = param.getJSONObject("param_island")
            .getJSONObject("bigIslandArea")
            .getJSONObject("imageTextInfoLeft")
            .getJSONObject("textInfo")
        // 进行中：左区 content 折叠「类型 · 已开场」
        assertEquals("演出 · 已开场", areaAText.getString("content"))
    }

    @Test
    fun livePayloadNeverFloatsOrAutoExpands() {
        val param = JSONObject(
            XiaomiHyperIslandAdapter.buildJsonParam(
                title = "下一项：StarDiary演出",
                content = "2026-08-05 10:00 开始",
                subTitle = "演出",
                islandTitle = "StarDiary",
                islandContent = "演出",
                timerSuffix = "后开场",
            ),
        ).getJSONObject("param_v2")

        // 允许岛屿浮出 / 展开以展示左区文字，但不强制首次自动展开，避免打扰
        assertTrue(param.getBoolean("enableFloat"))
        assertFalse(param.getBoolean("islandFirstFloat"))
    }
}
