package de.samthedev.veloutils.common

import de.samthedev.veloutils.api.AccessDecision
import de.samthedev.veloutils.api.MaintenanceSnapshot
import de.samthedev.veloutils.api.MaintenanceWindow
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AccessPoliciesTest {
    private val player = UUID.randomUUID()

    @Test fun `maintenance respects UUID allowlist and bypass`() {
        val snapshot = MaintenanceSnapshot(MaintenanceWindow("upgrade", Instant.EPOCH), emptyMap(), setOf(player))
        assertIs<AccessDecision.Allowed>(MaintenanceAccessPolicy().decide(snapshot, player, emptySet(), null))
        assertIs<AccessDecision.Allowed>(MaintenanceAccessPolicy().decide(snapshot, UUID.randomUUID(), setOf("veloutils.maintenance.bypass"), null))
        assertIs<AccessDecision.Denied>(MaintenanceAccessPolicy().decide(snapshot, UUID.randomUUID(), emptySet(), null))
    }

    @Test fun `server rule denies without required permission`() {
        val rule = ServerAccessRule("veloutils.server.builder", fallbackServers = listOf("lobby"))
        val denied = ServerAccessPolicy().decide(player, emptySet(), rule)
        assertEquals(listOf("lobby"), (denied as AccessDecision.Denied).fallbackServers)
    }

    @Test fun `fallback skips current unavailable and unauthorized servers`() {
        val selected = FallbackSelector.select(
            listOf("BUILD", "limbo", "lobby", "lobby"),
            currentServer = "build",
            available = setOf("limbo", "lobby"),
            authorized = { it != "limbo" },
        )
        assertEquals("lobby", selected)
    }
}
