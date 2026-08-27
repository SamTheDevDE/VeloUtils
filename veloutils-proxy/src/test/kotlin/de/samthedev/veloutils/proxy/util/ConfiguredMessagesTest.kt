// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.util

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfiguredMessagesTest {
    @Test
    fun `invalid MiniMessage identifies the failing setting`() {
        val file = Files.createTempFile("veloutils-messages", ".yml")
        file.writeText("broken: \"<red>Missing closing tag\"\n")

        val failure = assertFailsWith<IllegalArgumentException> { ConfiguredMessages(file).validate() }

        assertTrue(failure.message.orEmpty().contains("broken"))
    }
}
