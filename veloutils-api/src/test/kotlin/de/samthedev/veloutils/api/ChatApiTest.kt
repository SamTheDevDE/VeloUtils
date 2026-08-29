// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.api

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ChatApiTest {
    @Test
    fun `radius channels require a usable radius`() {
        assertFailsWith<IllegalArgumentException> {
            ChatChannelDefinition("local", "<message>", ChatChannelScope.RADIUS)
        }
        ChatChannelDefinition("local", "<message>", ChatChannelScope.RADIUS, radius = 100.0)
    }
}
