package com.lamuier.scheduletimeline.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LeasedStateFlowCacheTest {
    @Test fun thousandVisitsReleaseJobsWhileActiveLeasesStaySharedAndFresh() = runTest {
        val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
        val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
        val sources = mutableMapOf<Int, MutableStateFlow<Int>>()
        var subscriptions = 0
        val cache = LeasedStateFlowCache(scope, { -1 }, { key: Int ->
            sources.getOrPut(key) { MutableStateFlow(key) }
                .onStart { subscriptions++ }.onCompletion { subscriptions-- }
        })
        val first = cache.acquire(0)
        val second = cache.acquire(0)
        assertSame(first.state, second.state)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { second.state.collect() }
        runCurrent()
        assertEquals(1, subscriptions)
        first.close(); first.close()
        repeat(1000) { cache.acquire(it + 1).close() }
        runCurrent()
        advanceTimeBy(5_001); runCurrent()
        assertEquals(13, parent.children.count()) // twelve idle entries plus the pinned date
        sources.getValue(0).value = 42
        runCurrent()
        assertEquals(42, second.state.value)
        collector.cancel(); second.close()
        runCurrent(); advanceTimeBy(5_001); runCurrent()
        assertEquals(0, subscriptions)
        assertEquals(12, parent.children.count())
        parent.cancel()
    }
}
