// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.api

import java.net.InetAddress
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Stable service entry point exposed through Velocity's service manager. */
public interface VeloUtilsApi {
    public val network: NetworkService
    public val maintenance: MaintenanceService
    public val staff: StaffService
    public val reports: ReportService
    public val moderation: ModerationService
}

public interface NetworkService {
    public fun snapshot(): NetworkSnapshot
    public fun server(name: String): ServerSnapshot?
    public suspend fun connect(playerId: UUID, destinations: List<String>): ConnectionOutcome
    public suspend fun broadcast(message: PlainUserText)
}

public interface MaintenanceService {
    public fun snapshot(): MaintenanceSnapshot
    public fun access(playerId: UUID, permissions: Set<String>, server: String? = null): AccessDecision
    public suspend fun update(request: MaintenanceUpdate): MaintenanceSnapshot
}

public interface StaffService {
    public fun onlineStaff(): List<StaffMemberSnapshot>
    public fun session(playerId: UUID): StaffSessionSnapshot?
    public suspend fun trackedTime(playerId: UUID, from: Instant, until: Instant): Duration
}

public interface ReportService {
    public suspend fun create(request: CreateReport): Report
    public suspend fun find(id: ReportId): Report?
    public suspend fun history(subject: UUID, limit: Int = 50): List<Report>
    public suspend fun claim(id: ReportId, staffId: UUID, staffName: String): Report
    public suspend fun close(id: ReportId, actorId: UUID?, resolution: String): Report
}

public interface ModerationService {
    public suspend fun punish(request: CreatePunishment): Punishment
    public suspend fun revoke(id: PunishmentId, actorId: UUID?, reason: String): Punishment
    public suspend fun activeFor(playerId: UUID, address: InetAddress? = null): List<Punishment>
    public suspend fun history(playerId: UUID, limit: Int = 100): List<Punishment>
}

@JvmInline
public value class ReportId(public val value: Long) {
    init { require(value > 0) { "Report id must be positive" } }
}

@JvmInline
public value class PunishmentId(public val value: Long) {
    init { require(value > 0) { "Punishment id must be positive" } }
}

@JvmInline
public value class PlainUserText(public val value: String) {
    init { require(value.isNotBlank()) { "Text must not be blank" } }
}

public data class NetworkSnapshot(
    val capturedAt: Instant,
    val playerCount: Int,
    val servers: List<ServerSnapshot>,
)

public data class ServerSnapshot(
    val name: String,
    val online: Boolean,
    val playerCount: Int,
    val bridge: BridgeSnapshot?,
)

public data class BridgeSnapshot(
    val pluginVersion: String,
    val protocolVersion: Int,
    val implementation: String,
    val minecraftVersion: String,
    val folia: Boolean,
    val lastHeartbeat: Instant,
)

public sealed interface ConnectionOutcome {
    public data class Connected(val server: String) : ConnectionOutcome
    public data class Denied(val reason: String) : ConnectionOutcome
    public data object NoDestinationAvailable : ConnectionOutcome
    public data object PlayerOffline : ConnectionOutcome
}

public data class MaintenanceSnapshot(
    val global: MaintenanceWindow?,
    val servers: Map<String, MaintenanceWindow>,
    val allowedPlayers: Set<UUID>,
)

public data class MaintenanceWindow(
    val reason: String,
    val activatedAt: Instant,
    val scheduledEnd: Instant? = null,
)

public sealed interface MaintenanceUpdate {
    public data class Enable(val server: String?, val reason: String, val at: Instant = Instant.now()) : MaintenanceUpdate
    public data class Disable(val server: String?) : MaintenanceUpdate
    public data class Allow(val playerId: UUID) : MaintenanceUpdate
    public data class Disallow(val playerId: UUID) : MaintenanceUpdate
}

public sealed interface AccessDecision {
    public data object Allowed : AccessDecision
    public data class Denied(val messageKey: String, val fallbackServers: List<String> = emptyList()) : AccessDecision
}

public data class StaffMemberSnapshot(
    val playerId: UUID,
    val name: String,
    val server: String?,
    val rank: String?,
    val sessionStartedAt: Instant,
)

public data class StaffSessionSnapshot(
    val playerId: UUID,
    val startedAt: Instant,
    val currentServer: String?,
    val timePerServer: Map<String, Duration>,
)

public enum class ReportType { PLAYER, HELPOP }
public enum class ReportStatus { OPEN, CLAIMED, CLOSED }

public data class CreateReport(
    val type: ReportType,
    val reporterId: UUID,
    val reporterName: String,
    val targetId: UUID?,
    val targetName: String?,
    val reason: String,
    val server: String?,
)

public data class Report(
    val id: ReportId,
    val type: ReportType,
    val reporterId: UUID,
    val reporterName: String,
    val targetId: UUID?,
    val targetName: String?,
    val reason: String,
    val createdAt: Instant,
    val server: String?,
    val status: ReportStatus,
    val assignedStaffId: UUID? = null,
    val assignedStaffName: String? = null,
    val resolution: String? = null,
    val closedAt: Instant? = null,
)

public enum class PunishmentType { BAN, IP_BAN, KICK, MUTE, WARNING }
public enum class PunishmentScope { NETWORK, SERVER }

public data class CreatePunishment(
    val type: PunishmentType,
    val targetId: UUID,
    val targetName: String,
    val actorId: UUID?,
    val actorName: String,
    val reason: String,
    val expiresAt: Instant?,
    val scope: PunishmentScope = PunishmentScope.NETWORK,
    val server: String? = null,
    /** Implementations must hash this before persistence. */
    val address: InetAddress? = null,
)

public data class Punishment(
    val id: PunishmentId,
    val type: PunishmentType,
    val targetId: UUID,
    val targetName: String,
    val actorId: UUID?,
    val actorName: String,
    val reason: String,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val active: Boolean,
    val scope: PunishmentScope,
    val server: String?,
) {
    public fun isEffective(at: Instant): Boolean = active && (expiresAt == null || expiresAt.isAfter(at))
}
