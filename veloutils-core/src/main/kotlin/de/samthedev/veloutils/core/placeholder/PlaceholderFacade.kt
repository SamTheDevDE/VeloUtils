// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.core.placeholder

import de.samthedev.veloutils.common.escapeMiniMessage
import de.samthedev.veloutils.api.PlaceholderContext
import de.samthedev.veloutils.api.PlaceholderProvider
import de.samthedev.veloutils.api.PlaceholderService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Thread-safe facade for native, backend snapshot, and addon-provided plain-text values. */
public class PlaceholderFacade(
    private val maximumSnapshots: Int = 2_048,
    private val maximumProviders: Int = 128,
    private val maximumValuesPerSource: Int = 256,
    private val snapshotTtl: Duration = Duration.ofSeconds(30),
    private val clock: Clock = Clock.systemUTC(),
) : PlaceholderService {
    private data class Snapshot(val values: Map<String, String>, val expiresAt: Instant)
    private val providers = ConcurrentHashMap<String, PlaceholderProvider>()
    private val snapshots = object : LinkedHashMap<UUID, Snapshot>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, Snapshot>?): Boolean = size > maximumSnapshots
    }
    private val snapshotLock = Any()

    init {
        require(maximumSnapshots > 0)
        require(maximumProviders > 0)
        require(maximumValuesPerSource > 0)
        require(!snapshotTtl.isNegative && !snapshotTtl.isZero)
    }

    override fun register(namespace: String, provider: PlaceholderProvider): AutoCloseable {
        require(namespace.matches(Regex("[a-z][a-z0-9_-]{0,31}"))) { "Invalid placeholder namespace: $namespace" }
        check(providers.size < maximumProviders) { "Placeholder provider limit reached" }
        check(providers.putIfAbsent(namespace, provider) == null) { "Placeholder namespace is already registered: $namespace" }
        return AutoCloseable { providers.remove(namespace, provider) }
    }

    public fun updateSnapshot(playerId: UUID, values: Map<String, String>) {
        val safe = values.entries.asSequence().filter { validKey(it.key) }.take(maximumValuesPerSource)
            .associate { (key, value) -> key to value.take(MAXIMUM_VALUE_LENGTH) }
        synchronized(snapshotLock) { snapshots[playerId] = Snapshot(safe, clock.instant().plus(snapshotTtl)) }
    }

    public fun removeSnapshot(playerId: UUID) {
        synchronized(snapshotLock) { snapshots.remove(playerId) }
    }

    override fun resolve(context: PlaceholderContext): Map<String, String> {
        val result = mutableMapOf<String, String>()
        context.playerId?.let { playerId ->
            synchronized(snapshotLock) {
                val snapshot = snapshots[playerId]
                if (snapshot != null && snapshot.expiresAt.isAfter(clock.instant())) result.putAll(snapshot.values)
                else if (snapshot != null) snapshots.remove(playerId)
            }
        }
        providers.entries.sortedBy(Map.Entry<String, PlaceholderProvider>::key).forEach { (namespace, provider) ->
            runCatching { provider.resolve(context) }.getOrDefault(emptyMap()).entries.asSequence()
                .filter { validKey(it.key) }.take(maximumValuesPerSource).forEach { (key, value) ->
                result["${namespace}_$key"] = value.take(MAXIMUM_VALUE_LENGTH)
            }
        }
        return result.toMap()
    }

    public companion object {
        private const val MAXIMUM_VALUE_LENGTH: Int = 2_048
        private fun validKey(key: String): Boolean = key.matches(Regex("[a-z][a-z0-9_.-]{0,63}"))
    }
}

/** Tokenizes placeholders once and caches unchanged rendered output. Dynamic values are always MiniMessage-escaped. */
public class TemplateRenderer(
    template: String,
    private val miniMessage: MiniMessage = MiniMessage.miniMessage(),
    private val maximumCachedResults: Int = 256,
) {
    private sealed interface Segment {
        data class Literal(val value: String) : Segment
        data class Placeholder(val key: String) : Segment
    }

    private val segments: List<Segment> = tokenize(template)
    private val cache = object : LinkedHashMap<List<Pair<String, String>>, Component>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<Pair<String, String>>, Component>?): Boolean =
            size > maximumCachedResults
    }

    init { require(maximumCachedResults > 0) }

    /** Placeholder keys discovered while tokenizing the immutable template. */
    public fun requiredKeys(): Set<String> = segments.asSequence()
        .filterIsInstance<Segment.Placeholder>()
        .mapTo(linkedSetOf(), Segment.Placeholder::key)

    public fun render(values: Map<String, String>): Component {
        val used = requiredKeys()
            .map { it to values.getOrDefault(it, "") }
        synchronized(cache) { cache[used]?.let { return it } }
        val serialized = buildString {
            segments.forEach { segment ->
                when (segment) {
                    is Segment.Literal -> append(segment.value)
                    is Segment.Placeholder -> append(escapeMiniMessage(values.getOrDefault(segment.key, "")))
                }
            }
        }
        return miniMessage.deserialize(serialized).also { synchronized(cache) { cache[used] = it } }
    }

    private companion object {
        val PLACEHOLDER: Regex = Regex("\\{([a-z][a-z0-9_.-]{0,63})}")
        fun tokenize(template: String): List<Segment> {
            val result = mutableListOf<Segment>()
            var cursor = 0
            PLACEHOLDER.findAll(template).forEach { match ->
                if (match.range.first > cursor) result += Segment.Literal(template.substring(cursor, match.range.first))
                result += Segment.Placeholder(match.groupValues[1])
                cursor = match.range.last + 1
            }
            if (cursor < template.length) result += Segment.Literal(template.substring(cursor))
            return result
        }
    }
}
