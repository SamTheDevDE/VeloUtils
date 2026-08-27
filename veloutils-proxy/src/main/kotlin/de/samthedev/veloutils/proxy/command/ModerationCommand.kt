// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.command

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import de.samthedev.veloutils.api.CreatePunishment
import de.samthedev.veloutils.api.Punishment
import de.samthedev.veloutils.api.PunishmentId
import de.samthedev.veloutils.api.PunishmentScope
import de.samthedev.veloutils.api.PunishmentType
import de.samthedev.veloutils.common.DurationParser
import de.samthedev.veloutils.common.PageRequest
import de.samthedev.veloutils.common.PermissionDefinition
import de.samthedev.veloutils.common.Permissions
import de.samthedev.veloutils.proxy.integration.NetworkEventKind
import de.samthedev.veloutils.proxy.integration.NetworkEventSink
import de.samthedev.veloutils.proxy.messaging.ProxyProtocolGateway
import de.samthedev.veloutils.proxy.moderation.PersistentModerationService
import de.samthedev.veloutils.proxy.moderation.PunishmentAction
import de.samthedev.veloutils.proxy.moderation.PunishmentActionPolicy
import de.samthedev.veloutils.proxy.moderation.SelfPunishmentConfirmations
import de.samthedev.veloutils.proxy.permission.PermissionService
import de.samthedev.veloutils.proxy.player.PlayerIdentity
import de.samthedev.veloutils.proxy.player.PlayerIdentityService
import de.samthedev.veloutils.proxy.ui.ChatUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.time.Instant

public enum class ModerationCommandKind(
    public val type: PunishmentType?,
    public val permission: PermissionDefinition,
    public val temporary: Boolean = false,
) {
    BAN(PunishmentType.BAN, Permissions.MODERATION_BAN),
    TEMPBAN(PunishmentType.BAN, Permissions.MODERATION_TEMPBAN, true),
    IPBAN(PunishmentType.IP_BAN, Permissions.MODERATION_IPBAN),
    TEMPIPBAN(PunishmentType.IP_BAN, Permissions.MODERATION_TEMPIPBAN, true),
    KICK(PunishmentType.KICK, Permissions.MODERATION_KICK),
    MUTE(PunishmentType.MUTE, Permissions.MODERATION_MUTE),
    TEMPMUTE(PunishmentType.MUTE, Permissions.MODERATION_TEMPMUTE, true),
    WARN(PunishmentType.WARNING, Permissions.MODERATION_WARN),
    UNBAN(null, Permissions.MODERATION_UNBAN),
    UNMUTE(null, Permissions.MODERATION_UNMUTE),
    HISTORY(null, Permissions.MODERATION_HISTORY),
    CHECKBAN(null, Permissions.MODERATION_BAN_VIEW),
}

