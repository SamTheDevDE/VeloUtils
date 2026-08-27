// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.command

import java.nio.file.Files
import java.time.Duration
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfiguredCommandLoaderTest {
    @Test
    fun `configured aliases servers permissions and cooldowns affect definitions`() {
        val file = Files.createTempFile("veloutils-commands", ".yml")
        file.writeText(
            """
            config-version: 1
            move-commands:
              spawn:
                aliases:
                  - hub
                servers:
                  - Lobby
                  - Limbo
                permission: custom.move.spawn
                cooldown: 9s
            message-commands:
              website:
                aliases:
                  - web
                permission: custom.message.website
                cooldown: 11s
                messages:
                  - "<green>Website</green>"
            """.trimIndent(),
        )

        val definitions = ConfiguredCommandLoader.load(file)

        assertEquals(listOf("hub"), definitions.move.single().aliases)
        assertEquals(listOf("lobby", "limbo"), definitions.move.single().servers)
        assertEquals("custom.move.spawn", definitions.move.single().permission)
        assertEquals(Duration.ofSeconds(9), definitions.move.single().cooldown)
        assertEquals(listOf("web"), definitions.message.single().aliases)
        assertEquals("custom.message.website", definitions.message.single().permission)
        assertEquals(Duration.ofSeconds(11), definitions.message.single().cooldown)
    }
}
