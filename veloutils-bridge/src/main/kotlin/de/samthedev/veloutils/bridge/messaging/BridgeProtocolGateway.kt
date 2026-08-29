// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge.messaging

import de.samthedev.veloutils.common.InputPolicies
import de.samthedev.veloutils.protocol.ChatPayload
import de.samthedev.veloutils.protocol.AlertPayload
import de.samthedev.veloutils.protocol.DeliveryResponsePayload
import de.samthedev.veloutils.protocol.DeliveryStatus
import de.samthedev.veloutils.protocol.CommandRequestPayload
import de.samthedev.veloutils.protocol.CommandResponsePayload
import de.samthedev.veloutils.protocol.PlaceholderPayload
import de.samthedev.veloutils.protocol.MuteStatePayload
import de.samthedev.veloutils.protocol.PrivateMessageRequestPayload
import de.samthedev.veloutils.protocol.PrivateMessageDeliveryPayload
import de.samthedev.veloutils.protocol.IgnoreUpdateRequestPayload
import de.samthedev.veloutils.protocol.IgnoreListRequestPayload
import de.samthedev.veloutils.protocol.IgnoreUpdateResponsePayload
import de.samthedev.veloutils.protocol.IgnoreListResponsePayload
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
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.concurrent.ConcurrentHashMap

