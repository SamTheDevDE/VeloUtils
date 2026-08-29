// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.integration.tab

import de.samthedev.veloutils.common.DurationParser
import de.samthedev.veloutils.proxy.config.ServerMetadata
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

internal data class TabPlayerState(
    val server: String?,
    val ping: Long,
)

internal data class TabBackendState(
    val playerCount: Int,
    val confirmedOnline: Boolean,
)

internal data class TabMaintenanceState(
    val active: Boolean,
    val reason: String,
)

internal interface TabDataSource {
    fun player(playerId: UUID): TabPlayerState?
    fun backendNames(): Set<String>
    fun backend(name: String): TabBackendState?
    fun networkPlayerCount(): Int
    fun maintenance(server: String?): TabMaintenanceState
}

internal class TabPlaceholderProvider(
    private val source: TabDataSource,
    metadata: Map<String, ServerMetadata>,
    private val startedAt: Instant,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val metadata = metadata.mapKeys { it.key.lowercase() }

    fun server(playerId: UUID): String = player(playerId)?.server.orEmpty()

    fun serverDisplayName(playerId: UUID): String {
        val server = player(playerId)?.server ?: return ""
        return metadata[server.lowercase()]?.displayName ?: server
    }

    fun networkPlayers(): String = source.networkPlayerCount().coerceAtLeast(0).toString()

    fun serverPlayers(playerId: UUID): String = player(playerId)?.server
        ?.let(source::backend)?.playerCount?.coerceAtLeast(0)?.toString() ?: "0"

    fun serverMaximumPlayers(playerId: UUID): String = player(playerId)?.server
        ?.lowercase()?.let(metadata::get)?.maximumPlayers?.toString() ?: "0"

    fun ping(playerId: UUID): String = player(playerId)?.ping?.coerceAtLeast(0)?.toString() ?: "0"

    fun maintenance(playerId: UUID): String = source.maintenance(player(playerId)?.server).active.toString()

    fun maintenanceReason(playerId: UUID): String = source.maintenance(player(playerId)?.server).reason

    fun uptime(): String = DurationParser.format(Duration.between(startedAt, Instant.now(clock)).coerceAtLeast(Duration.ZERO))

    fun backendCount(): String = source.backendNames().size.toString()

    fun onlineBackendCount(): String = source.backendNames().count { name ->
        source.backend(name)?.confirmedOnline == true
    }.toString()

    fun networkStatus(): String = if (source.maintenance(null).active) "maintenance" else "online"

    fun backendStatus(server: String): String = source.backend(server)?.let { backend ->
        if (backend.confirmedOnline) "online" else "unknown"
    } ?: "missing"

    fun backendPlayers(server: String): String = source.backend(server)?.playerCount?.coerceAtLeast(0)?.toString() ?: "0"

    private fun player(playerId: UUID): TabPlayerState? = source.player(playerId)
}
