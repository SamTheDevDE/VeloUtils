// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.maintenance

import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.player.ServerPreConnectEvent
import de.samthedev.veloutils.api.AccessDecision
import de.samthedev.veloutils.common.Permissions
import de.samthedev.veloutils.proxy.util.ConfiguredMessages
import net.kyori.adventure.text.Component

public class MaintenanceListener(
    private val maintenance: PersistentMaintenanceService,
    private val messages: ConfiguredMessages,
) {
    @Subscribe
    public fun onLogin(event: LoginEvent) {
        if (decision(event.player.uniqueId, event.player::hasPermission, null) is AccessDecision.Denied) {
            event.result = ResultedEvent.ComponentResult.denied(denialMessage())
        }
    }

    @Subscribe
    public fun onServerConnect(event: ServerPreConnectEvent) {
        val destination = event.result.server.orElse(event.originalServer).serverInfo.name
        if (decision(event.player.uniqueId, event.player::hasPermission, destination) is AccessDecision.Denied) {
            event.result = ServerPreConnectEvent.ServerResult.denied()
            event.player.sendMessage(denialMessage())
        }
    }

    private fun decision(
        playerId: java.util.UUID,
        permission: (String) -> Boolean,
        server: String?,
    ): AccessDecision = maintenance.access(
        playerId,
        buildSet { if (permission(Permissions.MAINTENANCE_BYPASS.node)) add(Permissions.MAINTENANCE_BYPASS.node) },
        server,
    )

    private fun denialMessage(): Component {
        val snapshot = maintenance.snapshot()
        val reason = snapshot.global?.reason ?: "This server is under maintenance"
        return messages.render("maintenance.denied", mapOf("reason" to Component.text(reason)))
    }
}
