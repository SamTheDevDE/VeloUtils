// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import de.samthedev.veloutils.api.ConnectionOutcome
import de.samthedev.veloutils.proxy.network.VelocityNetworkService
import de.samthedev.veloutils.proxy.util.ConfiguredMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component

public enum class NetworkCommandKind(public val permission: String) {
    FIND("veloutils.command.find"),
    GOTO("veloutils.command.goto"),
    LIST("veloutils.command.list"),
    NETWORK("veloutils.command.network"),
    SERVER_INFO("veloutils.command.serverinfo"),
    SEND("veloutils.command.send"),
    SEND_ALL("veloutils.command.sendall"),
}

public class NetworkCommand(
    private val kind: NetworkCommandKind,
    private val proxy: ProxyServer,
    private val network: VelocityNetworkService,
    private val messages: ConfiguredMessages,
    private val scope: CoroutineScope,
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        if (!source.hasPermission(kind.permission)) {
            source.sendMessage(messages.render("no-permission"))
            return
        }
        val args = invocation.arguments()
        when (kind) {
            NetworkCommandKind.FIND -> {
                val target = args.firstOrNull()?.let { proxy.getPlayer(it).orElse(null) }
                if (target == null) source.sendMessage(messages.render("player-offline", mapOf("player" to Component.text(args.firstOrNull().orEmpty()))))
                else source.sendMessage(Component.text("${target.username} is on ${target.currentServer.map { it.serverInfo.name }.orElse("unknown")}"))
            }
            NetworkCommandKind.GOTO -> connectPlayer(source as? Player, args.firstOrNull()?.let { proxy.getPlayer(it).orElse(null) }?.currentServer?.map { it.serverInfo.name }?.orElse(null))
            NetworkCommandKind.LIST -> network.snapshot().servers.filter { it.playerCount > 0 }.forEach {
                source.sendMessage(Component.text("${it.name} (${it.playerCount}): ${proxy.getServer(it.name).map { server -> server.playersConnected.joinToString { player -> player.username } }.orElse("")}"))
            }
            NetworkCommandKind.NETWORK -> source.sendMessage(Component.text("Network: ${network.snapshot().playerCount} players across ${network.snapshot().servers.size} servers"))
            NetworkCommandKind.SERVER_INFO -> {
                val server = args.firstOrNull()?.let(network::server)
                if (server == null) source.sendMessage(messages.render("server-unavailable"))
                else source.sendMessage(Component.text("${server.name}: ${server.playerCount} players; bridge=${server.bridge ?: "unavailable"}"))
            }
            NetworkCommandKind.SEND -> {
                val target = args.getOrNull(0)?.let { proxy.getPlayer(it).orElse(null) }
                if (target == null) source.sendMessage(messages.render("player-offline", mapOf("player" to Component.text(args.getOrNull(0).orEmpty()))))
                else connectPlayer(target, args.getOrNull(1))
            }
            NetworkCommandKind.SEND_ALL -> {
                val destination = args.firstOrNull()
                if (destination == null) source.sendMessage(Component.text("/sendall <server>"))
                else proxy.allPlayers.forEach { connectPlayer(it, destination) }
            }
        }
    }

    private fun connectPlayer(player: Player?, server: String?) {
        if (player == null) return
        if (server == null) {
            player.sendMessage(messages.render("server-unavailable"))
            return
        }
        scope.launch {
            when (val result = network.connect(player.uniqueId, listOf(server))) {
                is ConnectionOutcome.Connected -> player.sendMessage(messages.render("connecting", mapOf("server" to Component.text(result.server))))
                else -> player.sendMessage(messages.render("server-unavailable"))
            }
        }
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        if (!invocation.source().hasPermission(kind.permission)) return emptyList()
        val args = invocation.arguments()
        val input = args.lastOrNull()?.lowercase().orEmpty()
        return when (kind) {
            NetworkCommandKind.FIND, NetworkCommandKind.GOTO -> proxy.allPlayers.map(Player::getUsername).filter { it.lowercase().startsWith(input) }
            NetworkCommandKind.SERVER_INFO, NetworkCommandKind.SEND_ALL -> proxy.allServers.map { it.serverInfo.name }.filter { it.lowercase().startsWith(input) }
            NetworkCommandKind.SEND -> if (args.size <= 1) proxy.allPlayers.map(Player::getUsername).filter { it.lowercase().startsWith(input) }
                else proxy.allServers.map { it.serverInfo.name }.filter { it.lowercase().startsWith(input) }
            else -> emptyList()
        }
    }
}
