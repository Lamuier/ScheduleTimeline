package com.lamuier.scheduletimeline.notification

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Every waiter completes after a pass covering its request; requests during a pass share the next. */
internal class CoalescingRefresh(
    private val scope: CoroutineScope,
    private val action: suspend (force: Boolean) -> Unit,
) {
    private val lock = Any()
    private var running = false
    private var generation = 0L
    private var forcePending = false
    private val waiters = mutableListOf<Pair<Long, CompletableDeferred<Unit>>>()

    suspend fun request(force: Boolean = false) {
        val done = CompletableDeferred<Unit>()
        synchronized(lock) {
            waiters += ++generation to done
            forcePending = forcePending || force
            if (!running) {
                running = true
                scope.launch { drain() }
            }
        }
        done.await()
    }

    private suspend fun drain() {
        while (true) {
            val (through, force) = synchronized(lock) {
                (generation to forcePending).also { forcePending = false }
            }
            val result = runCatching { action(force) }
            val (finished, again) = synchronized(lock) {
                val cancelled = result.exceptionOrNull() is CancellationException
                val finished = waiters.filter { cancelled || it.first <= through }
                waiters.removeAll(finished.toSet())
                val again = !cancelled && generation > through
                if (!again) running = false
                finished to again
            }
            finished.forEach { (_, waiter) ->
                result.fold({ waiter.complete(Unit) }, { waiter.completeExceptionally(it) })
            }
            if (!again) return
        }
    }
}
