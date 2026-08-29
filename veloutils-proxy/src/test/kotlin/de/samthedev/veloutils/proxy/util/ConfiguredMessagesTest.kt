// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.util

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

class ConfiguredMessagesTest {
    private val plain = PlainTextComponentSerializer.plainText()

    @Test
    fun `unknown message returns a safe component and warns only once`() {
        val file = Files.createTempFile("veloutils-messages-missing", ".yml")
        file.writeText("config-version: 1\n")
        val warnings = mutableListOf<String>()
        val messages = ConfiguredMessages(file, warnings::add)
        messages.reload()

        val first = messages.render("does.not.exist")
        val second = messages.render("does.not.exist")

        assertEquals("Missing message: does.not.exist", plain.serialize(first))
        assertEquals(first, second)
        assertEquals(1, warnings.count { it.contains("does.not.exist") })
    }

    @Test
    fun `old messages file falls back to bundled global chat template`() {
        val file = Files.createTempFile("veloutils-messages-old", ".yml")
        file.writeText("config-version: 1\nno-permission: \"<red>Denied</red>\"\n")
        val warnings = mutableListOf<String>()
        val messages = ConfiguredMessages(file, warnings::add)
        messages.reload()

        val rendered = messages.render(
            "chat.global-format",
            mapOf("player" to Component.text("Alex"), "message" to Component.text("Hello")),
        )

        assertEquals("[Global] Alex » Hello", plain.serialize(rendered))
        assertEquals(1, warnings.count { it.contains("chat.global-format") })
    }

    @Test
    fun `configured global chat supports gradients and component placeholders without injection`() {
        val file = Files.createTempFile("veloutils-messages-gradient", ".yml")
        file.writeText(
            "chat:\n  global-format: \"<gradient:#7c3aed:#a855f7>[Global]</gradient> <green><player></green> <white><message></white> <blue>END</blue>\"\n",
        )
        val messages = ConfiguredMessages(file)
        messages.reload()
        val rendered = messages.render(
            "chat.global-format",
            mapOf(
                "player" to Component.text("Alex"),
                "message" to Component.text("</white><red>Injected</red>"),
            ),
        )

        assertEquals("[Global] Alex </white><red>Injected</red> END", plain.serialize(rendered))
        val legacy = LegacyComponentSerializer.builder().character('§').hexColors()
            .useUnusualXRepeatedCharacterHexFormat().build().serialize(rendered)
        assertTrue(legacy.contains("§x"))
        assertTrue(legacy.contains("§aAlex"))
        assertTrue(legacy.contains("§9END"))
    }

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