public class ModerationCommand(
    private val kind: ModerationCommandKind,
    private val proxy: ProxyServer,
    private val moderation: PersistentModerationService,
    private val identities: PlayerIdentityService,
    private val gateway: ProxyProtocolGateway,
    private val permissions: PermissionService,
    private val confirmations: SelfPunishmentConfirmations,
    private val pageSize: Int,
    private val scope: CoroutineScope,
    private val eventSink: NetworkEventSink,
) : SimpleCommand {
    private val commandName = kind.name.lowercase()

    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        if (!permissions.has(source, kind.permission)) {
            source.sendMessage(ChatUi.error("You do not have permission to use /$commandName."))
            return
        }
        val arguments = invocation.arguments()
        if (kind.type != null && arguments.firstOrNull()?.equals("confirm", true) == true) {
            confirm(source, arguments.getOrNull(1))
            return
        }
        if (kind.type != null && arguments.firstOrNull()?.equals("cancel", true) == true) {
            cancel(source, arguments.getOrNull(1))
            return
        }
        when (kind) {
            ModerationCommandKind.UNBAN -> revokeBySelector(source, arguments, setOf(PunishmentType.BAN, PunishmentType.IP_BAN))
            ModerationCommandKind.UNMUTE -> revokeBySelector(source, arguments, setOf(PunishmentType.MUTE))
            ModerationCommandKind.HISTORY -> history(source, arguments)
            ModerationCommandKind.CHECKBAN -> checkBan(source, arguments)
            else -> create(source, arguments)
        }
    }

    private fun create(source: CommandSource, arguments: Array<String>) {
        val targetInput = arguments.firstOrNull()
        if (targetInput == null) {
            ChatUi.usage(source, createSyntax(), "Punishes a known player; bans, mutes, and warnings support offline targets.")
                .forEach(source::sendMessage)
            return
        }
        val duration = if (kind.temporary) arguments.getOrNull(1)?.let { value ->
            runCatching { DurationParser.parse(value) }.getOrElse {
                source.sendMessage(ChatUi.error("Invalid duration '$value'. Use values such as 10m, 2h, 3d, or 1w."))
                return
            }
        } else null
        if (kind.temporary && duration == null) {
            source.sendMessage(ChatUi.error("A duration is required. Examples: 10m, 2h, 3d, 1w."))
            return
        }
        val reasonIndex = if (kind.temporary) 2 else 1
        val reason = arguments.drop(reasonIndex).joinToString(" ").ifBlank { "No reason provided" }
        scope.launch {
            val online = proxy.getPlayer(targetInput).orElse(null)
            val identity = online?.let {
                PlayerIdentity(it.uniqueId, it.username, Instant.now(), it.currentServer.map { connection -> connection.serverInfo.name }.orElse(null))
            } ?: identities.resolve(targetInput)
            if (identity == null) {
                source.sendMessage(ChatUi.error("Unknown player '$targetInput'. The player must have joined this network at least once."))
                return@launch
            }
            if (kind.type in setOf(PunishmentType.KICK, PunishmentType.IP_BAN) && online == null) {
                val action = if (kind.type == PunishmentType.KICK) "kicked" else "IP-banned"
                source.sendMessage(ChatUi.error("${identity.name} is offline and cannot be $action. Use a UUID ban for offline enforcement."))
                return@launch
            }
            val actor = source as? Player
            val request = CreatePunishment(
                requireNotNull(kind.type), identity.playerId, identity.name, actor?.uniqueId, actor?.username ?: "CONSOLE", reason,
                duration?.let(Instant.now()::plus), PunishmentScope.NETWORK, null,
                online?.remoteAddress?.address?.takeIf { kind.type == PunishmentType.IP_BAN },
            )
            if (actor?.uniqueId == identity.playerId && !permissions.has(source, Permissions.MODERATION_SELF_PUNISH)) {
                val token = confirmations.begin(actor.uniqueId, commandName, request)
                source.sendMessage(ChatUi.warning("You are about to $commandName yourself. This confirmation expires shortly."))
                source.sendMessage(ChatUi.join(
                    ChatUi.button(source, "Confirm", "/$commandName confirm $token", "Confirm this self-punishment", destructive = true),
                    ChatUi.button(source, "Cancel", "/$commandName cancel $token", "Cancel this action"),
                ))
                return@launch
            }
            applyPunishment(source, request, online)
        }
    }

    private fun confirm(source: CommandSource, token: String?) {
        val player = source as? Player
        if (player == null || token == null) {
            source.sendMessage(ChatUi.error("Only the player who started a self-punishment can confirm it."))
            return
        }
        val pending = confirmations.consume(player.uniqueId, commandName, token)
        if (pending == null) {
            source.sendMessage(ChatUi.error("That confirmation is invalid, expired, or has already been used."))
            return
        }
        scope.launch { applyPunishment(source, pending.request, proxy.getPlayer(pending.request.targetId).orElse(null)) }
    }

    private fun cancel(source: CommandSource, token: String?) {
        val player = source as? Player
        val cancelled = player != null && token != null && confirmations.cancel(player.uniqueId, token)
        source.sendMessage(if (cancelled) ChatUi.success("Self-punishment cancelled.") else ChatUi.error("That confirmation is invalid or expired."))
    }

    private suspend fun applyPunishment(source: CommandSource, request: CreatePunishment, online: Player?) {
        runCatching { moderation.punish(request) }.onSuccess { punishment ->
            source.sendMessage(ChatUi.success("Created ${displayType(punishment)} #${punishment.id.value} for ${punishment.targetName}."))
            source.sendMessage(ChatUi.button(source, "View details", "/punishment ${punishment.id.value}", "View punishment #${punishment.id.value}"))
            eventSink.emit(
                NetworkEventKind.PUNISHMENT,
                "${displayType(punishment)} #${punishment.id.value}",
                "Target: ${punishment.targetName}\nActor: ${punishment.actorName}\nReason: ${punishment.reason}",
            )
            if (punishment.type == PunishmentType.MUTE && online != null) gateway.sendMuteState(online, punishment)
            if (punishment.type in setOf(PunishmentType.BAN, PunishmentType.IP_BAN, PunishmentType.KICK)) {
                online?.disconnect(ChatUi.error("Removed from the network: ${punishment.reason}"))
            }
        }.onFailure { source.sendMessage(ChatUi.error("Punishment failed: ${it.message ?: "invalid request"}.")) }
    }

    private fun revokeBySelector(source: CommandSource, arguments: Array<String>, types: Set<PunishmentType>) {
        val selector = arguments.firstOrNull()
        if (selector == null) {
            ChatUi.usage(source, "/$commandName <player|#id> [reason]", "Revokes an active ${if (kind == ModerationCommandKind.UNBAN) "ban" else "mute"}.")
                .forEach(source::sendMessage)
            return
        }
        val reason = arguments.drop(1).joinToString(" ").ifBlank { "Revoked by staff" }
        val id = selector.removePrefix("#").toLongOrNull()?.takeIf { it > 0 }?.let(::PunishmentId)
        scope.launch {
            if (id != null) {
                val details = moderation.find(id)
                if (details == null || details.punishment.type !in types) {
                    source.sendMessage(ChatUi.error("No matching punishment '$selector' was found."))
                    return@launch
                }
                revoke(source, details.punishment, reason)
                return@launch
            }
            val identity = identities.resolve(selector)
            if (identity == null) {
                source.sendMessage(ChatUi.error("Unknown player '$selector'."))
                return@launch
            }
            val matches = moderation.activeForTypes(identity.playerId, types)
            when (matches.size) {
                0 -> source.sendMessage(ChatUi.info("${identity.name} has no matching active punishments."))
                1 -> revoke(source, matches.single(), reason)
                else -> {
                    source.sendMessage(ChatUi.header("Active punishments • ${identity.name}"))
                    matches.forEach { punishment ->
                        source.sendMessage(punishmentLine(punishment))
                        source.sendMessage(ChatUi.button(
                            source, "Revoke #${punishment.id.value}", "/$commandName #${punishment.id.value} $reason",
                            "Review and revoke punishment #${punishment.id.value}", destructive = true,
                        ))
                    }
                }
            }
        }
    }

    private suspend fun revoke(source: CommandSource, punishment: Punishment, reason: String) {
        if (!punishment.isEffective(Instant.now())) {
            source.sendMessage(ChatUi.info("Punishment #${punishment.id.value} is already inactive or expired."))
            return
        }
        runCatching { moderation.revoke(punishment.id, (source as? Player)?.uniqueId, reason) }.onSuccess { revoked ->
            if (revoked.type == PunishmentType.MUTE) {
                proxy.getPlayer(revoked.targetId).ifPresent { target ->
                    scope.launch {
                        val remaining = moderation.activeForTypes(target.uniqueId, setOf(PunishmentType.MUTE)).firstOrNull()
                        gateway.sendMuteState(target, remaining)
                    }
                }
            }
            source.sendMessage(ChatUi.success("Revoked ${displayType(revoked)} #${revoked.id.value} for ${revoked.targetName}."))
        }.onFailure { source.sendMessage(ChatUi.error("Revocation failed: ${it.message ?: "invalid state"}.")) }
    }

    private fun history(source: CommandSource, arguments: Array<String>) {
        val selector = arguments.firstOrNull()
        val pageInput = arguments.getOrNull(1)
        val pageNumber = pageInput?.toIntOrNull() ?: 1
        if (selector == null || pageInput != null && pageInput.toIntOrNull() == null || pageNumber < 1) {
            if (pageInput != null && (pageInput.toIntOrNull() == null || pageNumber < 1)) {
                source.sendMessage(ChatUi.error("Invalid page '$pageInput'. Pages are positive whole numbers."))
            }
            ChatUi.usage(source, "/history <player> [page]", "Shows paginated punishment history for a known player.")
                .forEach(source::sendMessage)
            return
        }
        scope.launch {
            val identity = identities.resolve(selector)
            if (identity == null) {
                source.sendMessage(ChatUi.error("Unknown player '$selector'."))
                return@launch
            }
            val page = moderation.historyPage(identity.playerId, PageRequest(pageNumber, pageSize))
            if (pageNumber > page.totalPages) {
                source.sendMessage(ChatUi.error("Page $pageNumber does not exist; the last page is ${page.totalPages}."))
                return@launch
            }
            source.sendMessage(ChatUi.header("Player history • ${identity.name}"))
            source.sendMessage(ChatUi.field("UUID", identity.playerId.toString()))
            source.sendMessage(ChatUi.field("Total records", page.totalItems.toString()))
            if (page.items.isEmpty()) source.sendMessage(ChatUi.info("No punishment records found."))
            page.items.forEach { punishment ->
                source.sendMessage(punishmentLine(punishment))
                source.sendMessage(ChatUi.button(source, "Details", "/punishment ${punishment.id.value}", "View punishment #${punishment.id.value}"))
            }
            if (page.totalPages > 1) source.sendMessage(ChatUi.pagination(source, page, "/history ${identity.name}"))
        }
    }

    private fun checkBan(source: CommandSource, arguments: Array<String>) {
        val selector = arguments.firstOrNull()
        if (selector == null) {
            ChatUi.usage(source, "/checkban <player>", "Shows all active UUID and IP bans for a known player.").forEach(source::sendMessage)
            return
        }
        scope.launch {
            val identity = identities.resolve(selector)
            if (identity == null) {
                source.sendMessage(ChatUi.error("Unknown player '$selector'."))
                return@launch
            }
            val visibleTypes = buildSet {
                add(PunishmentType.BAN)
                if (permissions.has(source, Permissions.MODERATION_IP_VIEW)) add(PunishmentType.IP_BAN)
            }
            val bans = moderation.activeForTypes(identity.playerId, visibleTypes)
            source.sendMessage(ChatUi.header("Active bans • ${identity.name}"))
            if (bans.isEmpty()) source.sendMessage(ChatUi.success("No active bans found."))
            bans.forEach { source.sendMessage(punishmentLine(it)) }
        }
    }

    private fun punishmentLine(punishment: Punishment): Component = Component.text()
        .append(Component.text("#${punishment.id.value} ", NamedTextColor.DARK_GRAY))
        .append(Component.text(displayType(punishment), NamedTextColor.GOLD))
        .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
        .append(ChatUi.status(if (punishment.isEffective(Instant.now())) "Active" else "Closed", punishment.isEffective(Instant.now())))
        .append(Component.newline())
        .append(Component.text("Reason: ${punishment.reason}", NamedTextColor.GRAY))
        .append(Component.newline())
        .append(Component.text("By ${punishment.actorName} • ${ChatUi.format(punishment.createdAt)} • ${ChatUi.remaining(punishment.expiresAt)}", NamedTextColor.DARK_GRAY))
        .build()

    private fun createSyntax(): String = if (kind.temporary) "/$commandName <player> <duration> [reason]" else "/$commandName <player> [reason]"

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        if (!permissions.has(invocation.source(), kind.permission)) return emptyList()
        val arguments = invocation.arguments()
        if (arguments.size > 1) return emptyList()
        val input = arguments.firstOrNull().orEmpty()
        return (proxy.allPlayers.map(Player::getUsername) + identities.suggestions(input)).distinct()
            .filter { it.startsWith(input, true) }.take(20)
    }

    private fun displayType(punishment: Punishment): String = punishment.type.name.lowercase().replace('_', ' ')
        .replaceFirstChar(Char::uppercase)
}

