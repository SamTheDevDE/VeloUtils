// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.network

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.ServerPing
import com.velocitypowered.api.util.Favicon
import de.samthedev.veloutils.api.MaintenanceSnapshot
import de.samthedev.veloutils.proxy.config.MotdConfig
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

public class MotdListener(
    private val proxy: ProxyServer,
    config: MotdConfig,
    dataDirectory: Path,
    private val maintenance: () -> MaintenanceSnapshot?,
) {
    private val miniMessage = MiniMessage.miniMessage()
    private val entries = config.entries.map(miniMessage::deserialize)
    private val maintenanceEntries = config.maintenanceEntries.map(miniMessage::deserialize)
    private val virtualHosts = config.virtualHosts.mapValues { (_, values) -> values.map(miniMessage::deserialize) }
    private val maximumPlayers = config.maximumPlayers
    private val samples = config.samplePlayers.take(12)
    private val favicon = config.favicon?.let(dataDirectory::resolve)?.takeIf(Files::isRegularFile)?.let(Favicon::create)

    @Subscribe
    public fun onPing(event: ProxyPingEvent) {
        val maintenanceReason = maintenance()?.global?.reason
        val host = event.connection.rawVirtualHost.orElse("").substringBefore(':').lowercase().removeSuffix(".")
        val candidates = if (maintenanceReason != null) maintenanceEntries else virtualHosts[host].orEmpty().ifEmpty { entries }
        val selected = candidates[ThreadLocalRandom.current().nextInt(candidates.size)]
            .replaceLiteral("{players}", proxy.playerCount.toString())
            .replaceLiteral("{reason}", maintenanceReason.orEmpty())
        val builder = event.ping.asBuilder()
            .description(selected)
            .onlinePlayers(proxy.playerCount)
            .maximumPlayers(maximumPlayers)
            .samplePlayers(samples.map { ServerPing.SamplePlayer(it, UUID.nameUUIDFromBytes(it.toByteArray())) })
        favicon?.let(builder::favicon)
        event.ping = builder.build()
    }

    private fun Component.replaceLiteral(literal: String, replacement: String): Component = replaceText { builder ->
        builder.matchLiteral(literal).replacement(Component.text(replacement))
    }
}
