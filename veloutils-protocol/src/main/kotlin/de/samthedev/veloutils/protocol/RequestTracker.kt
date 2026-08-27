// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.protocol

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

public class RequestTracker(
    private val scheduler: ScheduledExecutorService,
    private val maximumPending: Int = 4_096,
) : AutoCloseable {
    private val pending = ConcurrentHashMap<String, CompletableFuture<Envelope>>()

    public fun register(requestId: String, timeout: Duration): CompletableFuture<Envelope> {
        require(!timeout.isNegative && !timeout.isZero)
        check(pending.size < maximumPending) { "Too many pending protocol requests" }
        val future = CompletableFuture<Envelope>()
        check(pending.putIfAbsent(requestId, future) == null) { "Duplicate request id" }
        val timeoutTask = scheduler.schedule({
            pending.remove(requestId, future)
            future.completeExceptionally(TimeoutException("Protocol request timed out"))
        }, timeout.toMillis(), TimeUnit.MILLISECONDS)
        future.whenComplete { _, _ ->
            timeoutTask.cancel(false)
            pending.remove(requestId, future)
        }
        return future
    }

    public fun complete(envelope: Envelope): Boolean = pending.remove(envelope.requestId)?.complete(envelope) == true

    public fun pendingCount(): Int = pending.size

    override fun close() {
        pending.values.forEach { it.cancel(false) }
        pending.clear()
    }
}
