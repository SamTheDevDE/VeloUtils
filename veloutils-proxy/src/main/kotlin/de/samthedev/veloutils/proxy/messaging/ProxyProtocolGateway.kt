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
        if (!staffChatEnabled) return
        val payload = runCatching { json.decodeFromJsonElement<ChatPayload>(result.envelope.payload) }.getOrNull() ?: return
        val player = source.player
        val channel = payload.channel.lowercase()
        if (payload.playerId != player.uniqueId.toString() || payload.playerName != player.username) return
        if (!Regex("[a-z0-9_-]{1,32}").matches(channel) || !player.hasPermission("veloutils.chat.$channel")) return
        val message = runCatching { InputPolicies.CHAT.validate(payload.message) }.getOrNull() ?: return
        val rendered = messages.render(
            "staff-chat.sent",
            mapOf("channel" to Component.text(channel), "player" to Component.text(player.username), "message" to Component.text(message)),
        )
        proxy.allPlayers.filter { it.hasPermission("veloutils.chat.$channel") }.forEach { it.sendMessage(rendered) }
        proxy.consoleCommandSource.sendMessage(rendered)
    }

    private fun handleNetworkAlert(source: ServerConnection, result: DecodeResult.Accepted) {
        val player = source.player
        if (!player.hasPermission("veloutils.bridge.alert")) return
        val fields = runCatching {
            Triple(
                result.envelope.payload["actor_id"]?.jsonPrimitive?.content,
                result.envelope.payload["actor_name"]?.jsonPrimitive?.content,
                result.envelope.payload["message"]?.jsonPrimitive?.content,
            )
        }.getOrNull() ?: return
        val (actorId, actorName, rawMessage) = fields
        if (rawMessage == null) return
        if (actorId != player.uniqueId.toString() || actorName != player.username) return
        val safeMessage = runCatching { InputPolicies.ALERT.validate(rawMessage) }.getOrNull() ?: return
        val rendered = Component.text("[Network] ${player.username}: $safeMessage")
        proxy.allPlayers.forEach { it.sendMessage(rendered) }
        proxy.consoleCommandSource.sendMessage(rendered)
        eventSink.emit(NetworkEventKind.ALERT, "Network alert", "${player.username}: $safeMessage")
    }

    private fun pushPlaceholderSnapshot(source: ServerConnection) {
        val values = buildMap {
            put("network_players", proxy.playerCount.toString())
            put("staff_online", proxy.allPlayers.count { it.hasPermission("veloutils.staff.member") }.toString())
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
