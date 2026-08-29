// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.config

import java.time.Duration
import de.samthedev.veloutils.common.ServerAccessRule

public data class ModuleConfig(
    val maintenance: Boolean,
    val reports: Boolean,
    val staff: Boolean,
    val staffChat: Boolean,
    val chat: Boolean,
    val messaging: Boolean,
    val moderation: Boolean,
    val motd: Boolean,
    val serverAccess: Boolean,
    val networkCommands: Boolean,
    val discord: Boolean,
    val alerts: Boolean,
)

public data class ProtocolConfig(
    val requireAuthentication: Boolean,
    val sharedSecret: String?,
    val requestTimeout: Duration,
    val maximumPayloadBytes: Int,
    val remoteCommandsEnabled: Boolean,
    val commandAllowlist: Set<String>,
)

public data class StorageConfig(
    val type: StorageType,
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    val poolSize: Int,
)

public enum class StorageType { SQLITE, MYSQL, POSTGRESQL }

public data class ModerationConfig(
    val ipHashKey: String?,
    val selfPunishmentConfirmation: Duration,
)

public data class UiConfig(val pageSize: Int)

public data class LegacyPermissionConfig(val enabled: Boolean, val warn: Boolean)

public data class MaintenanceTransferConfig(
    val enabled: Boolean,
    val before: Duration,
    val destinations: List<String>,
)

public data class MotdConfig(
    val entries: List<String>,
    val maintenanceEntries: List<String>,
    val maximumPlayers: Int,
    val samplePlayers: List<String>,
    val favicon: String?,
    val virtualHosts: Map<String, List<String>>,
)

public data class PlayerFormattingPermissions(
    val colors: String,
    val decorations: String,
    val gradients: String,
    val full: String,
)

public data class PlayerFormattingDefaults(
    val colors: Boolean,
    val decorations: Boolean,
    val gradients: Boolean,
)

public data class PlayerFormattingConfig(
    val enabled: Boolean,
    val defaults: PlayerFormattingDefaults,
    val permissions: PlayerFormattingPermissions,
)

public data class DiscordConfig(
    val connectTimeout: Duration,
    val requestTimeout: Duration,
    val maximumRetries: Int,
    val webhooks: Map<String, String>,
)

public data class LimboConfig(
    val enabled: Boolean,
    val server: String,
)

public data class TabIntegrationConfig(
    val enabled: Boolean,
    val placeholdersEnabled: Boolean,
)

public data class ServerMetadata(
    val displayName: String?,
    val maximumPlayers: Int,
)

public data class UpdateConfig(
    val enabled: Boolean,
    val provider: String,
    val projectId: String,
    val checkInterval: Duration,
)

public data class AlertConfig(
    val enabled: Boolean,
    val initialDelay: Duration,
    val interval: Duration,
    val randomOrder: Boolean,
    val messages: List<String>,
)

public data class ProxyConfig(
    val modules: ModuleConfig,
    val protocol: ProtocolConfig,
    val storage: StorageConfig,
    val moderation: ModerationConfig,
    val motd: MotdConfig,
    val playerFormatting: PlayerFormattingConfig,
    val discord: DiscordConfig,
    val limbo: LimboConfig,
    val tab: TabIntegrationConfig,
    val serverMetadata: Map<String, ServerMetadata>,
    val updates: UpdateConfig,
    val alerts: AlertConfig,
    val ui: UiConfig,
    val legacyPermissions: LegacyPermissionConfig,
    val maintenanceTransfer: MaintenanceTransferConfig,
    val serverAccessRules: Map<String, ServerAccessRule>,
)

public class ConfigValidationException(public val problems: List<String>) :
    IllegalArgumentException(problems.joinToString(prefix = "Invalid VeloUtils configuration: ", separator = "; "))
