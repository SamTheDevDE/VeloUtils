// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.maintenance

import de.samthedev.veloutils.api.AccessDecision
import de.samthedev.veloutils.api.MaintenanceService
import de.samthedev.veloutils.api.MaintenanceSnapshot
import de.samthedev.veloutils.api.MaintenanceUpdate
import de.samthedev.veloutils.api.MaintenanceWindow
import de.samthedev.veloutils.common.MaintenanceAccessPolicy
import de.samthedev.veloutils.proxy.storage.StorageProvider
import java.time.Instant
import java.util.UUID

public class PersistentMaintenanceService(
    private val storage: StorageProvider,
    private val policy: MaintenanceAccessPolicy = MaintenanceAccessPolicy(),
) : MaintenanceService {
    private val state = MaintenanceState(MaintenanceSnapshot(null, emptyMap(), emptySet()))

    public suspend fun load() {
        val loaded = storage.read { connection ->
            val windows = connection.prepareStatement(
                "SELECT scope, reason, activated_at, scheduled_end FROM maintenance_state WHERE active = ?",
            ).use { statement ->
                statement.setBoolean(1, true)
                statement.executeQuery().use { result ->
                    buildMap {
                        while (result.next()) {
                            val activated = result.getLong("activated_at").takeUnless { result.wasNull() }
                                ?.let(Instant::ofEpochMilli) ?: Instant.EPOCH
                            val end = result.getLong("scheduled_end").takeUnless { result.wasNull() }?.let(Instant::ofEpochMilli)
                            put(result.getString("scope"), MaintenanceWindow(result.getString("reason"), activated, end))
                        }
                    }
                }
            }
            val allowed = connection.prepareStatement("SELECT player_uuid FROM maintenance_allowlist").use { statement ->
                statement.executeQuery().use { result -> buildSet { while (result.next()) add(UUID.fromString(result.getString(1))) } }
            }
            MaintenanceSnapshot(windows[GLOBAL_SCOPE], windows.filterKeys { it != GLOBAL_SCOPE }, allowed)
        }
        loaded.global?.let { state.update(MaintenanceUpdate.Enable(null, it.reason, it.activatedAt)) }
        loaded.servers.forEach { (server, window) -> state.update(MaintenanceUpdate.Enable(server, window.reason, window.activatedAt)) }
        loaded.allowedPlayers.forEach { state.update(MaintenanceUpdate.Allow(it)) }
    }

    override fun snapshot(): MaintenanceSnapshot = state.snapshot()

    override fun access(playerId: UUID, permissions: Set<String>, server: String?): AccessDecision =
        policy.decide(snapshot(), playerId, permissions, server)

    override suspend fun update(request: MaintenanceUpdate): MaintenanceSnapshot {
        storage.transaction { connection ->
            when (request) {
                is MaintenanceUpdate.Enable -> {
                    val scope = request.server?.lowercase() ?: GLOBAL_SCOPE
                    connection.prepareStatement("DELETE FROM maintenance_state WHERE scope = ?").use {
                        it.setString(1, scope)
                        it.executeUpdate()
                    }
                    connection.prepareStatement(
                        "INSERT INTO maintenance_state(scope, active, reason, activated_at, scheduled_start, scheduled_end) VALUES (?, ?, ?, ?, ?, ?)",
                    ).use {
                        it.setString(1, scope)
                        it.setBoolean(2, true)
                        it.setString(3, request.reason.trim())
                        it.setLong(4, request.at.toEpochMilli())
                        it.setObject(5, null)
                        it.setObject(6, null)
                        it.executeUpdate()
                    }
                }
                is MaintenanceUpdate.Disable -> connection.prepareStatement("DELETE FROM maintenance_state WHERE scope = ?").use {
                    it.setString(1, request.server?.lowercase() ?: GLOBAL_SCOPE)
                    it.executeUpdate()
                }
                is MaintenanceUpdate.Allow -> connection.prepareStatement(
                    "INSERT INTO maintenance_allowlist(player_uuid, added_at, added_by) VALUES (?, ?, ?)",
                ).use {
                    connection.prepareStatement("DELETE FROM maintenance_allowlist WHERE player_uuid = ?").use { delete ->
                        delete.setString(1, request.playerId.toString())
                        delete.executeUpdate()
                    }
                    it.setString(1, request.playerId.toString())
                    it.setLong(2, System.currentTimeMillis())
                    it.setObject(3, null)
                    it.executeUpdate()
                }
                is MaintenanceUpdate.Disallow -> connection.prepareStatement(
                    "DELETE FROM maintenance_allowlist WHERE player_uuid = ?",
                ).use {
                    it.setString(1, request.playerId.toString())
                    it.executeUpdate()
                }
            }
        }
        return state.update(request)
    }

    private companion object { private const val GLOBAL_SCOPE: String = "global" }
}
