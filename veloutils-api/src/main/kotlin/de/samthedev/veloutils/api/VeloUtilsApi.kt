// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.api

import java.net.InetAddress
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Stable entry point exposed by the Velocity plugin instance and Paper/Folia services manager. */
public interface VeloUtilsApi {
    /** Use this before invoking an optional feature service. */
    public val modules: ModuleService
    public val network: NetworkService?
    public val maintenance: MaintenanceService?
    public val staff: StaffService?
    public val reports: ReportService?
    public val moderation: ModerationService?
    public val afk: AfkService? get() = null
    public val chat: ChatService? get() = null
    public val presentation: PresentationService? get() = null
    public val messaging: MessagingService? get() = null
    /** Present once a platform has activated shared placeholder rendering. */
    public val placeholders: PlaceholderService? get() = null
}

public interface ModuleService {
    public fun state(id: String): ModuleAvailability
    public fun active(): Set<String>
}

public enum class ModuleAvailability { ENABLED, DISABLED, UNAVAILABLE }

public data class PlaceholderContext(
    val playerId: UUID? = null,
    val server: String? = null,
    val world: String? = null,
)

public fun interface PlaceholderProvider {
    /** Return plain-text values. MiniMessage markup supplied here is treated as literal text. */
    public fun resolve(context: PlaceholderContext): Map<String, String>
}

public interface PlaceholderService {
    public fun resolve(context: PlaceholderContext): Map<String, String>
    public fun register(namespace: String, provider: PlaceholderProvider): AutoCloseable
}

public interface AfkService {
    public fun snapshot(playerId: UUID): AfkStatus?
    public suspend fun setAfk(playerId: UUID, afk: Boolean): AfkStatus?
}

public data class AfkStatus(
    val playerId: UUID,
    val afk: Boolean,
    val since: Instant?,
    val lastActivity: Instant,
)

public interface PresentationService {
    public fun snapshot(): PresentationSnapshot
    public fun refresh(playerId: UUID)
    public fun showBossBar(request: TemporaryBossBarRequest): AutoCloseable
}

public enum class PresentationBossBarColor { PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE }
public enum class PresentationBossBarOverlay { PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12, NOTCHED_20 }

public data class TemporaryBossBarRequest(
    val id: String,
    val text: String,
    val startsAt: Instant = Instant.now(),
    val endsAt: Instant,
    val playerIds: Set<UUID> = emptySet(),
    val priority: Int = 0,
    val progress: Float = 1.0f,
    val color: PresentationBossBarColor = PresentationBossBarColor.PURPLE,
    val overlay: PresentationBossBarOverlay = PresentationBossBarOverlay.PROGRESS,
) {
    init {
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "Invalid bossbar id" }
        require(text.isNotBlank()) { "Bossbar text must not be blank" }
        require(endsAt.isAfter(startsAt)) { "Bossbar end must follow its start" }
        require(progress in 0.0f..1.0f) { "Bossbar progress must be between 0 and 1" }
    }
}

public data class PresentationSnapshot(
    val tab: Boolean,
    val bossBars: Boolean,
    val scoreboards: Boolean,
    val nametags: Boolean,
)

public interface MessagingService {
    public fun isIgnoring(playerId: UUID, otherId: UUID): Boolean
    public fun send(senderId: UUID, targetId: UUID, message: String): MessageDelivery
}

public enum class MessageDelivery { SENT, SENDER_OFFLINE, TARGET_OFFLINE, IGNORED, INVALID }

public enum class ChatChannelScope { SERVER, RADIUS, NETWORK }

public data class ChatChannelDefinition(
    val id: String,
    val format: String,
    val scope: ChatChannelScope = ChatChannelScope.SERVER,
    val radius: Double? = null,
    val permission: String? = null,
    val mentions: Boolean = true,
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9_-]{0,31}"))) { "Invalid chat channel id" }
        require(format.isNotBlank()) { "Chat channel format must not be blank" }
        require(scope != ChatChannelScope.RADIUS || radius?.let { it in 1.0..10_000.0 } == true) {
            "Radius channels require a radius between 1 and 10000"
        }
        require(permission == null || permission.isNotBlank()) { "Chat channel permission must not be blank" }
    }
}

public interface ChatService {
    public fun channels(): Set<String>
    public fun activeChannel(playerId: UUID): String
    public fun select(playerId: UUID, channel: String): Boolean
    /** Addon channels are local runtime registrations and must be closed when the addon disables. */
    public fun register(channel: ChatChannelDefinition): AutoCloseable
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
    public data class Enable(
        val server: String?,
        val reason: String,
        val at: Instant = Instant.now(),
        val scheduledEnd: Instant? = null,
    ) : MaintenanceUpdate
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