public class PunishmentCommand(
    private val moderation: PersistentModerationService,
    private val identities: PlayerIdentityService,
    private val permissions: PermissionService,
    private val scope: CoroutineScope,
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        if (!permissions.has(source, Permissions.MODERATION_DETAILS)) {
            source.sendMessage(ChatUi.error("You do not have permission to view punishment details."))
            return
        }
        val id = invocation.arguments().firstOrNull()?.removePrefix("#")?.toLongOrNull()?.takeIf { it > 0 }?.let(::PunishmentId)
        if (id == null) {
            ChatUi.usage(source, "/punishment <id>", "Shows a complete punishment record.").forEach(source::sendMessage)
            return
        }
        scope.launch {
            val details = moderation.find(id)
            if (details == null) {
                source.sendMessage(ChatUi.error("Punishment #${id.value} was not found."))
                return@launch
            }
            val punishment = details.punishment
            if (punishment.type == PunishmentType.IP_BAN && !permissions.has(source, Permissions.MODERATION_IP_VIEW)) {
                source.sendMessage(ChatUi.error("You do not have permission to view IP-ban details."))
                return@launch
            }
            val revokedBy = details.revokedBy?.let { identities.resolve(it.toString())?.name ?: it.toString() }
            source.sendMessage(ChatUi.header("Punishment #${id.value}"))
            listOf(
                ChatUi.field("Type", punishment.type.name.replace('_', ' ')), ChatUi.field("Target", punishment.targetName),
                ChatUi.field("UUID", punishment.targetId.toString()), ChatUi.field("Actor", punishment.actorName),
                ChatUi.field("Reason", punishment.reason), ChatUi.field("Created", ChatUi.format(punishment.createdAt)),
                ChatUi.field("Expires", punishment.expiresAt?.let(ChatUi::format) ?: "Never"),
                ChatUi.field("Remaining", ChatUi.remaining(punishment.expiresAt)), ChatUi.field("Scope", punishment.scope.name),
                ChatUi.field("Server", punishment.server ?: "Network-wide"),
                ChatUi.field("Status", if (punishment.isEffective(Instant.now())) "Active" else "Inactive"),
                ChatUi.field("Revoked by", revokedBy ?: "Not revoked"), ChatUi.field("Revoked at", ChatUi.format(details.revokedAt)),
                ChatUi.field("Revocation reason", details.revocationReason ?: "Not revoked"),
            ).forEach(source::sendMessage)
            val availableActions = PunishmentActionPolicy.available(
                punishment.type,
                punishment.isEffective(Instant.now()),
                permissions.has(source, Permissions.MODERATION_UNBAN),
                permissions.has(source, Permissions.MODERATION_UNMUTE),
                permissions.has(source, Permissions.MODERATION_HISTORY),
                permissions.has(source, Permissions.MODERATION_BAN_VIEW),
            )
            val actions = buildList {
                if (PunishmentAction.UNBAN in availableActions) {
                    add(ChatUi.button(source, "Unban", "/unban #${id.value}", "Review revoking punishment #${id.value}", destructive = true))
                }
                if (PunishmentAction.UNMUTE in availableActions) {
                    add(ChatUi.button(source, "Unmute", "/unmute #${id.value}", "Review revoking punishment #${id.value}", destructive = true))
                }
                if (PunishmentAction.HISTORY in availableActions) add(ChatUi.button(source, "History", "/history ${punishment.targetName}", "View player history"))
                if (PunishmentAction.CHECK_BAN in availableActions) add(ChatUi.button(source, "Check player", "/checkban ${punishment.targetName}", "View active bans"))
            }
            if (actions.isNotEmpty()) source.sendMessage(ChatUi.join(*actions.toTypedArray()))
        }
    }
}
