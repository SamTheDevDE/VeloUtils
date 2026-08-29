// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.network

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.ServerPing
import com.velocitypowered.api.util.Favicon
import de.samthedev.veloutils.api.MaintenanceSnapshot
import de.samthedev.veloutils.proxy.config.MotdConfig
import de.samthedev.veloutils.proxy.util.DynamicMiniMessageTemplate
import net.kyori.adventure.text.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

public class MotdListener(
    private val proxy: ProxyServer,
    config: MotdConfig,
    dataDirectory: Path,
    private val maintenance: () -> MaintenanceSnapshot?,
    private val placeholderValues: () -> Map<String, String> = { emptyMap() },
) {
    private val entries = config.entries.mapIndexed { index, source ->
        DynamicMiniMessageTemplate(source, "config.yml: motd.entries[$index]")
    }
    private val maintenanceEntries = config.maintenanceEntries.mapIndexed { index, source ->
        DynamicMiniMessageTemplate(source, "config.yml: motd.maintenance-entries[$index]")
    }
    private val virtualHosts = config.virtualHosts.mapValues { (host, values) ->
        values.mapIndexed { index, source ->
            DynamicMiniMessageTemplate(source, "config.yml: motd.virtual-hosts.$host.entries[$index]")
        }
    }
    private val maximumPlayers = config.maximumPlayers
    private val samples = SamplePlayerRenderer(config.samplePlayers)
    private val favicon = config.favicon?.let(dataDirectory::resolve)?.takeIf(Files::isRegularFile)?.let(Favicon::create)

    @Subscribe
    public fun onPing(event: ProxyPingEvent) {
        val maintenanceReason = maintenance()?.global?.reason
        val host = event.connection.rawVirtualHost.orElse("").substringBefore(':').lowercase().removeSuffix(".")
        val candidates = if (maintenanceReason != null) maintenanceEntries else virtualHosts[host].orEmpty().ifEmpty { entries }
        val values = placeholderValues() + mapOf(
            "players" to proxy.playerCount.toString(),
            "max_players" to maximumPlayers.toString(),
            "reason" to maintenanceReason.orEmpty(),
        )
        val selected = candidates[ThreadLocalRandom.current().nextInt(candidates.size)].render(values)
        val renderedSamples = samples.render(values)
        val builder = event.ping.asBuilder()
            .description(selected)
            .onlinePlayers(proxy.playerCount)
            .maximumPlayers(maximumPlayers)
            .samplePlayers(renderedSamples.mapIndexed { index, value ->
                ServerPing.SamplePlayer(value, UUID.nameUUIDFromBytes("veloutils-sample-$index".toByteArray()))
            })
        favicon?.let(builder::favicon)
        event.ping = builder.build()
    }
}
