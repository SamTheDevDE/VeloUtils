// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.UUID

public object ProtocolVersion {
    public const val CURRENT: Int = 1
    public const val MINIMUM_SUPPORTED: Int = 1
}

@Serializable
public enum class PacketType {
    HELLO,
    HELLO_ACK,
    PLAYER_CONTEXT_REQUEST,
    PLAYER_CONTEXT_RESPONSE,
    PLAYER_COUNT_REQUEST,
    PLAYER_COUNT_RESPONSE,
    COMMAND_REQUEST,
    COMMAND_RESPONSE,
    STAFF_CHAT_MESSAGE,
    NETWORK_ALERT,
    PLACEHOLDER_REQUEST,
    PLACEHOLDER_RESPONSE,
    MUTE_STATE,
    SERVER_STATUS,
    SERVER_STATUS_REQUEST,
    HEARTBEAT,
    ERROR,
}

@Serializable
public data class Envelope(
    val version: Int,
    val type: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("sent_at_epoch_ms") val sentAtEpochMillis: Long,
    val nonce: String,
    val payload: JsonObject,
    val signature: String? = null,
) {
    public fun knownType(): PacketType? = PacketType.entries.firstOrNull { it.name == type }
}

public sealed interface DecodeResult {
    public data class Accepted(val envelope: Envelope, val type: PacketType) : DecodeResult
    public data class UnknownPacket(val envelope: Envelope) : DecodeResult
    public data class Rejected(val error: ProtocolError) : DecodeResult
}

public enum class ProtocolErrorCode {
    PAYLOAD_TOO_LARGE,
    MALFORMED_PACKET,
    INVALID_VERSION,
    INVALID_REQUEST_ID,
    INVALID_NONCE,
    STALE_PACKET,
    SIGNATURE_REQUIRED,
    INVALID_SIGNATURE,
    REPLAYED_PACKET,
}

public data class ProtocolError(val code: ProtocolErrorCode, val safeMessage: String)

@Serializable
public data class HelloPayload(
    @SerialName("plugin_version") val pluginVersion: String,
    @SerialName("minimum_protocol") val minimumProtocol: Int,
    @SerialName("maximum_protocol") val maximumProtocol: Int,
    val platform: String,
    @SerialName("minecraft_version") val minecraftVersion: String,
    val folia: Boolean,
)

@Serializable
public data class HelloAckPayload(
    val accepted: Boolean,
    @SerialName("selected_protocol") val selectedProtocol: Int?,
    val reason: String? = null,
)

@Serializable
public data class PlayerContextPayload(
    @SerialName("player_id") val playerId: String,
    val world: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
)

@Serializable
public data class PlayerCountPayload(val servers: Map<String, Int>, val total: Int)

@Serializable
public data class CommandRequestPayload(
    val command: String,
    @SerialName("authorization_scope") val authorizationScope: String,
)

@Serializable
public data class CommandResponsePayload(val accepted: Boolean, val detail: String)

@Serializable
public data class ChatPayload(
    val channel: String,
    @SerialName("player_id") val playerId: String,
    @SerialName("player_name") val playerName: String,
    val message: String,
)

@Serializable
public data class PlaceholderPayload(val values: Map<String, String>)

@Serializable
public data class MuteStatePayload(
    @SerialName("player_id") val playerId: String,
    val muted: Boolean,
    @SerialName("expires_at_epoch_ms") val expiresAtEpochMillis: Long? = null,
    val reason: String? = null,
)

@Serializable
public data class StatusPayload(
    @SerialName("plugin_version") val pluginVersion: String,
    @SerialName("protocol_version") val protocolVersion: Int,
    val implementation: String,
    @SerialName("minecraft_version") val minecraftVersion: String,
    val folia: Boolean,
    @SerialName("online_players") val onlinePlayers: Int,
)

public fun newRequestId(): String = UUID.randomUUID().toString()
public fun newNonce(): String = UUID.randomUUID().toString().replace("-", "")

public fun negotiateProtocol(localMinimum: Int, localMaximum: Int, peerMinimum: Int, peerMaximum: Int): Int? {
    require(localMinimum > 0 && peerMinimum > 0)
    require(localMaximum >= localMinimum && peerMaximum >= peerMinimum)
    val minimum = maxOf(localMinimum, peerMinimum)
    val maximum = minOf(localMaximum, peerMaximum)
    return maximum.takeIf { it >= minimum }
}

internal fun canonical(element: JsonElement): String = when (element) {
    is JsonObject -> element.entries.sortedBy(Map.Entry<String, JsonElement>::key)
        .joinToString(prefix = "{", postfix = "}") { (key, value) -> "${jsonString(key)}:${canonical(value)}" }
    is kotlinx.serialization.json.JsonArray -> element.joinToString(prefix = "[", postfix = "]") { canonical(it) }
    else -> element.toString()
}

private fun jsonString(value: String): String = kotlinx.serialization.json.Json.encodeToString(value)
