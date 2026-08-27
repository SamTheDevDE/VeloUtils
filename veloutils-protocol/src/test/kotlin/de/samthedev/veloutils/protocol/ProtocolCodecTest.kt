package de.samthedev.veloutils.protocol

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

class ProtocolCodecTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC)
    private val secret = ByteArray(32) { it.toByte() }

    @Test fun `signed packet round trips`() {
        val codec = ProtocolCodec(ProtocolSecurity(secret, requireAuthentication = true), clock)
        val packet = codec.envelope(PacketType.HEARTBEAT, newRequestId(), buildJsonObject { put("players", 42) })
        val accepted = assertIs<DecodeResult.Accepted>(codec.decode(codec.encode(packet)))
        assertEquals(PacketType.HEARTBEAT, accepted.type)
        assertEquals(42, accepted.envelope.payload["players"]?.toString()?.toInt())
    }

    @Test fun `malformed oversized and incompatible packets are rejected`() {
        val codec = ProtocolCodec(ProtocolSecurity(null, requireAuthentication = false), clock, maximumPayloadBytes = 1_024)
        assertEquals(ProtocolErrorCode.MALFORMED_PACKET, (codec.decode("{".encodeToByteArray()) as DecodeResult.Rejected).error.code)
        assertEquals(ProtocolErrorCode.PAYLOAD_TOO_LARGE, (codec.decode(ByteArray(1_025)) as DecodeResult.Rejected).error.code)
        val incompatible = Envelope(99, "HEARTBEAT", newRequestId(), clock.millis(), newNonce(), buildJsonObject {})
        assertEquals(ProtocolErrorCode.INVALID_VERSION, (codec.decode(codec.encode(incompatible)) as DecodeResult.Rejected).error.code)
    }

    @Test fun `tampering and replay are rejected`() {
        val codec = ProtocolCodec(ProtocolSecurity(secret, requireAuthentication = true), clock)
        val packet = codec.envelope(PacketType.HEARTBEAT, newRequestId(), buildJsonObject {})
        val bytes = codec.encode(packet)
        assertIs<DecodeResult.Accepted>(codec.decode(bytes))
        assertEquals(ProtocolErrorCode.REPLAYED_PACKET, (codec.decode(bytes) as DecodeResult.Rejected).error.code)

        val tampered = packet.copy(payload = buildJsonObject { put("forged", true) }, nonce = newNonce())
        assertEquals(ProtocolErrorCode.INVALID_SIGNATURE, (codec.decode(codec.encode(tampered)) as DecodeResult.Rejected).error.code)
    }

    @Test fun `version negotiation chooses highest shared version`() {
        assertEquals(2, negotiateProtocol(1, 3, 2, 2))
        assertEquals(null, negotiateProtocol(3, 4, 1, 2))
    }

    @Test fun `mute state packet round trips through authenticated envelope`() {
        val codec = ProtocolCodec(ProtocolSecurity(secret, requireAuthentication = true), clock)
        val payload = MuteStatePayload("00000000-0000-0000-0000-000000000001", true, clock.millis() + 60_000, "Spam")
        val packet = codec.envelope(PacketType.MUTE_STATE, newRequestId(), Json.encodeToJsonElement(payload).jsonObject)

        val accepted = assertIs<DecodeResult.Accepted>(codec.decode(codec.encode(packet)))

        assertEquals(PacketType.MUTE_STATE, accepted.type)
        assertEquals(payload, Json.decodeFromJsonElement<MuteStatePayload>(accepted.envelope.payload))
    }

    @Test fun `delivery acknowledgements round trip for success and failure`() {
        val codec = ProtocolCodec(ProtocolSecurity(secret, requireAuthentication = true), clock)
        listOf(
            DeliveryResponsePayload(true, DeliveryStatus.SENT, 4, "Staff chat message sent to 4 recipients."),
            DeliveryResponsePayload(false, DeliveryStatus.NO_PERMISSION, 0, "Permission denied."),
        ).forEach { payload ->
            val packet = codec.envelope(PacketType.CHAT_RESPONSE, newRequestId(), Json.encodeToJsonElement(payload).jsonObject)
            val accepted = assertIs<DecodeResult.Accepted>(codec.decode(codec.encode(packet)))
            assertEquals(payload, Json.decodeFromJsonElement<DeliveryResponsePayload>(accepted.envelope.payload))
        }
    }
}
