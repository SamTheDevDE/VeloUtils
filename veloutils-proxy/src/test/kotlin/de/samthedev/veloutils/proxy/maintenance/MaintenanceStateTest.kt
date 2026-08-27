package de.samthedev.veloutils.proxy.maintenance

import de.samthedev.veloutils.api.MaintenanceSnapshot
import de.samthedev.veloutils.api.MaintenanceUpdate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MaintenanceStateTest {
    @Test fun `updates global server and allowlist state`() {
        val state = MaintenanceState(MaintenanceSnapshot(null, emptyMap(), emptySet()))
        state.update(MaintenanceUpdate.Enable(null, "upgrade"))
        state.update(MaintenanceUpdate.Enable("Lobby", "restart"))
        val player = UUID.randomUUID()
        state.update(MaintenanceUpdate.Allow(player))
        assertNotNull(state.snapshot().global)
        assertNotNull(state.snapshot().servers["lobby"])
        assertEquals(setOf(player), state.snapshot().allowedPlayers)
        state.update(MaintenanceUpdate.Disable(null))
        assertNull(state.snapshot().global)
    }
}
