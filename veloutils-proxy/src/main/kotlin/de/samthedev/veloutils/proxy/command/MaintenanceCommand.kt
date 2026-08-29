// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.ProxyServer
import de.samthedev.veloutils.api.MaintenanceUpdate
import de.samthedev.veloutils.common.InputPolicies
import de.samthedev.veloutils.common.DurationParser
import de.samthedev.veloutils.common.Permissions
import de.samthedev.veloutils.proxy.maintenance.PersistentMaintenanceService
import de.samthedev.veloutils.proxy.maintenance.MaintenanceScheduler
import de.samthedev.veloutils.proxy.maintenance.ScheduledMaintenance
import de.samthedev.veloutils.proxy.util.ConfiguredMessages
import de.samthedev.veloutils.proxy.integration.NetworkEventKind
import de.samthedev.veloutils.proxy.integration.NetworkEventSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import de.samthedev.veloutils.proxy.permission.PermissionService
import de.samthedev.veloutils.proxy.player.PlayerIdentityService
import de.samthedev.veloutils.proxy.ui.ChatUi
import java.time.Instant

public class MaintenanceCommand(
    private val proxy: ProxyServer,
    private val maintenance: PersistentMaintenanceService,
    private val scheduler: MaintenanceScheduler,
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
                scheduler.snapshot().forEach { scheduled ->
                    source.sendMessage(
                        ChatUi.field(
                            "Scheduled ${scheduled.server ?: "global"}",
                            "${scheduled.startsAt} — ${scheduled.reason}",
                        ),
                    )
                }
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
            "schedule" -> schedule(source, arguments)
            "cancel" -> {
                val target = arguments.getOrNull(1)?.takeUnless { it.equals("global", true) }
                scope.launch {
                    if (scheduler.cancel(target)) source.sendMessage(ChatUi.info("Scheduled maintenance cancelled."))
                    else source.sendMessage(ChatUi.error("No matching scheduled maintenance exists."))
                }
            }
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
                "/maintenance <status|enable|disable|schedule|cancel|allow|disallow> ...",
                "Controls global/server maintenance and its persistent player allowlist.",
            ).forEach(source::sendMessage)
        }
    }

    private fun schedule(source: com.velocitypowered.api.command.CommandSource, arguments: Array<String>) {
        if (arguments.size < 5) {
            ChatUi.usage(
                source,
                "/maintenance schedule <global|server> <delay> <duration|permanent> <reason>",
                "Persists a scheduled maintenance window with countdown notifications.",
            ).forEach(source::sendMessage)
            return
        }
        val server = arguments[1].takeUnless { it.equals("global", true) }
        val delay = runCatching { DurationParser.parse(arguments[2]) }.getOrElse {
            source.sendMessage(ChatUi.error("Invalid delay: ${it.message}."))
            return
        }
        val duration = arguments[3].takeUnless { it.equals("permanent", true) || it == "-" }?.let { value ->
            runCatching { DurationParser.parse(value) }.getOrElse {
                source.sendMessage(ChatUi.error("Invalid duration: ${it.message}."))
                return
            }
        }
        if (delay.isZero || delay.isNegative || duration?.let { it.isZero || it.isNegative } == true) {
            source.sendMessage(ChatUi.error("Delay and duration must be positive."))
            return
        }
        val reason = runCatching { InputPolicies.PUNISHMENT_REASON.validate(arguments.drop(4).joinToString(" ")) }.getOrElse {
            source.sendMessage(ChatUi.error("Invalid maintenance reason: ${it.message}."))
            return
        }
        val startsAt = Instant.now().plus(delay)
        scope.launch {
            runCatching { scheduler.schedule(ScheduledMaintenance(server, reason, startsAt, duration?.let(startsAt::plus))) }
                .onSuccess { source.sendMessage(ChatUi.info("Maintenance scheduled for $startsAt.")) }
                .onFailure { source.sendMessage(ChatUi.error("Could not schedule maintenance: ${it.message}.")) }
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
            0, 1 -> listOf("status", "enable", "disable", "schedule", "cancel", "allow", "disallow")
                .filter { it.startsWith(args.firstOrNull().orEmpty(), true) }
            2 -> when (args[0].lowercase()) {
                "enable", "disable", "schedule", "cancel" -> (listOf("global") + proxy.allServers.map { it.serverInfo.name })
                    .filter { it.startsWith(args[1], true) }
                "allow", "disallow" -> identities.suggestions(args[1])
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
