// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge.player

import de.samthedev.veloutils.protocol.MuteStatePayload
import java.time.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

public class MuteStateCache(private val clock: Clock = Clock.systemUTC()) {
    public data class State(val expiresAtEpochMillis: Long?, val reason: String)

    private val states = ConcurrentHashMap<UUID, State>()

    public fun update(payload: MuteStatePayload): Boolean {
        val playerId = runCatching { UUID.fromString(payload.playerId) }.getOrNull() ?: return false
        if (!payload.muted) {
            states.remove(playerId)
            return true
        }
        val expiresAt = payload.expiresAtEpochMillis
        if (expiresAt != null && expiresAt <= clock.millis()) {
            states.remove(playerId)
            return true
        }
        val reason = payload.reason?.trim()?.takeIf(String::isNotEmpty)?.take(1_024) ?: "No reason provided"
        states[playerId] = State(expiresAt, reason)
        return true
    }

    public fun active(playerId: UUID): State? {
        val state = states[playerId] ?: return null
        if (state.expiresAtEpochMillis != null && state.expiresAtEpochMillis <= clock.millis()) {
            states.remove(playerId, state)
            return null
        }
        return state
    }

    public fun remove(playerId: UUID) {
        states.remove(playerId)
    }
}
