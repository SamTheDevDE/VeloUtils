// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.ProxyServer
import de.samthedev.veloutils.proxy.messaging.ProxyProtocolGateway
import de.samthedev.veloutils.common.Permissions
import de.samthedev.veloutils.proxy.permission.PermissionService
import de.samthedev.veloutils.proxy.ui.ChatUi

public class ServerExecuteCommand(
    private val proxy: ProxyServer,
    private val gateway: ProxyProtocolGateway,
    private val permissions: PermissionService,
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        if (!permissions.has(source, Permissions.NETWORK_EXECUTE)) {
            source.sendMessage(ChatUi.error("You do not have permission to execute backend commands."))
            return
        }
        val arguments = invocation.arguments()
        if (arguments.size < 2) {
            ChatUi.usage(source, "/serverexecute <server> <allowlisted-command>", "Executes a command allowed by both proxy and bridge policy.").forEach(source::sendMessage)
            return
        }
        val server = proxy.getServer(arguments[0]).orElse(null)
        if (server == null) {
            source.sendMessage(ChatUi.error("Server '${arguments[0]}' is not registered on this proxy."))
            return
        }
        runCatching { gateway.execute(server, arguments.drop(1).joinToString(" ")) }
            .onFailure { source.sendMessage(ChatUi.error("Command rejected by VeloUtils policy: ${it.message}.")) }
            .onSuccess { future ->
                future.whenComplete { response, failure ->
                    when {
                        failure != null -> source.sendMessage(ChatUi.error("Backend command failed or timed out."))
                        response.accepted -> source.sendMessage(ChatUi.success("Backend accepted the command: ${response.detail}."))
                        else -> source.sendMessage(ChatUi.error("Backend rejected the command: ${response.detail}."))
                    }
                }
            }
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        if (!permissions.has(invocation.source(), Permissions.NETWORK_EXECUTE) || invocation.arguments().size > 1) return emptyList()
        val input = invocation.arguments().firstOrNull()?.lowercase().orEmpty()
        return proxy.allServers.map { it.serverInfo.name }.filter { it.lowercase().startsWith(input) }
    }
}
