// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.integration

import de.samthedev.veloutils.common.SemanticVersion
import de.samthedev.veloutils.proxy.config.UpdateConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

public class ModrinthUpdateProvider(
    private val config: UpdateConfig,
    private val currentVersion: String,
    private val scope: CoroutineScope,
    private val logger: Logger,
) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "veloutils-updates").apply { isDaemon = true }
    }
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).executor(executor).build()
    private var job: Job? = null

    public fun start() {
        if (!config.enabled) return
        job = scope.launch {
            delay(Duration.ofSeconds(30).toMillis())
            while (isActive) {
                checkNow()
                delay(config.checkInterval.toMillis())
            }
        }
    }

    public suspend fun checkNow(): String? {
        val current = SemanticVersion.parseOrNull(currentVersion) ?: return null
        val request = HttpRequest.newBuilder(
            URI("https://api.modrinth.com/v2/project/${config.projectId}/version?include_changelog=false"),
        ).timeout(Duration.ofSeconds(10))
            .header("User-Agent", "SamTheDevDE/VeloUtils/$currentVersion (https://github.com/SamTheDevDE/VeloUtils)")
            .GET()
            .build()
        val response = runCatching { client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).awaitUpdate() }
            .getOrElse {
                logger.debug("[VeloUtils] Update check failed: {}", it.javaClass.simpleName)
                return null
            }
        if (response.statusCode() != 200 || response.body().length > 1_000_000) return null
        val versions = runCatching { Json.parseToJsonElement(response.body()).jsonArray }.getOrNull() ?: return null
        val latest = versions.mapNotNull { entry ->
            val value = entry.jsonObject
            if (value["version_type"]?.jsonPrimitive?.content != "release") return@mapNotNull null
            value["version_number"]?.jsonPrimitive?.content?.let(SemanticVersion::parseOrNull)
        }.maxOrNull() ?: return null
        return if (latest > current) latest.toString().also {
            logger.info("[VeloUtils] Update available: {} (running {}).", it, currentVersion)
        } else null
    }

    override fun close() {
        job?.cancel()
        executor.shutdownNow()
    }
}

private suspend fun <T> CompletableFuture<T>.awaitUpdate(): T = suspendCancellableCoroutine { continuation ->
    whenComplete { value, failure ->
        if (failure == null) continuation.resume(value) else continuation.resumeWithException(failure)
    }
    continuation.invokeOnCancellation { cancel(true) }
}
