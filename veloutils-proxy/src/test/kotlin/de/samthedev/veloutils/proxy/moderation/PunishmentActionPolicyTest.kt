// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.moderation

import de.samthedev.veloutils.api.PunishmentType
import kotlin.test.Test
import kotlin.test.assertEquals

class PunishmentActionPolicyTest {
    @Test
    fun `only permitted and valid punishment actions are visible`() {
        assertEquals(
            setOf(PunishmentAction.UNBAN, PunishmentAction.HISTORY),
            PunishmentActionPolicy.available(PunishmentType.BAN, true, true, false, true, false),
        )
        assertEquals(
            setOf(PunishmentAction.HISTORY),
            PunishmentActionPolicy.available(PunishmentType.BAN, false, true, true, true, false),
        )
        assertEquals(
            setOf(PunishmentAction.UNMUTE, PunishmentAction.CHECK_BAN),
            PunishmentActionPolicy.available(PunishmentType.MUTE, true, false, true, false, true),
        )
    }
}