public class BridgeProtocolGateway(
    private val plugin: Plugin,
    private val codec: ProtocolCodec,
    private val isFolia: Boolean,
    private val schedulers: PlatformSchedulers,
    private val remoteCommands: RemoteCommandPolicy,
    private val placeholders: NetworkPlaceholderCache?,
    private val muteEnforcement: () -> MuteEnforcement?,
    private val privateMessageHandler: (PrivateMessageDeliveryPayload) -> DeliveryStatus,
    private val authenticatedMode: Boolean,
) : PluginMessageListener {
    public companion object { public const val CHANNEL: String = "veloutils:main" }
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private data class PendingFeedback(val sender: CommandSender, val label: String)
    private val pendingFeedback = ConcurrentHashMap<String, PendingFeedback>()
    private val pendingIgnoreUpdates = ConcurrentHashMap<String, (IgnoreUpdateResponsePayload) -> Unit>()
    private val pendingIgnoreLists = ConcurrentHashMap<String, (IgnoreListResponsePayload) -> Unit>()

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
        sendWithFeedback(
            carrier, carrier, PacketType.STAFF_CHAT_MESSAGE, channel.replaceFirstChar(Char::uppercase) + " Chat",
            json.encodeToJsonElement(payload).jsonObject,
        )
    }

    public fun alert(sender: CommandSender, carrier: Player, message: String) {
        val safe = InputPolicies.ALERT.validate(message)
        val player = sender as? Player
        val payload = AlertPayload(player?.uniqueId?.toString(), player?.name ?: "CONSOLE", player == null, safe)
        sendWithFeedback(sender, carrier, PacketType.NETWORK_ALERT, "Network alert", json.encodeToJsonElement(payload).jsonObject)
    }

    public fun privateMessage(sender: Player, targetName: String, message: String): Boolean {
        val safe = InputPolicies.CHAT.validate(message)
        val payload = PrivateMessageRequestPayload(sender.uniqueId.toString(), sender.name, targetName, safe)
        sendWithFeedback(
            sender,
            sender,
            PacketType.PRIVATE_MESSAGE_REQUEST,
            "Private message",
            json.encodeToJsonElement(payload).jsonObject,
        )
        return true
    }

    public fun updateIgnore(
        player: Player,
        targetName: String,
        ignored: Boolean,
        callback: (IgnoreUpdateResponsePayload) -> Unit,
    ) {
        val requestId = newRequestId()
        pendingIgnoreUpdates[requestId] = callback
        val payload = IgnoreUpdateRequestPayload(player.uniqueId.toString(), player.name, targetName, ignored)
        runCatching { send(player, PacketType.IGNORE_UPDATE_REQUEST, requestId, json.encodeToJsonElement(payload).jsonObject) }
            .onFailure {
                pendingIgnoreUpdates.remove(requestId)
                callback(IgnoreUpdateResponsePayload(false, "The proxy bridge is unavailable."))
            }
        schedulers.laterAsync(6) {
            pendingIgnoreUpdates.remove(requestId)?.invoke(IgnoreUpdateResponsePayload(false, "The proxy did not acknowledge the ignore request."))
        }
    }

    public fun requestIgnoreList(player: Player, callback: (IgnoreListResponsePayload) -> Unit) {
        val requestId = newRequestId()
        pendingIgnoreLists[requestId] = callback
        val payload = IgnoreListRequestPayload(player.uniqueId.toString(), player.name)
        runCatching { send(player, PacketType.IGNORE_LIST_REQUEST, requestId, json.encodeToJsonElement(payload).jsonObject) }
            .onFailure { pendingIgnoreLists.remove(requestId) }
        schedulers.laterAsync(6) { pendingIgnoreLists.remove(requestId) }
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != CHANNEL) return
        when (val result = codec.decode(message)) {
            is DecodeResult.Accepted -> when (result.type) {
                PacketType.HELLO_ACK -> plugin.logger.info("VeloUtils proxy handshake acknowledged.")
                PacketType.COMMAND_REQUEST -> handleCommand(player, result.envelope.requestId, result.envelope.payload)
                PacketType.PLACEHOLDER_RESPONSE -> runCatching {
                    json.decodeFromJsonElement<PlaceholderPayload>(result.envelope.payload)
                }.onSuccess { placeholders?.update(it.values) }
                PacketType.MUTE_STATE -> if (authenticatedMode && muteEnforcement() != null) {
                    runCatching { json.decodeFromJsonElement<MuteStatePayload>(result.envelope.payload) }
                        .onSuccess { muteEnforcement()?.update(it) }
                        .onFailure { plugin.logger.warning("Rejected malformed mute-state packet.") }
                } else plugin.logger.fine("Ignored mute-state packet because moderation or protocol authentication is disabled.")
                PacketType.PRIVATE_MESSAGE_DELIVERY -> {
                    val delivery = runCatching {
                        json.decodeFromJsonElement<PrivateMessageDeliveryPayload>(result.envelope.payload)
                    }.getOrNull()
                    val status = if (delivery == null) DeliveryStatus.INVALID_MESSAGE else privateMessageHandler(delivery)
                    val detail = when (status) {
                        DeliveryStatus.SENT -> "Message delivered to ${delivery?.targetName.orEmpty()}."
                        DeliveryStatus.IGNORED -> "That player is ignoring you."
                        DeliveryStatus.NO_RECIPIENTS -> "That player is no longer on the target backend."
                        else -> "The target backend rejected the private message."
                    }
                    val response = DeliveryResponsePayload(status == DeliveryStatus.SENT, status, if (status == DeliveryStatus.SENT) 1 else 0, detail)
                    send(player, PacketType.PRIVATE_MESSAGE_DELIVERY_RESPONSE, result.envelope.requestId, json.encodeToJsonElement(response).jsonObject)
                }
                PacketType.CHAT_RESPONSE, PacketType.ALERT_RESPONSE, PacketType.PRIVATE_MESSAGE_RESPONSE ->
                    handleDeliveryResponse(result.envelope.requestId, result.envelope.payload)
                PacketType.IGNORE_UPDATE_RESPONSE -> {
                    val callback = pendingIgnoreUpdates.remove(result.envelope.requestId) ?: return
                    runCatching { json.decodeFromJsonElement<IgnoreUpdateResponsePayload>(result.envelope.payload) }
                        .onSuccess(callback)
                }
                PacketType.IGNORE_LIST_RESPONSE -> {
                    val callback = pendingIgnoreLists.remove(result.envelope.requestId) ?: return
                    runCatching { json.decodeFromJsonElement<IgnoreListResponsePayload>(result.envelope.payload) }
                        .onSuccess(callback)
                }
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

    private fun sendWithFeedback(
        sender: CommandSender,
        carrier: Player,
        type: PacketType,
        label: String,
        payload: kotlinx.serialization.json.JsonObject,
    ) {
        val requestId = newRequestId()
        pendingFeedback[requestId] = PendingFeedback(sender, label)
        runCatching {
            send(carrier, type, requestId, payload)
        }.onFailure {
            pendingFeedback.remove(requestId)
            feedback(sender, "VeloUtils could not send the $label request to the proxy.")
            return
        }
        schedulers.laterAsync(6) {
            val pending = pendingFeedback.remove(requestId) ?: return@laterAsync
            feedback(pending.sender, "The proxy did not acknowledge the ${pending.label.lowercase()}; check bridge availability and authentication settings.")
        }
    }

    private fun handleDeliveryResponse(requestId: String, payload: kotlinx.serialization.json.JsonObject) {
        val pending = pendingFeedback.remove(requestId) ?: return
        val response = runCatching { json.decodeFromJsonElement<DeliveryResponsePayload>(payload) }.getOrNull()
        if (response == null) {
            feedback(pending.sender, "The proxy returned an invalid ${pending.label.lowercase()} response.")
            return
        }
        val prefix = when (response.status) {
            DeliveryStatus.SENT -> "Success: "
            DeliveryStatus.NO_RECIPIENTS -> "Notice: "
            else -> "Failed: "
        }
        feedback(pending.sender, prefix + response.detail)
    }

    private fun feedback(sender: CommandSender, message: String) {
        val component = net.kyori.adventure.text.Component.text(message)
        if (sender is Player) schedulers.entity(sender) { sender.sendMessage(component) }
        else schedulers.global { sender.sendMessage(component) }
    }

    private fun send(carrier: Player, type: PacketType, requestId: String, payload: kotlinx.serialization.json.JsonObject) {
        carrier.sendPluginMessage(plugin, CHANNEL, codec.encode(codec.envelope(type, requestId, payload)))
    }
}
