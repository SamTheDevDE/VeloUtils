// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.moderation

import de.samthedev.veloutils.api.CreatePunishment
import de.samthedev.veloutils.api.PunishmentType
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SelfPunishmentConfirmationsTest {
    @Test
    fun `confirmation is bound to actor and consumed once`() {
        val actor = UUID.randomUUID()
        val confirmations = SelfPunishmentConfirmations(Duration.ofSeconds(30))
        val request = CreatePunishment(PunishmentType.BAN, actor, "Moderator", actor, "Moderator", "Test", null)
        val token = confirmations.begin(actor, "ban", request)

        assertNull(confirmations.consume(UUID.randomUUID(), "ban", token))
        val replacement = confirmations.begin(actor, "ban", request)
        assertNotNull(confirmations.consume(actor, "ban", replacement))
        assertNull(confirmations.consume(actor, "ban", replacement))
    }

    @Test
    fun `confirmation expires`() {
        val actor = UUID.randomUUID()
        val clock = MutableClock(Instant.parse("2026-08-27T12:00:00Z"))
        val confirmations = SelfPunishmentConfirmations(Duration.ofSeconds(30), clock)
        val request = CreatePunishment(PunishmentType.BAN, actor, "Moderator", actor, "Moderator", "Test", null)
        val token = confirmations.begin(actor, "ban", request)
        clock.now = clock.now.plusSeconds(31)

        assertNull(confirmations.consume(actor, "ban", token))
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }
}
