// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import de.samthedev.veloutils.api.CreatePunishment
import de.samthedev.veloutils.api.PunishmentId
import de.samthedev.veloutils.api.PunishmentScope
import de.samthedev.veloutils.api.PunishmentType
import de.samthedev.veloutils.common.DurationParser
import de.samthedev.veloutils.proxy.moderation.PersistentModerationService
import de.samthedev.veloutils.proxy.util.ConfiguredMessages
import de.samthedev.veloutils.proxy.messaging.ProxyProtocolGateway
import de.samthedev.veloutils.proxy.integration.NetworkEventKind
import de.samthedev.veloutils.proxy.integration.NetworkEventSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import java.time.Instant

public enum class ModerationCommandKind(public val type: PunishmentType?, public val temporary: Boolean = false) {
    BAN(PunishmentType.BAN), TEMPBAN(PunishmentType.BAN, true), IPBAN(PunishmentType.IP_BAN), TEMPIPBAN(PunishmentType.IP_BAN, true),
    KICK(PunishmentType.KICK), MUTE(PunishmentType.MUTE), TEMPMUTE(PunishmentType.MUTE, true), WARN(PunishmentType.WARNING),
    UNBAN(null), UNMUTE(null), HISTORY(null), CHECKBAN(null),
}

public class ModerationCommand(
    private val kind: ModerationCommandKind,
    private val proxy: ProxyServer,
    private val moderation: PersistentModerationService,
    private val gateway: ProxyProtocolGateway,
    private val messages: ConfiguredMessages,
    private val scope: CoroutineScope,
    private val eventSink: NetworkEventSink,
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        if (!source.hasPermission("veloutils.moderation.${kind.name.lowercase()}")) {
            source.sendMessage(messages.render("no-permission"))
            return
        }
        val arguments = invocation.arguments()
        when (kind) {
            ModerationCommandKind.UNBAN, ModerationCommandKind.UNMUTE -> revoke(invocation, arguments)
            ModerationCommandKind.HISTORY, ModerationCommandKind.CHECKBAN -> inspect(invocation, arguments)
            else -> create(invocation, arguments)
        }
    }

    private fun create(invocation: SimpleCommand.Invocation, arguments: Array<String>) {
        val source = invocation.source()
        val target = arguments.firstOrNull()?.let { proxy.getPlayer(it).orElse(null) }
        if (target == null) {
            source.sendMessage(messages.render("player-offline", mapOf("player" to Component.text(arguments.firstOrNull().orEmpty()))))
            return
        }
        val duration = if (kind.temporary) arguments.getOrNull(1)?.let { runCatching { DurationParser.parse(it) }.getOrNull() } else null
        if (kind.temporary && duration == null) {
            source.sendMessage(Component.text("A valid duration is required."))
            return
        }
        val reasonIndex = if (kind.temporary) 2 else 1
        val reason = arguments.drop(reasonIndex).joinToString(" ").ifBlank { "No reason provided" }
        val actor = source as? Player
        val request = CreatePunishment(
            requireNotNull(kind.type), target.uniqueId, target.username, actor?.uniqueId, actor?.username ?: "CONSOLE", reason,
            duration?.let { Instant.now().plus(it) }, PunishmentScope.NETWORK, null,
            target.remoteAddress.address.takeIf { kind.type == PunishmentType.IP_BAN },
        )
        scope.launch {
            runCatching { moderation.punish(request) }.onSuccess { punishment ->
                source.sendMessage(Component.text("Created ${punishment.type.name.lowercase()} #${punishment.id.value}."))
                eventSink.emit(
                    NetworkEventKind.PUNISHMENT,
                    "${punishment.type.name.lowercase()} #${punishment.id.value}",
                    "Target: ${punishment.targetName}\nActor: ${punishment.actorName}\nReason: ${punishment.reason}",
                )
                if (punishment.type == PunishmentType.MUTE) gateway.sendMuteState(target, punishment)
                if (punishment.type in setOf(PunishmentType.BAN, PunishmentType.IP_BAN, PunishmentType.KICK)) {
                    target.disconnect(Component.text("Removed from the network: ${punishment.reason}"))
                }
            }.onFailure { source.sendMessage(Component.text("Punishment failed: ${it.message ?: "invalid request"}")) }
        }
    }

    private fun revoke(invocation: SimpleCommand.Invocation, arguments: Array<String>) {
        val source = invocation.source()
        val id = arguments.firstOrNull()?.toLongOrNull()?.takeIf { it > 0 }?.let(::PunishmentId)
        if (id == null) {
            source.sendMessage(Component.text("/${kind.name.lowercase()} <punishment-id> [reason]"))
            return
        }
        scope.launch {
            runCatching { moderation.revoke(id, (source as? Player)?.uniqueId, arguments.drop(1).joinToString(" ").ifBlank { "Revoked" }) }
                .onSuccess { punishment ->
                    if (punishment.type == PunishmentType.MUTE) {
                        proxy.getPlayer(punishment.targetId).ifPresent { target ->
                            scope.launch {
                                val remaining = moderation.activeFor(target.uniqueId, target.remoteAddress.address)
                                    .firstOrNull { it.type == PunishmentType.MUTE }
                                gateway.sendMuteState(target, remaining)
                            }
                        }
                    }
                    source.sendMessage(Component.text("Revoked punishment #${punishment.id.value}."))
                    eventSink.emit(
                        NetworkEventKind.PUNISHMENT,
                        "Punishment #${punishment.id.value} revoked",
                        "Target: ${punishment.targetName}\nType: ${punishment.type.name.lowercase()}",
                    )
                }
                .onFailure { source.sendMessage(Component.text("Revocation failed: ${it.message ?: "invalid state"}")) }
        }
    }

    private fun inspect(invocation: SimpleCommand.Invocation, arguments: Array<String>) {
        val source = invocation.source()
        val target = arguments.firstOrNull()?.let { proxy.getPlayer(it).orElse(null) }
        if (target == null) {
            source.sendMessage(messages.render("player-offline", mapOf("player" to Component.text(arguments.firstOrNull().orEmpty()))))
            return
        }
        scope.launch {
            val records = if (kind == ModerationCommandKind.HISTORY) moderation.history(target.uniqueId)
            else moderation.activeFor(target.uniqueId, target.remoteAddress.address)
            if (records.isEmpty()) source.sendMessage(Component.text("No punishment records found."))
            records.forEach { source.sendMessage(Component.text("#${it.id.value} ${it.type} active=${it.active}: ${it.reason}")) }
        }
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        if (!invocation.source().hasPermission("veloutils.moderation.${kind.name.lowercase()}")) return emptyList()
        if (invocation.arguments().size > 1 || kind in setOf(ModerationCommandKind.UNBAN, ModerationCommandKind.UNMUTE)) return emptyList()
        val input = invocation.arguments().firstOrNull().orEmpty()
        return proxy.allPlayers.map { it.username }.filter { it.startsWith(input, true) }
    }
}
