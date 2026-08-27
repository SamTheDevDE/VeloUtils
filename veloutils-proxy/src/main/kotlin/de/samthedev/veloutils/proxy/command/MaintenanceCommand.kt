// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.ProxyServer
import de.samthedev.veloutils.api.MaintenanceUpdate
import de.samthedev.veloutils.common.InputPolicies
import de.samthedev.veloutils.common.Permissions
import de.samthedev.veloutils.proxy.maintenance.PersistentMaintenanceService
import de.samthedev.veloutils.proxy.util.ConfiguredMessages
import de.samthedev.veloutils.proxy.integration.NetworkEventKind
import de.samthedev.veloutils.proxy.integration.NetworkEventSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import de.samthedev.veloutils.proxy.permission.PermissionService
import de.samthedev.veloutils.proxy.player.PlayerIdentityService
import de.samthedev.veloutils.proxy.ui.ChatUi

public class MaintenanceCommand(
    private val proxy: ProxyServer,
    private val maintenance: PersistentMaintenanceService,
    private val messages: ConfiguredMessages,
    private val identities: PlayerIdentityService,
    private val permissions: PermissionService,
    private val scope: CoroutineScope,
    private val eventSink: NetworkEventSink,
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        if (!permissions.has(source, Permissions.MAINTENANCE_MANAGE)) {
            source.sendMessage(ChatUi.error("You do not have permission to manage maintenance."))
            return
        }
        val arguments = invocation.arguments()
        when (arguments.firstOrNull()?.lowercase()) {
            "status" -> {
                val snapshot = maintenance.snapshot()
                source.sendMessage(ChatUi.header("Maintenance status"))
                source.sendMessage(ChatUi.field("Global", snapshot.global?.reason ?: "Disabled"))
                snapshot.servers.forEach { (server, window) -> source.sendMessage(ChatUi.field(server, window.reason)) }
                source.sendMessage(ChatUi.field("Allowlisted players", snapshot.allowedPlayers.size.toString()))
            }
            "enable" -> {
                val target = arguments.getOrNull(1)?.takeUnless { it.equals("global", true) }
                val rawReason = arguments.drop(2).joinToString(" ").ifBlank { "Scheduled maintenance" }
                val reason = runCatching { InputPolicies.PUNISHMENT_REASON.validate(rawReason) }.getOrElse {
                    source.sendMessage(ChatUi.error("Invalid maintenance reason: ${it.message}."))
                    return
                }
                update(source, MaintenanceUpdate.Enable(target, reason), true)
            }
            "disable" -> update(source, MaintenanceUpdate.Disable(arguments.getOrNull(1)?.takeUnless { it.equals("global", true) }), false)
            "allow", "disallow" -> {
                val input = arguments.getOrNull(1)
                if (input == null) {
                    ChatUi.usage(source, "/maintenance ${arguments[0]} <known-player>", "Updates the persistent maintenance allowlist.").forEach(source::sendMessage)
                    return
                }
                scope.launch {
                    val identity = identities.resolve(input)
                    if (identity == null) source.sendMessage(ChatUi.error("Unknown player '$input'."))
                    else update(
                        source,
                        if (arguments[0].equals("allow", true)) MaintenanceUpdate.Allow(identity.playerId) else MaintenanceUpdate.Disallow(identity.playerId),
                        null,
                    )
                }
            }
            else -> ChatUi.usage(
                source,
                "/maintenance <status|enable|disable|allow|disallow> ...",
                "Controls global/server maintenance and its persistent player allowlist.",
            ).forEach(source::sendMessage)
        }
    }

    private fun update(source: com.velocitypowered.api.command.CommandSource, request: MaintenanceUpdate, enabled: Boolean?) {
        scope.launch {
            runCatching { maintenance.update(request) }
                .onSuccess {
                    val key = if (enabled == false) "maintenance.disabled" else "maintenance.enabled"
                    source.sendMessage(messages.render(key, mapOf("reason" to Component.text((request as? MaintenanceUpdate.Enable)?.reason.orEmpty()))))
                    val activity = when (request) {
                        is MaintenanceUpdate.Enable -> "Maintenance enabled for ${request.server ?: "the network"}: ${request.reason}"
                        is MaintenanceUpdate.Disable -> "Maintenance disabled for ${request.server ?: "the network"}."
                        is MaintenanceUpdate.Allow -> "A player was added to the maintenance allowlist."
                        is MaintenanceUpdate.Disallow -> "A player was removed from the maintenance allowlist."
                    }
                    proxy.allPlayers.asSequence()
                        .filter { it != source && permissions.has(it, Permissions.MAINTENANCE_NOTIFY) }
                        .forEach { it.sendMessage(ChatUi.info(activity)) }
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
                .onFailure { source.sendMessage(ChatUi.error("Maintenance update failed: ${it.message ?: "persistent state was not changed"}.")) }
        }
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        if (!permissions.has(invocation.source(), Permissions.MAINTENANCE_MANAGE)) return emptyList()
        val args = invocation.arguments()
        return when (args.size) {
            0, 1 -> listOf("status", "enable", "disable", "allow", "disallow").filter { it.startsWith(args.firstOrNull().orEmpty(), true) }
            2 -> when (args[0].lowercase()) {
                "enable", "disable" -> (listOf("global") + proxy.allServers.map { it.serverInfo.name }).filter { it.startsWith(args[1], true) }
                "allow", "disallow" -> identities.suggestions(args[1])
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
