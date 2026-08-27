// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.command

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import de.samthedev.veloutils.proxy.config.ConfigRepository
import de.samthedev.veloutils.proxy.network.VelocityNetworkService
import de.samthedev.veloutils.proxy.util.ConfiguredMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component

public class RootCommand(
    private val version: String,
    private val config: ConfigRepository,
    private val messages: ConfiguredMessages,
    private val network: VelocityNetworkService,
    private val scope: CoroutineScope,
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        if (!source.hasPermission("veloutils.command.admin")) {
            source.sendMessage(messages.render("no-permission"))
            return
        }
        when (invocation.arguments().firstOrNull()?.lowercase()) {
            "version" -> source.sendMessage(Component.text("VeloUtils $version by SamTheDevDE"))
            "status" -> showStatus(source)
            "debug" -> showDebug(source)
            "reload" -> scope.launch(Dispatchers.IO) {
                runCatching { config.load(); messages.reload() }
                    .onSuccess { source.sendMessage(messages.render("reload-success")) }
                    .onFailure { source.sendMessage(messages.render("reload-failed")) }
            }
            else -> source.sendMessage(Component.text("/veloutils <status|reload|version|debug>"))
        }
    }

    private fun showStatus(source: CommandSource) {
        val snapshot = network.snapshot()
        source.sendMessage(Component.text("VeloUtils $version — ${snapshot.playerCount} players"))
        snapshot.servers.forEach { server ->
            val bridge = server.bridge
            val bridgeText = if (bridge == null) "bridge unavailable" else
                "bridge ${bridge.pluginVersion}, protocol ${bridge.protocolVersion}, ${if (bridge.folia) "Folia" else bridge.implementation}"
            source.sendMessage(Component.text("${server.name}: ${server.playerCount} players, $bridgeText"))
        }
    }

    private fun showDebug(source: CommandSource) {
        val snapshot = config.snapshot()
        source.sendMessage(Component.text("VeloUtils diagnostics (secrets redacted)"))
        source.sendMessage(Component.text("Storage: ${snapshot.storage.type}; modules: ${snapshot.modules}"))
        source.sendMessage(Component.text("Protocol authentication: ${snapshot.protocol.requireAuthentication}; secret: <redacted>"))
        source.sendMessage(Component.text("Network: ${network.snapshot().servers.size} configured servers"))
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        if (!invocation.source().hasPermission("veloutils.command.admin")) return emptyList()
        val input = invocation.arguments().firstOrNull()?.lowercase().orEmpty()
        return listOf("status", "reload", "version", "debug").filter { it.startsWith(input) }
    }
}

