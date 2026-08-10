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
        // 右区不挂 digit 计时组件，避免压缩左区文字（issue #2）
        assertFalse(big.has("fixedWidthDigitInfo"))
        assertFalse(big.has("sameWidthDigitInfo"))
        // 左区仅团队名；类型 / 倒计时说明在右区图文
        val areaAText = big.getJSONObject("imageTextInfoLeft").getJSONObject("textInfo")
        assertEquals("StarDiary", areaAText.getString("title"))
        assertFalse(areaAText.has("content"))
        val areaBText = big.getJSONObject("imageTextInfoRight").getJSONObject("textInfo")
        assertEquals("演出", areaBText.getString("title"))
        assertEquals("后开场", areaBText.getString("content"))
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

        // A 区：图标 + 团队名；B 区：类型 + 倒计时说明（非 digit 计时）
        val areaA = big.getJSONObject("imageTextInfoLeft")
        assertEquals(1, areaA.getInt("type"))
        assertEquals("miui.focus.pic_schedule", areaA.getJSONObject("picInfo").getString("pic"))
        assertEquals("StarDiary", areaA.getJSONObject("textInfo").getString("title"))
        assertEquals(2, big.getJSONObject("imageTextInfoRight").getInt("type"))
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
        // 无类型 / 后缀时不挂右区，也不挂 digit 计时
        assertFalse(big.has("imageTextInfoRight"))
        assertFalse(big.has("fixedWidthDigitInfo"))
        assertFalse(big.has("sameWidthDigitInfo"))
    }

    @Test
    fun activeSchedulePutsStartedSuffixOnRight() {
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

        val big = param.getJSONObject("param_island").getJSONObject("bigIslandArea")
        assertEquals(
            "StarDiary",
            big.getJSONObject("imageTextInfoLeft").getJSONObject("textInfo").getString("title"),
        )
        val areaBText = big.getJSONObject("imageTextInfoRight").getJSONObject("textInfo")
        assertEquals("演出", areaBText.getString("title"))
        assertEquals("已开场", areaBText.getString("content"))
    }

    @Test
    fun livePayloadFloatsAndAutoExpandsOnce() {
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

        // 对齐 ToolKit 默认：允许浮出，且首次自动展开以露出左右图文
        assertTrue(param.getBoolean("enableFloat"))
        assertTrue(param.getBoolean("islandFirstFloat"))
    }
}
