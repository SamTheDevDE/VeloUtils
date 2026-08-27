// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.messaging

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import de.samthedev.veloutils.api.BridgeSnapshot
import de.samthedev.veloutils.common.RemoteCommandPolicy
import de.samthedev.veloutils.common.InputPolicies
import de.samthedev.veloutils.protocol.DecodeResult
import de.samthedev.veloutils.protocol.HelloAckPayload
import de.samthedev.veloutils.protocol.HelloPayload
import de.samthedev.veloutils.protocol.PacketType
import de.samthedev.veloutils.protocol.ProtocolCodec
import de.samthedev.veloutils.protocol.ProtocolVersion
import de.samthedev.veloutils.protocol.CommandRequestPayload
import de.samthedev.veloutils.protocol.CommandResponsePayload
import de.samthedev.veloutils.protocol.ChatPayload
import de.samthedev.veloutils.protocol.AlertPayload
import de.samthedev.veloutils.protocol.DeliveryResponsePayload
import de.samthedev.veloutils.protocol.DeliveryStatus
import de.samthedev.veloutils.protocol.PlaceholderPayload
import de.samthedev.veloutils.protocol.MuteStatePayload
import de.samthedev.veloutils.protocol.RequestTracker
import de.samthedev.veloutils.protocol.newRequestId
import de.samthedev.veloutils.protocol.StatusPayload
import de.samthedev.veloutils.protocol.negotiateProtocol
import de.samthedev.veloutils.proxy.network.BridgeStatusRegistry
import de.samthedev.veloutils.proxy.util.ConfiguredMessages
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.Logger
import java.time.Instant
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import net.kyori.adventure.text.Component
import de.samthedev.veloutils.api.Punishment
import de.samthedev.veloutils.proxy.integration.NetworkEventKind
import de.samthedev.veloutils.proxy.integration.NetworkEventSink
import de.samthedev.veloutils.common.Permissions
import de.samthedev.veloutils.proxy.permission.PermissionService
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor

