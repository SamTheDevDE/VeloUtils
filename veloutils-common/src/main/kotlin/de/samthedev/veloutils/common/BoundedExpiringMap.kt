// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.common

import java.time.Clock
import java.time.Duration
import java.util.LinkedHashMap

/** Small synchronized bounded cache for request correlation and cooldowns. */
public class BoundedExpiringMap<K : Any, V : Any>(
    private val maximumSize: Int,
    private val ttl: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {
    private data class Entry<V>(val value: V, val expiresAtMillis: Long)
    private val entries = LinkedHashMap<K, Entry<V>>(16, 0.75f, true)

    init {
        require(maximumSize > 0)
        require(!ttl.isNegative && !ttl.isZero)
    }

    @Synchronized
    public fun put(key: K, value: V) {
        purgeExpired()
        entries[key] = Entry(value, Math.addExact(clock.millis(), ttl.toMillis()))
        while (entries.size > maximumSize) entries.remove(entries.keys.first())
    }

    @Synchronized
    public fun remove(key: K): V? = entries.remove(key)?.takeUnless { it.expiresAtMillis <= clock.millis() }?.value

    @Synchronized
    public operator fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (entry.expiresAtMillis <= clock.millis()) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    public fun size(): Int {
        purgeExpired()
        return entries.size
    }

    private fun purgeExpired() {
        val now = clock.millis()
        entries.entries.removeIf { it.value.expiresAtMillis <= now }
    }
}
