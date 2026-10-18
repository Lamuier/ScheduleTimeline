package com.lamuier.scheduletimeline.data

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A consumer owns a lease for as long as it may collect [state]. */
class StateFlowLease<T> internal constructor(
    val state: StateFlow<T>,
    private val release: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean()
    override fun close() {
        if (closed.compareAndSet(false, true)) release()
    }
}

/**
 * Active consumers are pinned. Only idle entries count towards the LRU limit;
 * each entry owns the sharing job as well as its last value, so eviction releases both.
 */
internal class LeasedStateFlowCache<K, V>(
    private val scope: CoroutineScope,
    private val initialValue: (K) -> V,
    private val source: (K) -> Flow<V>,
    private val idleCapacity: Int = 12,
    private val stopTimeoutMillis: Long = 5_000,
) {
    private class Entry<V>(val job: Job, val scope: CoroutineScope, val state: StateFlow<V>) {
        var leases = 0
        var idle = false
        var retirement: Job? = null
    }

    private val lock = Any()
    private val entries = LinkedHashMap<K, Entry<V>>(16, 0.75f, true)

    init {
        require(idleCapacity >= 0 && stopTimeoutMillis >= 0)
    }

    fun acquire(key: K): StateFlowLease<V> = synchronized(lock) {
        val entry = entries.getOrPut(key) {
            val job = SupervisorJob(scope.coroutineContext[Job])
            val entryScope = CoroutineScope(scope.coroutineContext + job)
            Entry(job, entryScope, source(key).stateIn(
                entryScope, SharingStarted.WhileSubscribed(stopTimeoutMillis), initialValue(key),
            ))
        }
        entry.retirement?.cancel()
        entry.retirement = null
        entry.idle = false
        entry.leases++
        StateFlowLease(entry.state) { release(key, entry) }
    }

    private fun release(key: K, entry: Entry<V>) = synchronized(lock) {
        check(entry.leases > 0)
        entry.leases--
        if (entry.leases == 0) {
            entry.retirement = entry.scope.launch {
                delay(stopTimeoutMillis)
                synchronized(lock) {
                    if (entry.leases == 0) {
                        entry.idle = true
                        // Access on release makes recent visits most likely to survive.
                        entries[key]
                        trimIdleEntries()
                    }
                }
            }
        }
    }

    private fun trimIdleEntries() {
        var excess = entries.values.count { it.idle } - idleCapacity
        if (excess <= 0) return
        val iterator = entries.entries.iterator()
        while (iterator.hasNext() && excess > 0) {
            val entry = iterator.next().value
            if (entry.idle && entry.leases == 0) {
                iterator.remove()
                entry.job.cancel()
                excess--
            }
        }
    }
}
