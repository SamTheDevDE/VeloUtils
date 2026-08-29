// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.integration.tab

import com.velocitypowered.api.proxy.ProxyServer
import de.samthedev.veloutils.api.MaintenanceSnapshot
import de.samthedev.veloutils.proxy.network.BridgeStatusRegistry
import java.util.UUID

internal class VelocityTabDataSource(
    private val proxy: ProxyServer,
    private val bridgeStatuses: BridgeStatusRegistry,
    private val maintenance: () -> MaintenanceSnapshot?,
) : TabDataSource {
    override fun player(playerId: UUID): TabPlayerState? = proxy.getPlayer(playerId).orElse(null)?.let { player ->
        TabPlayerState(
            server = player.currentServer.map { it.serverInfo.name }.orElse(null),
            ping = player.ping,
        )
    }

    override fun backendNames(): Set<String> = proxy.allServers.mapTo(linkedSetOf()) { it.serverInfo.name }

    override fun backend(name: String): TabBackendState? = proxy.getServer(name).orElse(null)?.let { server ->
        val players = server.playersConnected.size
        TabBackendState(players, players > 0 || bridgeStatuses.get(server.serverInfo.name) != null)
    }

    override fun networkPlayerCount(): Int = proxy.playerCount

    override fun maintenance(server: String?): TabMaintenanceState {
        val snapshot = maintenance() ?: return TabMaintenanceState(false, "")
        val window = snapshot.global ?: server?.lowercase()?.let(snapshot.servers::get)
        return TabMaintenanceState(window != null, window?.reason.orEmpty())
    }
}
