// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge.player

import de.samthedev.veloutils.protocol.MuteStatePayload
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MuteStateCacheTest {
    private val now = Instant.parse("2026-08-27T12:00:00Z")
    private val playerId = UUID.randomUUID()

    @Test
    fun `stores active mute and removes explicit unmute`() {
        val cache = MuteStateCache(Clock.fixed(now, ZoneOffset.UTC))
        assertTrue(cache.update(MuteStatePayload(playerId.toString(), true, now.plusSeconds(60).toEpochMilli(), "Spam")))
        assertEquals("Spam", cache.active(playerId)?.reason)
        assertTrue(cache.update(MuteStatePayload(playerId.toString(), false)))
        assertNull(cache.active(playerId))
    }

    @Test
    fun `rejects invalid identity and ignores expired state`() {
        val cache = MuteStateCache(Clock.fixed(now, ZoneOffset.UTC))
        assertFalse(cache.update(MuteStatePayload("not-a-uuid", true, null, "Spam")))
        assertTrue(cache.update(MuteStatePayload(playerId.toString(), true, now.minusSeconds(1).toEpochMilli(), "Expired")))
        assertNull(cache.active(playerId))
    }
}
