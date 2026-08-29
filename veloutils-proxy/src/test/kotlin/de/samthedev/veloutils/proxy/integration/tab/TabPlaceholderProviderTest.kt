// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.integration.tab

import de.samthedev.veloutils.proxy.config.ServerMetadata
import de.samthedev.veloutils.proxy.config.TabIntegrationConfig
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class TabPlaceholderProviderTest {
    private val playerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val source = FakeTabDataSource(
        players = mutableMapOf(playerId to TabPlayerState("lobby", 42)),
        backends = linkedMapOf(
            "lobby" to TabBackendState(3, true),
            "hardcore" to TabBackendState(0, false),
        ),
    )
    private val provider = TabPlaceholderProvider(
        source,
        mapOf("lobby" to ServerMetadata("<gradient:#7c3aed:#a855f7>Lobby</gradient>", 200)),
        Instant.parse("2026-08-29T10:00:00Z"),
        Clock.fixed(Instant.parse("2026-08-29T11:02:03Z"), ZoneOffset.UTC),
    )

    @Test
    fun `player and network placeholders return predictable data`() {
        assertEquals("lobby", provider.server(playerId))
        assertEquals("<gradient:#7c3aed:#a855f7>Lobby</gradient>", provider.serverDisplayName(playerId))
        assertEquals("7", provider.networkPlayers())
        assertEquals("3", provider.serverPlayers(playerId))
        assertEquals("200", provider.serverMaximumPlayers(playerId))
        assertEquals("42", provider.ping(playerId))
        assertEquals("1h 2m 3s", provider.uptime())
        assertEquals("2", provider.backendCount())
        assertEquals("1", provider.onlineBackendCount())
        assertEquals("online", provider.networkStatus())
        assertEquals("online", provider.backendStatus("LOBBY"))
        assertEquals("unknown", provider.backendStatus("hardcore"))
    }

    @Test
    fun `maintenance uses global state then current backend state`() {
        source.serverMaintenance["lobby"] = TabMaintenanceState(true, "Lobby upgrade")
        assertEquals("true", provider.maintenance(playerId))
        assertEquals("Lobby upgrade", provider.maintenanceReason(playerId))

        source.globalMaintenance = TabMaintenanceState(true, "Network upgrade")
        assertEquals("Network upgrade", provider.maintenanceReason(playerId))
        assertEquals("maintenance", provider.networkStatus())
    }

    @Test
    fun `missing player and backend values are null safe`() {
        source.players.clear()
        assertEquals("", provider.server(playerId))
        assertEquals("", provider.serverDisplayName(playerId))
        assertEquals("0", provider.serverPlayers(playerId))
        assertEquals("0", provider.serverMaximumPlayers(playerId))
        assertEquals("0", provider.ping(playerId))
        assertEquals("missing", provider.backendStatus("removed"))
        assertEquals("0", provider.backendPlayers("removed"))
    }

    @Test
    fun `optional integration policy handles disabled and unavailable TAB`() {
        assertEquals(
            TabIntegrationDecision.DISABLED,
            tabIntegrationDecision(TabIntegrationConfig(enabled = false, placeholdersEnabled = true), tabInstalled = true),
        )
        assertEquals(
            TabIntegrationDecision.DISABLED,
            tabIntegrationDecision(TabIntegrationConfig(enabled = true, placeholdersEnabled = false), tabInstalled = true),
        )
        assertEquals(
            TabIntegrationDecision.UNAVAILABLE,
            tabIntegrationDecision(TabIntegrationConfig(enabled = true, placeholdersEnabled = true), tabInstalled = false),
        )
        assertEquals(
            TabIntegrationDecision.ENABLED,
            tabIntegrationDecision(TabIntegrationConfig(enabled = true, placeholdersEnabled = true), tabInstalled = true),
        )
    }

    private class FakeTabDataSource(
        val players: MutableMap<UUID, TabPlayerState>,
        val backends: MutableMap<String, TabBackendState>,
    ) : TabDataSource {
        var globalMaintenance = TabMaintenanceState(false, "")
        val serverMaintenance = mutableMapOf<String, TabMaintenanceState>()

        override fun player(playerId: UUID): TabPlayerState? = players[playerId]
        override fun backendNames(): Set<String> = backends.keys
        override fun backend(name: String): TabBackendState? = backends.entries
            .firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        override fun networkPlayerCount(): Int = 7
        override fun maintenance(server: String?): TabMaintenanceState = if (globalMaintenance.active) globalMaintenance
        else server?.lowercase()?.let(serverMaintenance::get) ?: TabMaintenanceState(false, "")
    }
}
