// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.protocol

import de.samthedev.veloutils.common.BoundedExpiringMap
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

public data class ProtocolSecurity(
    val sharedSecret: ByteArray?,
    val requireAuthentication: Boolean,
    val maximumClockSkew: Duration = Duration.ofMinutes(2),
) {
    init {
        require(!maximumClockSkew.isNegative && !maximumClockSkew.isZero)
        require(!requireAuthentication || (sharedSecret != null && sharedSecret.size >= 32)) {
            "Authenticated mode requires a shared secret of at least 32 bytes"
        }
    }
}

public class ProtocolCodec(
    private val security: ProtocolSecurity,
    private val clock: Clock = Clock.systemUTC(),
    private val maximumPayloadBytes: Int = 32 * 1_024,
    replayCapacity: Int = 16_384,
) {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        isLenient = false
        allowSpecialFloatingPointValues = false
    }
    private val seenNonces = BoundedExpiringMap<String, Unit>(replayCapacity, security.maximumClockSkew.multipliedBy(2), clock)
    private val idPattern = Regex("[A-Za-z0-9-]{8,64}")
    private val noncePattern = Regex("[A-Za-z0-9_-]{16,96}")

    public fun envelope(type: PacketType, requestId: String, payload: JsonObject): Envelope {
        val unsigned = Envelope(
            version = ProtocolVersion.CURRENT,
            type = type.name,
            requestId = requestId,
            sentAtEpochMillis = clock.millis(),
            nonce = newNonce(),
            payload = payload,
        )
        return unsigned.copy(signature = security.sharedSecret?.let { sign(unsigned, it) })
    }

    public fun encode(envelope: Envelope): ByteArray {
        val bytes = json.encodeToString(envelope).toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= maximumPayloadBytes) { "Packet exceeds $maximumPayloadBytes bytes" }
        return bytes
    }

    public fun decode(bytes: ByteArray): DecodeResult {
        if (bytes.size > maximumPayloadBytes) {
            return DecodeResult.Rejected(ProtocolError(ProtocolErrorCode.PAYLOAD_TOO_LARGE, "Packet exceeds the configured limit"))
        }
        val envelope = try {
            json.decodeFromString<Envelope>(bytes.toString(StandardCharsets.UTF_8))
        } catch (_: SerializationException) {
            return DecodeResult.Rejected(ProtocolError(ProtocolErrorCode.MALFORMED_PACKET, "Packet is not valid protocol JSON"))
        } catch (_: IllegalArgumentException) {
            return DecodeResult.Rejected(ProtocolError(ProtocolErrorCode.MALFORMED_PACKET, "Packet is not valid UTF-8 JSON"))
        }
        if (envelope.version !in ProtocolVersion.MINIMUM_SUPPORTED..ProtocolVersion.CURRENT) {
            return rejected(ProtocolErrorCode.INVALID_VERSION, "Unsupported protocol version")
        }
        if (!idPattern.matches(envelope.requestId)) return rejected(ProtocolErrorCode.INVALID_REQUEST_ID, "Invalid request id")
        if (!noncePattern.matches(envelope.nonce)) return rejected(ProtocolErrorCode.INVALID_NONCE, "Invalid nonce")
        if (kotlin.math.abs(clock.millis() - envelope.sentAtEpochMillis) > security.maximumClockSkew.toMillis()) {
            return rejected(ProtocolErrorCode.STALE_PACKET, "Packet timestamp is outside the accepted window")
        }
        if (seenNonces[envelope.nonce] != null) return rejected(ProtocolErrorCode.REPLAYED_PACKET, "Packet nonce was already used")

        val secret = security.sharedSecret
        if (security.requireAuthentication && envelope.signature == null) {
            return rejected(ProtocolErrorCode.SIGNATURE_REQUIRED, "Packet authentication is required")
        }
        if (envelope.signature != null) {
            if (secret == null || !constantTimeEquals(envelope.signature, sign(envelope.copy(signature = null), secret))) {
                return rejected(ProtocolErrorCode.INVALID_SIGNATURE, "Packet signature is invalid")
            }
        }
        seenNonces.put(envelope.nonce, Unit)
        return envelope.knownType()?.let { DecodeResult.Accepted(envelope, it) } ?: DecodeResult.UnknownPacket(envelope)
    }

    private fun sign(envelope: Envelope, secret: ByteArray): String {
        val unsigned = buildJsonObject {
            put("version", envelope.version)
            put("type", envelope.type)
            put("request_id", envelope.requestId)
            put("sent_at_epoch_ms", envelope.sentAtEpochMillis)
            put("nonce", envelope.nonce)
            put("payload", envelope.payload)
        }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.copyOf(), "HmacSHA256"))
        return mac.doFinal(canonical(unsigned).toByteArray(StandardCharsets.UTF_8)).toHex()
    }

    private fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
        left.toByteArray(StandardCharsets.US_ASCII),
        right.toByteArray(StandardCharsets.US_ASCII),
    )

    private fun rejected(code: ProtocolErrorCode, message: String): DecodeResult.Rejected =
        DecodeResult.Rejected(ProtocolError(code, message))
}

private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
