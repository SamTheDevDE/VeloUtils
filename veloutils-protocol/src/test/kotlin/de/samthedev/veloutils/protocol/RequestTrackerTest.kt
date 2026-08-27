package de.samthedev.veloutils.protocol

import kotlinx.serialization.json.buildJsonObject
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RequestTrackerTest {
    @Test fun `correlates response by request id`() {
        Executors.newSingleThreadScheduledExecutor().use { executor ->
            RequestTracker(executor).use { tracker ->
                val id = newRequestId()
                val future = tracker.register(id, Duration.ofSeconds(1))
                val packet = Envelope(1, "HEARTBEAT", id, System.currentTimeMillis(), newNonce(), buildJsonObject {})
                assertTrue(tracker.complete(packet))
                assertEquals(packet, future.get(1, TimeUnit.SECONDS))
                assertEquals(0, tracker.pendingCount())
            }
        }
    }

    @Test fun `request timeout completes exceptionally`() {
        Executors.newSingleThreadScheduledExecutor().use { executor ->
            RequestTracker(executor).use { tracker ->
                val future = tracker.register(newRequestId(), Duration.ofMillis(10))
                val exception = assertFailsWith<java.util.concurrent.ExecutionException> { future.get(1, TimeUnit.SECONDS) }
                assertTrue(exception.cause is TimeoutException)
            }
        }
    }
}
