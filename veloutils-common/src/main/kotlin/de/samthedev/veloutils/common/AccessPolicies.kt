// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.common

import de.samthedev.veloutils.api.AccessDecision
import de.samthedev.veloutils.api.MaintenanceSnapshot
import java.util.UUID

public data class ServerAccessRule(
    val permission: String?,
    val allowlist: Set<UUID> = emptySet(),
    val fallbackServers: List<String> = emptyList(),
    val denialMessageKey: String = "server-access.denied",
)

public class MaintenanceAccessPolicy(
    private val bypassPermission: String = "veloutils.maintenance.bypass",
) {
    public fun decide(
        snapshot: MaintenanceSnapshot,
        playerId: UUID,
        permissions: Set<String>,
        server: String?,
    ): AccessDecision {
        val active = snapshot.global != null || (server != null && snapshot.servers.containsKey(server.lowercase()))
        if (!active || bypassPermission in permissions || playerId in snapshot.allowedPlayers) return AccessDecision.Allowed
        return AccessDecision.Denied("maintenance.denied")
    }
}

public class ServerAccessPolicy(
    private val bypassPermission: String = "veloutils.server-access.bypass",
) {
    public fun decide(
        playerId: UUID,
        permissions: Set<String>,
        rule: ServerAccessRule?,
    ): AccessDecision {
        if (rule == null || bypassPermission in permissions || playerId in rule.allowlist) return AccessDecision.Allowed
        if (rule.permission == null || rule.permission in permissions) return AccessDecision.Allowed
        return AccessDecision.Denied(rule.denialMessageKey, rule.fallbackServers)
    }
}

public object FallbackSelector {
    public fun select(
        candidates: List<String>,
        currentServer: String?,
        available: Set<String>,
        authorized: (String) -> Boolean,
    ): String? = candidates.asSequence()
        .map(String::lowercase)
        .distinct()
        .filterNot { it.equals(currentServer, ignoreCase = true) }
        .firstOrNull { it in available && authorized(it) }
}

