// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge.messaging

import de.samthedev.veloutils.common.InputPolicies
import de.samthedev.veloutils.protocol.ChatPayload
import de.samthedev.veloutils.protocol.CommandRequestPayload
import de.samthedev.veloutils.protocol.CommandResponsePayload
import de.samthedev.veloutils.protocol.PlaceholderPayload
import de.samthedev.veloutils.protocol.MuteStatePayload
import de.samthedev.veloutils.protocol.DecodeResult
import de.samthedev.veloutils.protocol.HelloPayload
import de.samthedev.veloutils.protocol.PacketType
import de.samthedev.veloutils.protocol.ProtocolCodec
import de.samthedev.veloutils.protocol.ProtocolVersion
import de.samthedev.veloutils.protocol.StatusPayload
import de.samthedev.veloutils.protocol.newRequestId
import de.samthedev.veloutils.common.RemoteCommandPolicy
import de.samthedev.veloutils.bridge.server.PlatformSchedulers
import de.samthedev.veloutils.bridge.placeholder.NetworkPlaceholderCache
import de.samthedev.veloutils.bridge.player.MuteEnforcement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.messaging.PluginMessageListener

public class BridgeProtocolGateway(
    private val plugin: Plugin,
    private val codec: ProtocolCodec,
    private val isFolia: Boolean,
    private val schedulers: PlatformSchedulers,
    private val remoteCommands: RemoteCommandPolicy,
    private val placeholders: NetworkPlaceholderCache,
    private val muteEnforcement: MuteEnforcement,
    private val authenticatedMode: Boolean,
) : PluginMessageListener {
    public companion object { public const val CHANNEL: String = "veloutils:main" }
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    public fun hello(carrier: Player) {
        val payload = HelloPayload(
            plugin.pluginMeta.version,
            ProtocolVersion.MINIMUM_SUPPORTED,
            ProtocolVersion.CURRENT,
            plugin.server.name,
            plugin.server.minecraftVersion,
            isFolia,
        )
        send(carrier, PacketType.HELLO, newRequestId(), json.encodeToJsonElement(payload).jsonObject)
    }

    public fun heartbeat(carrier: Player) {
        val payload = StatusPayload(
            plugin.pluginMeta.version,
            ProtocolVersion.CURRENT,
            plugin.server.name,
            plugin.server.minecraftVersion,
            isFolia,
            plugin.server.onlinePlayers.size,
        )
        send(carrier, PacketType.HEARTBEAT, newRequestId(), json.encodeToJsonElement(payload).jsonObject)
    }

    public fun chat(carrier: Player, channel: String, message: String) {
        val safe = InputPolicies.CHAT.validate(message)
        val payload = ChatPayload(channel, carrier.uniqueId.toString(), carrier.name, safe)
        send(carrier, PacketType.STAFF_CHAT_MESSAGE, newRequestId(), json.encodeToJsonElement(payload).jsonObject)
    }

    public fun alert(carrier: Player, message: String) {
        val safe = InputPolicies.ALERT.validate(message)
        val payload = kotlinx.serialization.json.buildJsonObject {
            put("actor_id", carrier.uniqueId.toString())
            put("actor_name", carrier.name)
            put("message", safe)
        }
        send(carrier, PacketType.NETWORK_ALERT, newRequestId(), payload)
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != CHANNEL) return
        when (val result = codec.decode(message)) {
            is DecodeResult.Accepted -> when (result.type) {
                PacketType.HELLO_ACK -> plugin.logger.info("VeloUtils proxy handshake acknowledged.")
                PacketType.COMMAND_REQUEST -> handleCommand(player, result.envelope.requestId, result.envelope.payload)
                PacketType.PLACEHOLDER_RESPONSE -> runCatching {
                    json.decodeFromJsonElement<PlaceholderPayload>(result.envelope.payload)
                }.onSuccess { placeholders.update(it.values) }
                PacketType.MUTE_STATE -> if (authenticatedMode) {
                    runCatching { json.decodeFromJsonElement<MuteStatePayload>(result.envelope.payload) }
                        .onSuccess(muteEnforcement::update)
                        .onFailure { plugin.logger.warning("Rejected malformed mute-state packet.") }
                } else plugin.logger.warning("Ignored mute-state packet because protocol authentication is disabled.")
                else -> plugin.logger.fine("Ignored unsupported VeloUtils proxy packet ${result.type}.")
            }
            is DecodeResult.Rejected -> plugin.logger.warning("Rejected VeloUtils proxy packet: ${result.error.code}")
            is DecodeResult.UnknownPacket -> plugin.logger.fine("Ignored unknown VeloUtils packet type.")
        }
    }

    private fun handleCommand(player: Player, requestId: String, payload: kotlinx.serialization.json.JsonObject) {
        val request = runCatching { json.decodeFromJsonElement<CommandRequestPayload>(payload) }.getOrElse {
            respondToCommand(player, requestId, false, "Malformed command request")
            return
        }
        val command = runCatching { remoteCommands.validate(request.command) }.getOrElse {
            respondToCommand(player, requestId, false, "Command rejected by backend policy")
            return
        }
        schedulers.global {
            val accepted = plugin.server.dispatchCommand(plugin.server.consoleSender, command)
            plugin.logger.info("Executed authenticated VeloUtils command root: ${command.substringBefore(' ')}")
            schedulers.entity(player) {
                respondToCommand(player, requestId, accepted, if (accepted) "Dispatched" else "Backend rejected command")
            }
        }
    }

    private fun respondToCommand(player: Player, requestId: String, accepted: Boolean, detail: String) {
        val payload = json.encodeToJsonElement(CommandResponsePayload(accepted, detail)).jsonObject
        send(player, PacketType.COMMAND_RESPONSE, requestId, payload)
    }

    private fun send(carrier: Player, type: PacketType, requestId: String, payload: kotlinx.serialization.json.JsonObject) {
        carrier.sendPluginMessage(plugin, CHANNEL, codec.encode(codec.envelope(type, requestId, payload)))
    }
}
