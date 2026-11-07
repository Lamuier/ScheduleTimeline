package com.lamuier.scheduletimeline.notification

import android.app.Application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BroadcastWorkTest {
    @Test fun slowWorkReleasesBroadcastAtBudgetAndStillFinishes() = runTest {
        val gate = CompletableDeferred<Unit>()
        var released = false
        var done = false
        BroadcastWork.launch("slow", finish = { released = true }, workScope = backgroundScope) {
            gate.await()
            done = true
        }
        advanceTimeBy(BroadcastWork.WAIT_BUDGET_MS - 1)
        assertFalse(released)
        advanceTimeBy(2)
        // 赶在前台广播 10 秒超时之前交还；刷新没有被取消
        assertTrue(released)
        assertFalse(done)
        gate.complete(Unit)
        runCurrent()
        assertTrue(done)
    }

    @Test fun finishedOrFailedWorkReleasesBroadcastRightAway() = runTest {
        var released = 0
        BroadcastWork.launch("ok", finish = { released++ }, workScope = backgroundScope) { }
        BroadcastWork.launch("failed", finish = { released++ }, workScope = backgroundScope) {
            error("database locked")
        }
        runCurrent()
        assertEquals(2, released)
        assertEquals(0L, currentTime)
    }
}