public class ProxyProtocolGateway(
    private val codec: ProtocolCodec,
    private val statuses: BridgeStatusRegistry,
    private val logger: Logger,
    private val remoteCommands: RemoteCommandPolicy,
    private val requestTimeout: Duration,
    private val proxy: ProxyServer,
    private val messages: ConfiguredMessages,
    private val staffChatEnabled: Boolean,
    private val eventSink: NetworkEventSink,
    private val permissions: PermissionService,
    private val authenticatedMode: Boolean,
) : AutoCloseable {
    public companion object {
        public val CHANNEL: MinecraftChannelIdentifier = MinecraftChannelIdentifier.create("veloutils", "main")
    }

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val requestExecutor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "veloutils-protocol-timeouts").apply { isDaemon = true }
    }
    private val requests = RequestTracker(requestExecutor, maximumPending = 1_024)
    private val expectedResponders = ConcurrentHashMap<String, String>()

    public fun execute(server: RegisteredServer, command: String): CompletableFuture<CommandResponsePayload> {
        val validated = remoteCommands.validate(command)
        val requestId = newRequestId()
        val expectedServer = server.serverInfo.name.lowercase()
        val response = requests.register(requestId, requestTimeout)
        expectedResponders[requestId] = expectedServer
        response.whenComplete { _, _ -> expectedResponders.remove(requestId) }
        val payload = CommandRequestPayload(validated, "backend-console")
        val sent = server.sendPluginMessage(
            CHANNEL,
            codec.encode(codec.envelope(PacketType.COMMAND_REQUEST, requestId, json.encodeToJsonElement(payload).jsonObject)),
        )
        if (!sent) response.completeExceptionally(IllegalStateException("No player connection is available to carry the request"))
        return response.thenApply { envelope -> json.decodeFromJsonElement<CommandResponsePayload>(envelope.payload) }
    }

    public fun sendMuteState(player: Player, punishment: Punishment?): Boolean {
        val server = player.currentServer.map { it.server }.orElse(null) ?: return false
        val payload = MuteStatePayload(
            playerId = player.uniqueId.toString(),
            muted = punishment != null,
            expiresAtEpochMillis = punishment?.expiresAt?.toEpochMilli(),
            reason = punishment?.reason,
        )
        return runCatching {
            server.sendPluginMessage(
                CHANNEL,
                codec.encode(codec.envelope(PacketType.MUTE_STATE, newRequestId(), json.encodeToJsonElement(payload).jsonObject)),
            )
        }.getOrElse {
            logger.warn("[VeloUtils] Could not send mute state for {} to {}", player.username, server.serverInfo.name)
            false
        }
    }

    @Subscribe
    public fun onPluginMessage(event: PluginMessageEvent) {
        if (event.identifier != CHANNEL) return
        event.result = PluginMessageEvent.ForwardResult.handled()
        val source = event.source as? ServerConnection ?: return
        when (val result = codec.decode(event.data)) {
            is DecodeResult.Rejected -> logger.warn(
                "[VeloUtils] Rejected bridge packet from {}: {}",
                source.serverInfo.name,
                result.error.code,
            )
            is DecodeResult.UnknownPacket -> logger.debug("[VeloUtils] Ignored unknown packet type from {}", source.serverInfo.name)
            is DecodeResult.Accepted -> handle(source, result)
        }
    }

    private fun handle(source: ServerConnection, result: DecodeResult.Accepted) {
        when (result.type) {
            PacketType.HELLO -> {
                val hello = runCatching { json.decodeFromJsonElement<HelloPayload>(result.envelope.payload) }.getOrElse {
                    logger.warn("[VeloUtils] Invalid HELLO payload from {}", source.serverInfo.name)
                    return
                }
                val selected = negotiateProtocol(
                    ProtocolVersion.MINIMUM_SUPPORTED,
                    ProtocolVersion.CURRENT,
                    hello.minimumProtocol,
                    hello.maximumProtocol,
                )
                val response = HelloAckPayload(selected != null, selected, selected?.let { null } ?: "No compatible protocol version")
                val envelope = codec.envelope(PacketType.HELLO_ACK, result.envelope.requestId, json.encodeToJsonElement(response).jsonObject)
                source.sendPluginMessage(CHANNEL, codec.encode(envelope))
                logger.info("[VeloUtils] Bridge handshake {} with {} (protocol {})", if (selected == null) "rejected" else "completed", source.serverInfo.name, selected ?: "none")
            }
            PacketType.SERVER_STATUS, PacketType.HEARTBEAT -> {
                val payload = runCatching { json.decodeFromJsonElement<StatusPayload>(result.envelope.payload) }.getOrElse {
                    logger.warn("[VeloUtils] Invalid status payload from {}", source.serverInfo.name)
                    return
                }
                statuses.update(source.serverInfo.name, BridgeSnapshot(
                    payload.pluginVersion, payload.protocolVersion, payload.implementation,
                    payload.minecraftVersion, payload.folia, Instant.now(),
                ))
                pushPlaceholderSnapshot(source)
            }
            PacketType.STAFF_CHAT_MESSAGE -> handleStaffChat(source, result)
            PacketType.NETWORK_ALERT -> handleNetworkAlert(source, result)
            PacketType.COMMAND_RESPONSE -> {
                val expected = expectedResponders[result.envelope.requestId]
                if (expected == null || expected != source.serverInfo.name.lowercase()) {
                    logger.warn("[VeloUtils] Rejected unmatched command response from {}", source.serverInfo.name)
                    return
                }
                requests.complete(result.envelope)
            }
            else -> logger.debug("[VeloUtils] Packet {} is not handled by the proxy gateway", result.type)
        }
    }

    private fun handleStaffChat(source: ServerConnection, result: DecodeResult.Accepted) {
        if (!staffChatEnabled) {
            respond(source, PacketType.CHAT_RESPONSE, result.envelope.requestId, DeliveryStatus.MODULE_DISABLED, "Staff chat is disabled on the proxy.")
            return
        }
        val payload = runCatching { json.decodeFromJsonElement<ChatPayload>(result.envelope.payload) }.getOrNull()
        if (payload == null) {
            respond(source, PacketType.CHAT_RESPONSE, result.envelope.requestId, DeliveryStatus.INVALID_MESSAGE, "The chat request was malformed.")
            return
        }
        val player = source.player
        val channel = payload.channel.lowercase()
        if (payload.playerId != player.uniqueId.toString() || payload.playerName != player.username) {
            respond(source, PacketType.CHAT_RESPONSE, result.envelope.requestId, DeliveryStatus.AUTHENTICATION_FAILED, "The sender identity did not match the connection.")
            return
        }
        val usePermission = when (channel) {
            "staff" -> Permissions.CHAT_STAFF_USE
            "admin" -> Permissions.CHAT_ADMIN_USE
            else -> null
        }
        val receivePermission = when (channel) {
            "staff" -> Permissions.CHAT_STAFF_RECEIVE
            "admin" -> Permissions.CHAT_ADMIN_RECEIVE
            else -> null
        }
        if (usePermission == null || receivePermission == null) {
            respond(source, PacketType.CHAT_RESPONSE, result.envelope.requestId, DeliveryStatus.INVALID_CHANNEL, "That chat channel does not exist.")
            return
        }
        if (!permissions.has(player, usePermission)) {
            respond(source, PacketType.CHAT_RESPONSE, result.envelope.requestId, DeliveryStatus.NO_PERMISSION, "You do not have permission to use ${channel.replaceFirstChar(Char::uppercase)} Chat.")
            return
        }
        val message = runCatching { InputPolicies.CHAT.validate(payload.message) }.getOrNull()
        if (message == null) {
            respond(source, PacketType.CHAT_RESPONSE, result.envelope.requestId, DeliveryStatus.INVALID_MESSAGE, "The message was blank or exceeded the allowed length.")
            return
        }
        val serverName = source.serverInfo.name
        val playerComponent = Component.text(player.username, NamedTextColor.AQUA)
            .hoverEvent(HoverEvent.showText(Component.text("${player.username}\nServer: $serverName", NamedTextColor.GRAY)))
            .clickEvent(ClickEvent.suggestCommand("/find ${player.username}"))
        val rendered = messages.render(
            "staff-chat.${channel}-format",
            mapOf("channel" to Component.text(channel), "player" to playerComponent, "server" to Component.text(serverName), "message" to Component.text(message)),
        )
        val recipients = proxy.allPlayers.filter { permissions.has(it, receivePermission) }
        recipients.forEach { it.sendMessage(rendered) }
        proxy.consoleCommandSource.sendMessage(rendered)
        val status = if (recipients.isEmpty()) DeliveryStatus.NO_RECIPIENTS else DeliveryStatus.SENT
        val detail = if (recipients.isEmpty()) "No online players can receive ${channel.replaceFirstChar(Char::uppercase)} Chat." else
            "${channel.replaceFirstChar(Char::uppercase)} Chat message sent to ${recipients.size} recipient${if (recipients.size == 1) "" else "s"}."
        respond(source, PacketType.CHAT_RESPONSE, result.envelope.requestId, status, detail, recipients.size)
    }

    private fun handleNetworkAlert(source: ServerConnection, result: DecodeResult.Accepted) {
        val player = source.player
        val payload = runCatching { json.decodeFromJsonElement<AlertPayload>(result.envelope.payload) }.getOrNull()
        if (payload == null) {
            respond(source, PacketType.ALERT_RESPONSE, result.envelope.requestId, DeliveryStatus.INVALID_MESSAGE, "The alert request was malformed.")
            return
        }
        if (payload.console) {
            if (!authenticatedMode) {
                respond(source, PacketType.ALERT_RESPONSE, result.envelope.requestId, DeliveryStatus.AUTHENTICATION_FAILED, "Console alerts require authenticated bridge messaging.")
                return
            }
        } else {
            if (payload.actorId != player.uniqueId.toString() || payload.actorName != player.username) {
                respond(source, PacketType.ALERT_RESPONSE, result.envelope.requestId, DeliveryStatus.AUTHENTICATION_FAILED, "The sender identity did not match the connection.")
                return
            }
            if (!permissions.has(player, Permissions.ALERT_BROADCAST)) {
                respond(source, PacketType.ALERT_RESPONSE, result.envelope.requestId, DeliveryStatus.NO_PERMISSION, "You do not have permission to broadcast network alerts.")
                return
            }
        }
        val safeMessage = runCatching { InputPolicies.ALERT.validate(payload.message) }.getOrNull()
        if (safeMessage == null) {
            respond(source, PacketType.ALERT_RESPONSE, result.envelope.requestId, DeliveryStatus.INVALID_MESSAGE, "The alert was blank or exceeded the allowed length.")
            return
        }
        val actorName = if (payload.console) "Console (${source.serverInfo.name})" else player.username
        val rendered = Component.text("[Network] $actorName: $safeMessage", NamedTextColor.YELLOW)
        proxy.allPlayers.forEach { it.sendMessage(rendered) }
        proxy.consoleCommandSource.sendMessage(rendered)
        eventSink.emit(NetworkEventKind.ALERT, "Network alert", "$actorName: $safeMessage")
        val status = if (proxy.playerCount == 0) DeliveryStatus.NO_RECIPIENTS else DeliveryStatus.SENT
        val detail = if (proxy.playerCount == 0) "The alert was accepted, but no players are online." else
            "Network alert sent to ${proxy.playerCount} player${if (proxy.playerCount == 1) "" else "s"}."
        respond(source, PacketType.ALERT_RESPONSE, result.envelope.requestId, status, detail, proxy.playerCount)
    }

    private fun respond(
        source: ServerConnection,
        type: PacketType,
        requestId: String,
        status: DeliveryStatus,
        detail: String,
        recipients: Int = 0,
    ) {
        val payload = DeliveryResponsePayload(status == DeliveryStatus.SENT, status, recipients, detail)
        runCatching {
            source.sendPluginMessage(CHANNEL, codec.encode(codec.envelope(type, requestId, json.encodeToJsonElement(payload).jsonObject)))
        }.onFailure { logger.warn("[VeloUtils] Could not return {} acknowledgement to {}", type, source.serverInfo.name) }
    }

    private fun pushPlaceholderSnapshot(source: ServerConnection) {
        val values = buildMap {
            put("network_players", proxy.playerCount.toString())
            put("staff_online", proxy.allPlayers.count { permissions.has(it, Permissions.STAFF_MEMBER) }.toString())
            put("maintenance", "false")
            proxy.allServers.forEach { server ->
                val key = server.serverInfo.name.lowercase()
                put("server_${key}_players", server.playersConnected.size.toString())
                put("server_${key}_online", "true")
                put("maintenance_$key", "false")
            }
        }
        val envelope = codec.envelope(
            PacketType.PLACEHOLDER_RESPONSE,
            newRequestId(),
            json.encodeToJsonElement(PlaceholderPayload(values)).jsonObject,
        )
        runCatching { source.sendPluginMessage(CHANNEL, codec.encode(envelope)) }
            .onFailure { logger.warn("[VeloUtils] Placeholder snapshot for {} exceeded protocol limits", source.serverInfo.name) }
    }

    override fun close() {
        requests.close()
        expectedResponders.clear()
        requestExecutor.shutdownNow()
    }
}
