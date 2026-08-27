// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.integration

import de.samthedev.veloutils.proxy.config.DiscordConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

public class DiscordWebhookService(
    private val config: DiscordConfig,
    private val scope: CoroutineScope,
    private val logger: Logger,
) : NetworkEventSink, AutoCloseable {
    private val executor = Executors.newFixedThreadPool(2) { task ->
        Thread(task, "veloutils-discord").apply { isDaemon = true }
    }
    private val client = HttpClient.newBuilder()
        .connectTimeout(config.connectTimeout)
        .executor(executor)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun emit(kind: NetworkEventKind, title: String, description: String) {
        val endpoint = config.webhooks[kind.configKey]?.takeIf(String::isNotEmpty) ?: return
        val body = buildJsonObject {
            put("username", "VeloUtils")
            putJsonArray("embeds") {
                add(buildJsonObject {
                    put("title", title.take(256))
                    put("description", description.take(4_000))
                    put("color", 0x7c3aed)
                })
            }
            putJsonObject("allowed_mentions") { put("parse", buildJsonArray {}) }
        }.toString()
        scope.launch { deliver(URI(endpoint), body) }
    }

    private suspend fun deliver(endpoint: URI, body: String) {
        repeat(config.maximumRetries + 1) { attempt ->
            val response = runCatching {
                val request = HttpRequest.newBuilder(endpoint)
                    .timeout(config.requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()
                client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).await()
            }.getOrNull()
            if (response != null && response.statusCode() in 200..299) return
            val retryable = response == null || response.statusCode() == 429 || response.statusCode() >= 500
            if (!retryable || attempt == config.maximumRetries) {
                logger.warn("[VeloUtils] Discord webhook delivery failed for an event; endpoint redacted.")
                return
            }
            delay((1_000L shl attempt).coerceAtMost(8_000L))
        }
    }

    override fun close() {
        executor.shutdownNow()
    }
}

private suspend fun <T> CompletableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    whenComplete { value, failure ->
        if (failure == null) continuation.resume(value) else continuation.resumeWithException(failure)
    }
    continuation.invokeOnCancellation { cancel(true) }
}
