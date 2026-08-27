// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.ProxyServer
import de.samthedev.veloutils.proxy.messaging.ProxyProtocolGateway
import de.samthedev.veloutils.proxy.util.ConfiguredMessages
import net.kyori.adventure.text.Component

public class ServerExecuteCommand(
    private val proxy: ProxyServer,
    private val gateway: ProxyProtocolGateway,
    private val messages: ConfiguredMessages,
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        if (!source.hasPermission(PERMISSION)) {
            source.sendMessage(messages.render("no-permission"))
            return
        }
        val arguments = invocation.arguments()
        if (arguments.size < 2) {
            source.sendMessage(Component.text("/serverexecute <server> <allowlisted command>"))
            return
        }
        val server = proxy.getServer(arguments[0]).orElse(null)
        if (server == null) {
            source.sendMessage(messages.render("server-unavailable"))
            return
        }
        runCatching { gateway.execute(server, arguments.drop(1).joinToString(" ")) }
            .onFailure { source.sendMessage(Component.text("Command rejected by VeloUtils policy.")) }
            .onSuccess { future ->
                future.whenComplete { response, failure ->
                    when {
                        failure != null -> source.sendMessage(Component.text("Backend command failed or timed out."))
                        response.accepted -> source.sendMessage(Component.text("Backend accepted the command."))
                        else -> source.sendMessage(Component.text("Backend rejected the command."))
                    }
                }
            }
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        if (!invocation.source().hasPermission(PERMISSION) || invocation.arguments().size > 1) return emptyList()
        val input = invocation.arguments().firstOrNull()?.lowercase().orEmpty()
        return proxy.allServers.map { it.serverInfo.name }.filter { it.lowercase().startsWith(input) }
    }

    private companion object { private const val PERMISSION: String = "veloutils.command.serverexecute" }
}
