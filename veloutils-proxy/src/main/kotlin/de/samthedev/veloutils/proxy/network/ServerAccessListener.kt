// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.network

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.ServerPreConnectEvent
import com.velocitypowered.api.proxy.ProxyServer
import de.samthedev.veloutils.api.AccessDecision
import de.samthedev.veloutils.common.FallbackSelector
import de.samthedev.veloutils.common.ServerAccessPolicy
import de.samthedev.veloutils.common.ServerAccessRule
import de.samthedev.veloutils.proxy.util.ConfiguredMessages
import de.samthedev.veloutils.common.Permissions
import de.samthedev.veloutils.proxy.permission.PermissionService

public class ServerAccessListener(
    private val proxy: ProxyServer,
    rules: Map<String, ServerAccessRule>,
    private val messages: ConfiguredMessages,
    private val permissionService: PermissionService,
    private val policy: ServerAccessPolicy = ServerAccessPolicy(),
) {
    private val rules = rules.mapKeys { it.key.lowercase() }

    @Subscribe
    public fun onServerConnect(event: ServerPreConnectEvent) {
        val player = event.player
        val destination = event.result.server.orElse(event.originalServer)
        val destinationName = destination.serverInfo.name.lowercase()
        val bypass = permissionService.has(player, Permissions.SERVER_ACCESS_BYPASS)
        val decision = decide(player.uniqueId, player::hasPermission, destinationName, bypass)
        if (decision !is AccessDecision.Denied) return

        val available = proxy.allServers.mapTo(mutableSetOf()) { it.serverInfo.name.lowercase() }
        val fallback = FallbackSelector.select(
            decision.fallbackServers,
            player.currentServer.map { it.serverInfo.name }.orElse(null),
            available,
        ) { candidate -> decide(player.uniqueId, player::hasPermission, candidate, bypass) is AccessDecision.Allowed }
        event.result = fallback?.let { proxy.getServer(it).map(ServerPreConnectEvent.ServerResult::allowed).orElse(null) }
            ?: ServerPreConnectEvent.ServerResult.denied()
        player.sendMessage(messages.render("server-access.denied"))
    }

    private fun decide(
        playerId: java.util.UUID,
        permission: (String) -> Boolean,
        server: String,
        bypass: Boolean,
    ): AccessDecision {
        val rule = rules[server]
        val permissions = buildSet {
            if (bypass) add(Permissions.SERVER_ACCESS_BYPASS.node)
            rule?.permission?.takeIf(permission)?.let(::add)
        }
        return policy.decide(playerId, permissions, rule)
    }
}
