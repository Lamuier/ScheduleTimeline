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
    fun payloadMatchesToolKitCountdownTemplate() {
        val root = JSONObject(
            XiaomiHyperIslandAdapter.buildJsonParam(
                title = "下一项：StarDiary演出",
                content = "2026-08-05 10:00 开始",
                subTitle = "演出",
                islandTitle = "StarDiary",
                islandContent = "演出",
                timerSuffix = "后开场",
                startMillis = 1_800_000_000_000L,
                upcoming = true,
                nowMillis = 1_799_999_000_000L,
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
        val timer = param.getJSONObject("param_island")
            .getJSONObject("bigIslandArea")
            .getJSONObject("sameWidthDigitInfo")
            .getJSONObject("timerInfo")
        assertEquals(-1, timer.getInt("timerType"))
        assertEquals(1_799_999_000_000L, timer.getLong("timerTotal"))
        assertEquals(1_799_999_000_000L, timer.getLong("timerSystemCurrent"))
        assertTrue(timer.getLong("timerWhen") > timer.getLong("timerSystemCurrent"))
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
                startMillis = 1_800_000_000_000L,
                upcoming = true,
                nowMillis = 1_799_999_000_000L,
            ),
        ).getJSONObject("param_v2").getJSONObject("param_island")
        val big = param.getJSONObject("bigIslandArea")

        // A 区图文组件1：图标 + 大字 + 后置小字
        val areaA = big.getJSONObject("imageTextInfoLeft")
        assertEquals(1, areaA.getInt("type"))
        assertEquals("miui.focus.pic_schedule", areaA.getJSONObject("picInfo").getString("pic"))
        val areaAText = areaA.getJSONObject("textInfo")
        assertEquals("StarDiary", areaAText.getString("title"))
        assertEquals("演出", areaAText.getString("content"))

        // B 区等宽数字组件：计时器 + 小字后缀
        val areaB = big.getJSONObject("sameWidthDigitInfo")
        assertEquals("后开场", areaB.getString("content"))
        assertTrue(areaB.has("timerInfo"))

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
                startMillis = 1_800_000_000_000L,
                upcoming = true,
                nowMillis = 1_799_999_000_000L,
            ),
        ).getJSONObject("param_v2").getJSONObject("param_island")
        val big = param.getJSONObject("bigIslandArea")

        assertFalse(
            big.getJSONObject("imageTextInfoLeft").getJSONObject("textInfo").has("content"),
        )
        assertFalse(big.getJSONObject("sameWidthDigitInfo").has("content"))
    }

    @Test
    fun payloadUsesCountUpForActiveSchedule() {
        val param = JSONObject(
            XiaomiHyperIslandAdapter.buildJsonParam(
                title = "StarDiary正在进行",
                content = "至 11:00",
                subTitle = "演出",
                islandTitle = "StarDiary",
                islandContent = "演出",
                timerSuffix = "已开场",
                startMillis = 1_800_000_000_000L,
                upcoming = false,
                nowMillis = 1_800_000_600_000L,
            ),
        ).getJSONObject("param_v2")

        val timer = param.getJSONObject("param_island")
            .getJSONObject("bigIslandArea")
            .getJSONObject("sameWidthDigitInfo")
            .getJSONObject("timerInfo")
        assertEquals(1, timer.getInt("timerType"))
        assertEquals(1_800_000_000_000L, timer.getLong("timerTotal"))
        assertEquals(1_800_000_000_000L, timer.getLong("timerSystemCurrent"))
        assertEquals(timer.getLong("timerWhen"), timer.getLong("timerSystemCurrent"))
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
                startMillis = 1_800_000_000_000L,
                upcoming = true,
                nowMillis = 1_799_999_000_000L,
            ),
        ).getJSONObject("param_v2")

        // 常驻 live 通知不应首次浮出或自动展开岛屿，避免打扰
        assertFalse(param.getBoolean("enableFloat"))
        assertFalse(param.getBoolean("islandFirstFloat"))
    }
}
