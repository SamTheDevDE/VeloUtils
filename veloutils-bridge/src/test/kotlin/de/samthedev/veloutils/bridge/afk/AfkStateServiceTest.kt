// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge.afk

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AfkStateServiceTest {
    @Test
    fun `inactivity enters afk and activity clears it`() {
        val clock = MutableClock()
        val service = AfkStateService(clock)
        val playerId = UUID.randomUUID()
        service.join(playerId)
        clock.now = clock.now.plusSeconds(600)

        assertEquals(AfkTransition.ENTERED, service.update(playerId, Duration.ofMinutes(10), null))
        assertTrue(service.localSnapshot(playerId)?.afk == true)
        assertTrue(service.activity(playerId))
        assertFalse(service.localSnapshot(playerId)?.afk == true)
    }

    @Test
    fun `kick transition is emitted only once`() {
        val clock = MutableClock()
        val service = AfkStateService(clock)
        val playerId = UUID.randomUUID()
        service.join(playerId)
        service.toggle(playerId)
        clock.now = clock.now.plusSeconds(300)

        assertEquals(AfkTransition.KICK, service.update(playerId, Duration.ofMinutes(10), Duration.ofMinutes(5)))
        assertEquals(AfkTransition.NONE, service.update(playerId, Duration.ofMinutes(10), Duration.ofMinutes(5)))
    }

    private class MutableClock(var now: Instant = Instant.EPOCH) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }
}
