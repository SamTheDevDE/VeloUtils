// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.integration

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.KickedFromServerEvent
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component

public class LimboFallbackAdapter(
    private val proxy: ProxyServer,
    private val serverName: String,
) {
    @Subscribe
    public fun onBackendKick(event: KickedFromServerEvent) {
        if (event.server.serverInfo.name.equals(serverName, ignoreCase = true)) return
        val fallback = proxy.getServer(serverName).orElse(null) ?: return
        if (event.player.currentServer.map { it.serverInfo.name.equals(serverName, true) }.orElse(false)) return
        event.result = KickedFromServerEvent.RedirectPlayer.create(
            fallback,
            Component.text("The previous server became unavailable; you were moved to $serverName."),
        )
    }
}
