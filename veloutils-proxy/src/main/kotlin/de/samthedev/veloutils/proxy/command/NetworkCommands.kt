// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.command

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import de.samthedev.veloutils.api.ConnectionOutcome
import de.samthedev.veloutils.api.ServerSnapshot
import de.samthedev.veloutils.common.Page
import de.samthedev.veloutils.common.PermissionDefinition
import de.samthedev.veloutils.common.Permissions
import de.samthedev.veloutils.proxy.network.VelocityNetworkService
import de.samthedev.veloutils.proxy.permission.PermissionService
import de.samthedev.veloutils.proxy.ui.ChatUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

public enum class NetworkCommandKind(public val permission: PermissionDefinition) {
    FIND(Permissions.NETWORK_FIND), GOTO(Permissions.NETWORK_GOTO), LIST(Permissions.NETWORK_LIST),
    NETWORK(Permissions.NETWORK_STATUS), SERVER_INFO(Permissions.NETWORK_SERVER_INFO),
    SEND(Permissions.NETWORK_SEND), SEND_ALL(Permissions.NETWORK_SEND_ALL),
}

public class NetworkCommand(
    private val kind: NetworkCommandKind,
    private val proxy: ProxyServer,
    private val network: VelocityNetworkService,
    private val permissions: PermissionService,
    private val pageSize: Int,
    private val scope: CoroutineScope,
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        if (!permissions.has(source, kind.permission)) {
            source.sendMessage(ChatUi.error("You do not have permission to use this network command."))
            return
        }
        val arguments = invocation.arguments()
        when (kind) {
            NetworkCommandKind.FIND -> find(source, arguments)
            NetworkCommandKind.GOTO -> goto(source, arguments)
            NetworkCommandKind.LIST -> serverList(source, arguments, includePlayers = true)
            NetworkCommandKind.NETWORK -> serverList(source, arguments, includePlayers = false)
            NetworkCommandKind.SERVER_INFO -> serverInfo(source, arguments)
            NetworkCommandKind.SEND -> send(source, arguments)
            NetworkCommandKind.SEND_ALL -> sendAll(source, arguments)
        }
    }

    private fun find(source: CommandSource, arguments: Array<String>) {
        val input = arguments.firstOrNull()
        val target = input?.let { proxy.getPlayer(it).orElse(null) }
        if (target == null) {
            if (input == null) ChatUi.usage(source, "/find <online-player>", "Shows a player's current Velocity server and ping.").forEach(source::sendMessage)
            else source.sendMessage(ChatUi.error("Player '$input' is not online."))
            return
        }
        val server = target.currentServer.map { it.serverInfo.name }.orElse("Connecting")
        source.sendMessage(ChatUi.header("Player location"))
        source.sendMessage(ChatUi.field("Player", target.username, NamedTextColor.AQUA))
        source.sendMessage(ChatUi.field("Server", server, NamedTextColor.GOLD))
        source.sendMessage(ChatUi.field("Ping", "${target.ping} ms"))
        if (source is Player && source.uniqueId != target.uniqueId && permissions.has(source, Permissions.NETWORK_GOTO)) {
            source.sendMessage(ChatUi.button(source, "Go to player", "/goto ${target.username}", "Join $server"))
        }
    }

    private fun goto(source: CommandSource, arguments: Array<String>) {
        val player = source as? Player
        if (player == null) {
            source.sendMessage(ChatUi.error("Console cannot change servers. Use /send <player> <server> instead."))
            return
        }
        val input = arguments.firstOrNull()
        val target = input?.let { proxy.getPlayer(it).orElse(null) }
        val server = target?.currentServer?.map { it.serverInfo.name }?.orElse(null)
        if (target == null || server == null) {
            if (input == null) ChatUi.usage(source, "/goto <online-player>", "Joins the player's current server.").forEach(source::sendMessage)
            else source.sendMessage(ChatUi.error("Player '$input' is offline or not connected to a server."))
            return
        }
        connect(source, player, server)
    }

    private fun serverList(source: CommandSource, arguments: Array<String>, includePlayers: Boolean) {
        val pageInput = arguments.firstOrNull()
        val pageNumber = pageInput?.toIntOrNull() ?: 1
        if (pageInput != null && pageInput.toIntOrNull() == null || pageNumber < 1) {
            if (pageInput != null) source.sendMessage(ChatUi.error("Invalid page '$pageInput'. Pages are positive whole numbers."))
            ChatUi.usage(source, if (includePlayers) "/vlist [page]" else "/network [page]", "Shows a paginated server overview.")
                .forEach(source::sendMessage)
            return
        }
        val snapshot = network.snapshot()
        val total = snapshot.servers.size
        val totalPages = maxOf(1, (total + pageSize - 1) / pageSize)
        if (pageNumber > totalPages) {
            source.sendMessage(ChatUi.error("Page $pageNumber does not exist; the last page is $totalPages."))
            return
        }
        val items = snapshot.servers.drop((pageNumber - 1) * pageSize).take(pageSize)
        val page = Page(items, pageNumber, pageSize, total.toLong())
        source.sendMessage(ChatUi.header(if (includePlayers) "Players by server" else "Network overview"))
        source.sendMessage(ChatUi.field("Online players", snapshot.playerCount.toString()))
        items.forEach { server ->
            source.sendMessage(serverLine(server))
            if (includePlayers) {
                val names = proxy.getServer(server.name).map { registered -> registered.playersConnected.joinToString { it.username } }.orElse("")
                source.sendMessage(Component.text(if (names.isBlank()) "  No players online" else "  $names", NamedTextColor.GRAY))
            }
        }
        if (page.totalPages > 1) source.sendMessage(ChatUi.pagination(source, page, if (includePlayers) "/vlist" else "/network"))
    }

    private fun serverInfo(source: CommandSource, arguments: Array<String>) {
        val input = arguments.firstOrNull()
        val server = input?.let(network::server)
        if (server == null) {
            if (input == null) ChatUi.usage(source, "/serverinfo <server>", "Shows Velocity and bridge status without guessing backend health.").forEach(source::sendMessage)
            else source.sendMessage(ChatUi.error("Server '$input' is not registered with Velocity."))
            return
        }
        source.sendMessage(ChatUi.header("Server • ${server.name}"))
        source.sendMessage(ChatUi.field("Players", server.playerCount.toString()))
        source.sendMessage(ChatUi.field("Velocity registration", if (server.online) "Available" else "Unavailable"))
        val bridge = server.bridge
        if (bridge == null) {
            source.sendMessage(ChatUi.field("Bridge", "Unavailable — backend health is unknown", NamedTextColor.YELLOW))
        } else {
            source.sendMessage(ChatUi.field("Bridge", "Online", NamedTextColor.GREEN))
            source.sendMessage(ChatUi.field("Bridge version", bridge.pluginVersion))
            source.sendMessage(ChatUi.field("Protocol", bridge.protocolVersion.toString()))
            source.sendMessage(ChatUi.field("Platform", if (bridge.folia) "Folia" else bridge.implementation))
            source.sendMessage(ChatUi.field("Minecraft", bridge.minecraftVersion))
            source.sendMessage(ChatUi.field("Last heartbeat", ChatUi.format(bridge.lastHeartbeat)))
        }
        if (permissions.has(source, Permissions.NETWORK_LIST)) source.sendMessage(ChatUi.button(source, "View players", "/vlist", "View players by server"))
    }

    private fun send(source: CommandSource, arguments: Array<String>) {
        val playerName = arguments.getOrNull(0)
        val server = arguments.getOrNull(1)
        val target = playerName?.let { proxy.getPlayer(it).orElse(null) }
        if (target == null || server == null) {
            if (playerName != null && target == null) source.sendMessage(ChatUi.error("Player '$playerName' is not online."))
            else ChatUi.usage(source, "/send <online-player> <server>", "Moves one player to a registered server.").forEach(source::sendMessage)
            return
        }
        connect(source, target, server)
    }

    private fun sendAll(source: CommandSource, arguments: Array<String>) {
        val server = arguments.firstOrNull()
        if (server == null || proxy.getServer(server).isEmpty) {
            if (server == null) ChatUi.usage(source, "/sendall <server>", "Moves every online player to a registered server.").forEach(source::sendMessage)
            else source.sendMessage(ChatUi.error("Server '$server' is not registered with Velocity."))
            return
        }
        scope.launch {
            var moved = 0
            var failed = 0
            proxy.allPlayers.forEach { player ->
                when (network.connect(player.uniqueId, listOf(server))) {
                    is ConnectionOutcome.Connected -> moved++
                    else -> failed++
                }
            }
            source.sendMessage(ChatUi.success("Moved $moved players to $server${if (failed > 0) "; $failed could not be moved" else ""}."))
        }
    }

    private fun connect(source: CommandSource, target: Player, server: String) {
        if (proxy.getServer(server).isEmpty) {
            source.sendMessage(ChatUi.error("Server '$server' is not registered with Velocity."))
            return
        }
        scope.launch {
            when (val result = network.connect(target.uniqueId, listOf(server))) {
                is ConnectionOutcome.Connected -> {
                    source.sendMessage(ChatUi.success("Connecting ${target.username} to ${result.server}."))
                    if (source != target) target.sendMessage(ChatUi.info("A staff member is connecting you to ${result.server}."))
                }
                is ConnectionOutcome.Denied -> source.sendMessage(ChatUi.error("${target.username} could not connect: ${result.reason}."))
                ConnectionOutcome.NoDestinationAvailable -> source.sendMessage(ChatUi.error("No requested destination is available."))
                ConnectionOutcome.PlayerOffline -> source.sendMessage(ChatUi.error("${target.username} disconnected before the move completed."))
            }
        }
    }

    private fun serverLine(server: ServerSnapshot): Component {
        val bridge = if (server.bridge == null) "Bridge unavailable" else "Bridge online"
        return Component.text().append(Component.text(server.name, NamedTextColor.GOLD))
            .append(Component.text(" • ${server.playerCount} players • $bridge", NamedTextColor.GRAY)).build()
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        if (!permissions.has(invocation.source(), kind.permission)) return emptyList()
        val arguments = invocation.arguments()
        val input = arguments.lastOrNull().orEmpty()
        return when (kind) {
            NetworkCommandKind.FIND, NetworkCommandKind.GOTO -> proxy.allPlayers.map(Player::getUsername).filter { it.startsWith(input, true) }
            NetworkCommandKind.SERVER_INFO, NetworkCommandKind.SEND_ALL -> proxy.allServers.map { it.serverInfo.name }.filter { it.startsWith(input, true) }
            NetworkCommandKind.SEND -> if (arguments.size <= 1) proxy.allPlayers.map(Player::getUsername).filter { it.startsWith(input, true) }
                else proxy.allServers.map { it.serverInfo.name }.filter { it.startsWith(input, true) }
            else -> emptyList()
        }.take(20)
    }
}
