package de.samthedev.veloutils.proxy.moderation

import de.samthedev.veloutils.api.Punishment
import de.samthedev.veloutils.api.PunishmentId
import de.samthedev.veloutils.api.PunishmentScope
import de.samthedev.veloutils.api.PunishmentType
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class ModerationPolicyTest {
    @Test fun `expired and inactive punishments are ineffective`() {
        val base = Punishment(
            PunishmentId(1), PunishmentType.BAN, UUID.randomUUID(), "Player", null, "Console", "reason",
            Instant.EPOCH, Instant.ofEpochSecond(10), true, PunishmentScope.NETWORK, null,
        )
        assertEquals(1, ModerationPolicy.effective(listOf(base), Instant.ofEpochSecond(9), null).size)
        assertEquals(0, ModerationPolicy.effective(listOf(base), Instant.ofEpochSecond(10), null).size)
        assertEquals(0, ModerationPolicy.effective(listOf(base.copy(active = false)), Instant.ofEpochSecond(9), null).size)
    }
}
