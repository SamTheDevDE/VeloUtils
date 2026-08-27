// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.ProxyServer
import de.samthedev.veloutils.api.MaintenanceUpdate
import de.samthedev.veloutils.common.InputPolicies
import de.samthedev.veloutils.proxy.maintenance.PersistentMaintenanceService
import de.samthedev.veloutils.proxy.util.ConfiguredMessages
import de.samthedev.veloutils.proxy.integration.NetworkEventKind
import de.samthedev.veloutils.proxy.integration.NetworkEventSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component

public class MaintenanceCommand(
    private val proxy: ProxyServer,
    private val maintenance: PersistentMaintenanceService,
    private val messages: ConfiguredMessages,
    private val scope: CoroutineScope,
    private val eventSink: NetworkEventSink,
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        if (!source.hasPermission("veloutils.maintenance.command")) {
            source.sendMessage(messages.render("no-permission"))
            return
        }
        val arguments = invocation.arguments()
        when (arguments.firstOrNull()?.lowercase()) {
            "status" -> {
                val snapshot = maintenance.snapshot()
                source.sendMessage(Component.text("Global maintenance: ${snapshot.global?.reason ?: "disabled"}"))
                snapshot.servers.forEach { (server, window) -> source.sendMessage(Component.text("$server: ${window.reason}")) }
                source.sendMessage(Component.text("Allowlisted UUIDs: ${snapshot.allowedPlayers.size}"))
            }
            "enable" -> {
                val target = arguments.getOrNull(1)?.takeUnless { it.equals("global", true) }
                val reason = arguments.drop(2).joinToString(" ").ifBlank { "Scheduled maintenance" }
                update(source, MaintenanceUpdate.Enable(target, InputPolicies.PUNISHMENT_REASON.validate(reason)), true)
            }
            "disable" -> update(source, MaintenanceUpdate.Disable(arguments.getOrNull(1)?.takeUnless { it.equals("global", true) }), false)
            "allow", "disallow" -> {
                val player = arguments.getOrNull(1)?.let { proxy.getPlayer(it).orElse(null) }
                if (player == null) source.sendMessage(messages.render("player-offline", mapOf("player" to Component.text(arguments.getOrNull(1).orEmpty()))))
                else update(
                    source,
                    if (arguments[0].equals("allow", true)) MaintenanceUpdate.Allow(player.uniqueId) else MaintenanceUpdate.Disallow(player.uniqueId),
                    null,
                )
            }
            else -> source.sendMessage(Component.text("/maintenance <status|enable [global|server] [reason]|disable [global|server]|allow <player>|disallow <player>>"))
        }
    }

    private fun update(source: com.velocitypowered.api.command.CommandSource, request: MaintenanceUpdate, enabled: Boolean?) {
        scope.launch {
            runCatching { maintenance.update(request) }
                .onSuccess {
                    val key = if (enabled == false) "maintenance.disabled" else "maintenance.enabled"
                    source.sendMessage(messages.render(key, mapOf("reason" to Component.text((request as? MaintenanceUpdate.Enable)?.reason.orEmpty()))))
                    when (request) {
                        is MaintenanceUpdate.Enable -> eventSink.emit(
                            NetworkEventKind.MAINTENANCE,
                            "Maintenance enabled",
                            "Scope: ${request.server ?: "network"}\nReason: ${request.reason}",
                        )
                        is MaintenanceUpdate.Disable -> eventSink.emit(
                            NetworkEventKind.MAINTENANCE,
                            "Maintenance disabled",
                            "Scope: ${request.server ?: "network"}",
                        )
                        else -> Unit
                    }
                }
                .onFailure { source.sendMessage(Component.text("Maintenance update failed; persistent state was not changed.")) }
        }
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        if (!invocation.source().hasPermission("veloutils.maintenance.command")) return emptyList()
        val args = invocation.arguments()
        return when (args.size) {
            0, 1 -> listOf("status", "enable", "disable", "allow", "disallow").filter { it.startsWith(args.firstOrNull().orEmpty(), true) }
            2 -> when (args[0].lowercase()) {
                "enable", "disable" -> (listOf("global") + proxy.allServers.map { it.serverInfo.name }).filter { it.startsWith(args[1], true) }
                "allow", "disallow" -> proxy.allPlayers.map { it.username }.filter { it.startsWith(args[1], true) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
