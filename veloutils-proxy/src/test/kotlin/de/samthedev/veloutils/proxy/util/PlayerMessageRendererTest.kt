// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.util

import de.samthedev.veloutils.proxy.config.PlayerFormattingConfig
import de.samthedev.veloutils.proxy.config.PlayerFormattingDefaults
import de.samthedev.veloutils.proxy.config.PlayerFormattingPermissions
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerMessageRendererTest {
    private val plain = PlainTextComponentSerializer.plainText()
    private val legacy = LegacyComponentSerializer.legacySection()
    private val config = PlayerFormattingConfig(
        enabled = true,
        defaults = PlayerFormattingDefaults(false, false, false),
        permissions = PlayerFormattingPermissions("colors", "decorations", "gradients", "full"),
    )

    @Test
    fun `player without permission cannot apply MiniMessage formatting`() {
        val renderer = PlayerMessageRenderer(config)
        val rendered = renderer.render("<red>Hello</red>", renderer.capabilities { false })

        assertEquals("<red>Hello</red>", plain.serialize(rendered))
        assertFalse(legacy.serialize(rendered).contains('§'))
    }

    @Test
    fun `color and gradient permissions enable only their configured presentation tags`() {
        val renderer = PlayerMessageRenderer(config)
        val colored = renderer.render("<red>Hello</red>", renderer.capabilities { it == "colors" })
        val gradient = renderer.render(
            "<gradient:#7c3aed:#a855f7>Hello</gradient>",
            renderer.capabilities { it == "gradients" },
        )

        assertEquals("Hello", plain.serialize(colored))
        assertTrue(legacy.serialize(colored).contains("§c"))
        assertEquals("Hello", plain.serialize(gradient))
        assertTrue(legacy.serialize(gradient).contains('§'))
    }

    @Test
    fun `full permission still rejects interactive and data driven tags`() {
        val renderer = PlayerMessageRenderer(config)
        val rendered = renderer.render(
            "<click:run_command:'/op me'><hover:show_text:'fake'><selector:@a>Hello</selector></hover></click>",
            renderer.capabilities { it == "full" },
        )

        assertTrue(plain.serialize(rendered).contains("<click:run_command"))
        assertEquals(null, rendered.clickEvent())
        assertEquals(null, rendered.hoverEvent())
    }

    @Test
    fun `formatted player component cannot alter configured siblings`() {
        val renderer = PlayerMessageRenderer(config)
        val body = renderer.render("</white><red>Hello</red><click:run_command:'/x'>X</click>", renderer.capabilities { it == "full" })
        val message = Component.text("Player ").append(body).append(Component.text(" END"))

        assertTrue(plain.serialize(message).startsWith("Player </white>Hello"))
        assertTrue(plain.serialize(message).endsWith(" END"))
        assertEquals(null, message.clickEvent())
    }
}
