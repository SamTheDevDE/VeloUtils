// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.maintenance

import de.samthedev.veloutils.api.MaintenanceSnapshot
import de.samthedev.veloutils.api.MaintenanceUpdate
import de.samthedev.veloutils.api.MaintenanceWindow
import java.util.concurrent.atomic.AtomicReference

public class MaintenanceState(initial: MaintenanceSnapshot) {
    private val state = AtomicReference(normalize(initial))

    public fun snapshot(): MaintenanceSnapshot = state.get()

    public fun update(change: MaintenanceUpdate): MaintenanceSnapshot {
        while (true) {
            val before = state.get()
            val after = when (change) {
                is MaintenanceUpdate.Enable -> enable(before, change.server, MaintenanceWindow(change.reason.trim(), change.at))
                is MaintenanceUpdate.Disable -> disable(before, change.server)
                is MaintenanceUpdate.Allow -> before.copy(allowedPlayers = before.allowedPlayers + change.playerId)
                is MaintenanceUpdate.Disallow -> before.copy(allowedPlayers = before.allowedPlayers - change.playerId)
            }
            if (state.compareAndSet(before, after)) return after
        }
    }

    private fun enable(snapshot: MaintenanceSnapshot, server: String?, window: MaintenanceWindow): MaintenanceSnapshot {
        require(window.reason.isNotEmpty() && window.reason.length <= 1_024)
        return if (server == null) snapshot.copy(global = window)
        else snapshot.copy(servers = snapshot.servers + (server.lowercase() to window))
    }

    private fun disable(snapshot: MaintenanceSnapshot, server: String?): MaintenanceSnapshot =
        if (server == null) snapshot.copy(global = null)
        else snapshot.copy(servers = snapshot.servers - server.lowercase())

    private fun normalize(snapshot: MaintenanceSnapshot): MaintenanceSnapshot =
        snapshot.copy(servers = snapshot.servers.mapKeys { it.key.lowercase() })
}

