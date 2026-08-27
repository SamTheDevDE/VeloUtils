// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.moderation

import de.samthedev.veloutils.api.CreatePunishment
import de.samthedev.veloutils.common.BoundedExpiringMap
import java.time.Clock
import java.time.Duration
import java.util.UUID

public data class PendingSelfPunishment(val command: String, val request: CreatePunishment)

public class SelfPunishmentConfirmations(
    ttl: Duration,
    clock: Clock = Clock.systemUTC(),
) {
    private val pending = BoundedExpiringMap<String, Pair<UUID, PendingSelfPunishment>>(1_000, ttl, clock)

    public fun begin(actorId: UUID, command: String, request: CreatePunishment): String {
        require(request.targetId == actorId) { "Self-punishment confirmation target must match its actor" }
        val token = UUID.randomUUID().toString()
        pending.put(token, actorId to PendingSelfPunishment(command.lowercase(), request))
        return token
    }

    public fun consume(actorId: UUID, command: String, token: String): PendingSelfPunishment? {
        val value = pending.remove(token) ?: return null
        return value.second.takeIf { value.first == actorId && it.command == command.lowercase() }
    }

    public fun cancel(actorId: UUID, token: String): Boolean {
        val value = pending.remove(token) ?: return false
        return value.first == actorId
    }
}
