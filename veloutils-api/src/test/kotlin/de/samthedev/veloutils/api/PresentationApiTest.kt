// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.api

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PresentationApiTest {
    @Test
    fun `temporary bossbars require a bounded valid window`() {
        val now = Instant.parse("2026-08-29T12:00:00Z")
        TemporaryBossBarRequest("notice", "<yellow>Notice</yellow>", now, now.plusSeconds(30))
        assertFailsWith<IllegalArgumentException> {
            TemporaryBossBarRequest("notice", "Notice", now, now)
        }
        assertFailsWith<IllegalArgumentException> {
            TemporaryBossBarRequest("notice", "Notice", now, now.plusSeconds(1), progress = 2.0f)
        }
    }

    @Test
    fun `radius channels require a usable radius`() {
        assertFailsWith<IllegalArgumentException> {
            ChatChannelDefinition("local", "<message>", ChatChannelScope.RADIUS)
        }
        ChatChannelDefinition("local", "<message>", ChatChannelScope.RADIUS, radius = 100.0)
    }
}
