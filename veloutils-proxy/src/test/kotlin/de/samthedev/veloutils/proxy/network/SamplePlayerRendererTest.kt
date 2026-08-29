// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SamplePlayerRendererTest {
    @Test
    fun `MiniMessage colors become section sign legacy output`() {
        val rendered = SamplePlayerRenderer(listOf("<red>Hello</red>")).render(emptyMap()).single()

        assertTrue(rendered.contains("§c"))
        assertTrue(rendered.endsWith("Hello"))
        assertFalse(rendered.contains("<red>"))
    }

    @Test
    fun `hex gradients use modern repeated section sign RGB output`() {
        val rendered = SamplePlayerRenderer(
            listOf("<gradient:#7c3aed:#a855f7><bold>Network</bold></gradient>"),
        ).render(emptyMap()).single()

        assertTrue(rendered.contains("§x"))
        assertTrue(rendered.contains("§l"))
        assertFalse(rendered.contains("<gradient"))
    }

    @Test
    fun `dynamic player values are injected without reparsing as MiniMessage`() {
        val renderer = SamplePlayerRenderer(
            listOf("<gray>Players Online: <white>{players}</white>/<white>{max_players}</white></gray>"),
        )
        val rendered = renderer.render(mapOf("players" to "42<red>", "max_players" to "1000")).single()

        assertTrue(rendered.contains("42<red>"))
        assertTrue(rendered.contains("1000"))
        assertFalse(rendered.contains("{players}"))
    }

    @Test
    fun `complete configured sample list renders`() {
        val networkName = "Torus" + "MC Network"
        val templates = listOf(
            "<gradient:#7c3aed:#a855f7><bold>✦ $networkName ✦</bold></gradient>",
            "<gray>Players Online: <white>{players}</white></gray>",
            "<gray>Hardcore <dark_gray>•</dark_gray> Lobby <dark_gray>•</dark_gray> Builder</gray>",
            "<dark_gray>━━━━━━━━━━━━━━━━━━━━</dark_gray>",
            "<light_purple>➜</light_purple> <white>play.example.com</white>",
        )

        assertEquals(5, SamplePlayerRenderer(templates).render(mapOf("players" to "12", "max_players" to "1000")).size)
    }
}
