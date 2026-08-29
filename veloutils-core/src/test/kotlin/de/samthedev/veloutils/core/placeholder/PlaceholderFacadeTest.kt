// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.core.placeholder

import de.samthedev.veloutils.api.PlaceholderContext
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlaceholderFacadeTest {
    @Test
    fun `expired backend snapshots are discarded`() {
        val playerId = UUID.randomUUID()
        val facade = PlaceholderFacade(clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), snapshotTtl = Duration.ofSeconds(5))
        facade.updateSnapshot(playerId, mapOf("network_online" to "10"))
        assertEquals("10", facade.resolve(PlaceholderContext(playerId))["network_online"])

        val expired = PlaceholderFacade(clock = Clock.fixed(Instant.EPOCH.plusSeconds(10), ZoneOffset.UTC))
        assertFalse("network_online" in expired.resolve(PlaceholderContext(playerId)))
    }

    @Test
    fun `addon providers are namespaced and removable`() {
        val facade = PlaceholderFacade()
        val registration = facade.register("addon") { mapOf("value" to "42") }
        assertEquals("42", facade.resolve(PlaceholderContext())["addon_value"])
        registration.close()
        assertFalse("addon_value" in facade.resolve(PlaceholderContext()))
    }

    @Test
    fun `template values cannot inject minimessage`() {
        val template = TemplateRenderer("<green>{player}</green> {papi_luckperms_prefix}")
        assertEquals(setOf("player", "papi_luckperms_prefix"), template.requiredKeys())
        val rendered = template.render(mapOf("player" to "<red>attacker</red>"))
        assertEquals("<red>attacker</red> ", PlainTextComponentSerializer.plainText().serialize(rendered))
    }
}
