package com.lamuier.scheduletimeline.notification

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoalescingRefreshTest {
    @Test fun twentyInFlightRequestsShareOneFollowUpAndKeepLatestDataAndForce() = runTest {
        val gate = CompletableDeferred<Unit>()
        val seen = mutableListOf<Pair<Int, Boolean>>()
        var version = 0
        val refresh = CoalescingRefresh(backgroundScope) { force ->
            seen += version to force
            if (seen.size == 1) gate.await()
        }
        val first = async { refresh.request() }
        runCurrent()
        val followers = List(20) { index -> async { refresh.request(force = index == 10) } }
        runCurrent()
        assertTrue(followers.none { it.isCompleted })
        version = 1
        gate.complete(Unit)
        runCurrent()
        first.await(); followers.awaitAll()
        assertEquals(listOf(0 to false, 1 to true), seen)
        refresh.request()
        assertEquals(3, seen.size)
    }

    @Test fun failedPassNotifiesWaitersAndLaterRequestsCanRetry() = runTest {
        var attempts = 0
        val refresh = CoalescingRefresh(backgroundScope) { if (++attempts == 1) error("write failed") }
        try { refresh.request(); fail("Expected failure") } catch (_: IllegalStateException) { }
        refresh.request()
        assertEquals(2, attempts)
    }
}
