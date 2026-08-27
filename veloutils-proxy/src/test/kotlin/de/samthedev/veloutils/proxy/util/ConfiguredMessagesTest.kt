// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.util

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConfiguredMessagesTest {
    @Test
    fun `invalid MiniMessage identifies the failing setting`() {
        val file = Files.createTempFile("veloutils-messages", ".yml")
        file.writeText("no-permission: \"<red>Missing closing tag\"\n")

        val failure = assertFailsWith<IllegalArgumentException> { ConfiguredMessages(file).validate() }

        assertTrue(failure.message.orEmpty().contains("no-permission"))
    }

    @Test
    fun `legacy reset tags and removed message keys do not prevent startup`() {
        val file = Files.createTempFile("veloutils-legacy-messages", ".yml")
        file.writeText(
            """
            prefix: "<dark_gray>[<gradient:#7c3aed:#a855f7>VeloUtils</gradient><dark_gray>] <reset>"
            no-permission: "<red>Denied<reset>"
            """.trimIndent(),
        )
        val messages = ConfiguredMessages(file)

        messages.reload()

        assertNotNull(messages.render("no-permission"))
    }
}
