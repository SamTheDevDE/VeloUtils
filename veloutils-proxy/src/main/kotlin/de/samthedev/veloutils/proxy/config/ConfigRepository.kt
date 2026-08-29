// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.config

import de.samthedev.veloutils.common.DurationParser
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID
import java.net.URI
import de.samthedev.veloutils.common.ServerAccessRule
import de.samthedev.veloutils.proxy.util.ConfiguredMiniMessage
import de.samthedev.veloutils.proxy.command.ConfiguredCommandLoader

public class ConfigRepository(private val dataDirectory: Path) {
    public companion object {
        public const val CURRENT_VERSION: Int = 1
        public val FILES: List<String> = listOf(
            "config.yml",
            "messages.yml",
            "commands.yml",
            "moderation.yml",
            "integrations.yml",
            "alerts.yml",
            "storage.yml",
        )
    }

    private val current = AtomicReference<ProxyConfig>()

    public fun snapshot(): ProxyConfig = checkNotNull(current.get()) { "Configuration has not been loaded" }

    public fun load(): ProxyConfig {
        Files.createDirectories(dataDirectory)
        FILES.forEach(::installDefault)
        val parsed = readDocuments(migrate = true)
        current.set(parsed)
        return parsed
    }

    public fun validateFiles(): ProxyConfig = readDocuments(migrate = false)

    public fun missingDefaults(): Map<String, List<String>> = FILES.mapNotNull { fileName ->
        val disk = loader(fileName).load()
        val defaults = checkNotNull(javaClass.classLoader.getResourceAsStream(fileName)).use { input ->
            YamlConfigurationLoader.builder().source { input.bufferedReader() }.build().load()
        }
        val missing = mutableListOf<String>()
        fun visit(path: List<Any>, node: ConfigurationNode) {
            if (node.isList) {
                if (disk.node(*path.toTypedArray()).virtual()) missing += path.joinToString(".")
                return
            }
            node.childrenMap().forEach { (key, child) ->
                val childPath = path + key
                if (child.childrenMap().isEmpty()) {
                    if (disk.node(*childPath.toTypedArray()).virtual()) missing += childPath.joinToString(".")
                } else visit(childPath, child)
            }
        }
        visit(emptyList(), defaults)
        missing.takeIf(List<String>::isNotEmpty)?.let { fileName to it }
    }.toMap()

    private fun readDocuments(migrate: Boolean): ProxyConfig {
        val documents = FILES.associateWith { fileName ->
            loader(fileName).load().also { document ->
                if (migrate) migrate(document, fileName)
                document.mergeFrom(loadBundled(fileName))
            }
        }
        val parsed = parse(
            checkNotNull(documents["config.yml"]),
            checkNotNull(documents["storage.yml"]),
            checkNotNull(documents["moderation.yml"]),
            checkNotNull(documents["integrations.yml"]),
            checkNotNull(documents["alerts.yml"]),
        )
        validate(parsed)
        ConfiguredCommandLoader.load(dataDirectory.resolve("commands.yml"))
        return parsed
    }

    private fun installDefault(fileName: String) {
        val target = dataDirectory.resolve(fileName)
        if (Files.exists(target)) return
        checkNotNull(javaClass.classLoader.getResourceAsStream(fileName)) { "Missing resource $fileName" }.use { input ->
            Files.copy(input, target)
        }
    }

    private fun migrate(node: ConfigurationNode, fileName: String) {
        val version = node.node("config-version").int
        if (version > CURRENT_VERSION) throw ConfigValidationException(listOf("$fileName uses unsupported config-version $version"))
        if (version < 1) {
            val source = dataDirectory.resolve(fileName)
            Files.copy(source, source.resolveSibling("$fileName.pre-migration.bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            node.node("config-version").set(CURRENT_VERSION)
            loader(fileName).save(node)
        }
    }

    private fun parse(
        config: ConfigurationNode,
        storage: ConfigurationNode,
        moderation: ConfigurationNode,
        integrations: ConfigurationNode,
        alerts: ConfigurationNode,
    ): ProxyConfig {
        fun module(name: String, fallback: Boolean = true): Boolean = config.node("modules", name).getBoolean(fallback)
        val storageType = runCatching { StorageType.valueOf(storage.node("type").getString("sqlite").uppercase()) }
            .getOrElse { throw ConfigValidationException(listOf("storage.yml: type must be sqlite, mysql, or postgresql")) }
        return ProxyConfig(
            modules = ModuleConfig(
                maintenance = module("maintenance"), reports = module("reports"), staff = module("staff"),
                staffChat = module("staff-chat"), chat = module("chat", false), messaging = module("messaging", false),
                moderation = module("moderation", false), motd = module("motd"), serverAccess = module("server-access"),
                networkCommands = module("network-commands"), discord = module("discord", false),
                alerts = module("alerts"),
            ),
            protocol = ProtocolConfig(
                requireAuthentication = config.node("protocol", "authentication", "required").getBoolean(false),
                sharedSecret = config.node("protocol", "authentication", "shared-secret").string?.trim()?.takeIf(String::isNotEmpty),
                requestTimeout = DurationParser.parse(config.node("protocol", "request-timeout").getString("5s")),
                maximumPayloadBytes = config.node("protocol", "maximum-payload-bytes").getInt(32 * 1_024),
                remoteCommandsEnabled = config.node("protocol", "remote-commands", "enabled").getBoolean(false),
                commandAllowlist = config.node("protocol", "remote-commands", "allowlist").getList(String::class.java, emptyList()).toSet(),
            ),
            storage = StorageConfig(
                type = storageType,
                host = storage.node("host").getString("127.0.0.1"), port = storage.node("port").getInt(3306),
                database = storage.node("database").getString("veloutils"), username = storage.node("username").getString("veloutils"),
                password = storage.node("password").getString(""), poolSize = storage.node("pool-size").getInt(10),
            ),
            moderation = ModerationConfig(
                ipHashKey = moderation.node("ip-hash-key").getString()?.trim()?.takeIf(String::isNotEmpty),
                selfPunishmentConfirmation = DurationParser.parse(
                    moderation.node("self-punishment-confirmation").getString("30s"),
                ),
            ),
            motd = MotdConfig(
                entries = config.node("motd", "entries").getList(String::class.java, listOf("<gradient:#7c3aed:#a855f7>VeloUtils</gradient>")),
                maintenanceEntries = config.node("motd", "maintenance-entries").getList(String::class.java, listOf("<red>Maintenance</red>")),
                maximumPlayers = config.node("motd", "maximum-players").getInt(1_000),
                samplePlayers = config.node("motd", "sample-players").getList(String::class.java, emptyList()),
                favicon = config.node("motd", "favicon").getString()?.trim()?.takeIf(String::isNotEmpty),
                virtualHosts = config.node("motd", "virtual-hosts").childrenMap().mapValues { (_, node) ->
                    node.node("entries").getList(String::class.java, emptyList())
                }.mapKeys { it.key.toString().lowercase() },
            ),
            playerFormatting = PlayerFormattingConfig(
                enabled = config.node("chat", "player-formatting", "enabled").getBoolean(true),
                defaults = PlayerFormattingDefaults(
                    colors = config.node("chat", "player-formatting", "default", "colors").getBoolean(false),
                    decorations = config.node("chat", "player-formatting", "default", "decorations").getBoolean(false),
                    gradients = config.node("chat", "player-formatting", "default", "gradients").getBoolean(false),
                ),
                permissions = PlayerFormattingPermissions(
                    colors = config.node("chat", "player-formatting", "permissions", "colors")
                        .getString("veloutils.chat.format.colors"),
                    decorations = config.node("chat", "player-formatting", "permissions", "decorations")
                        .getString("veloutils.chat.format.decorations"),
                    gradients = config.node("chat", "player-formatting", "permissions", "gradients")
                        .getString("veloutils.chat.format.gradients"),
                    full = config.node("chat", "player-formatting", "permissions", "full")
                        .getString("veloutils.chat.format.full"),
                ),
            ),
            discord = DiscordConfig(
                connectTimeout = DurationParser.parse(integrations.node("discord", "connect-timeout").getString("5s")),
                requestTimeout = DurationParser.parse(integrations.node("discord", "request-timeout").getString("10s")),
                maximumRetries = integrations.node("discord", "maximum-retries").getInt(2),
                webhooks = integrations.node("discord", "webhooks").childrenMap().mapValues { (_, node) ->
                    node.getString("").trim()
                }.mapKeys { it.key.toString().lowercase() },
            ),
            limbo = LimboConfig(
                enabled = integrations.node("limbo", "enabled").getBoolean(false),
                server = integrations.node("limbo", "server").getString("limbo").lowercase(),
            ),
            tab = TabIntegrationConfig(
                enabled = integrations.node("tab", "enabled").getBoolean(true),
                placeholdersEnabled = integrations.node("tab", "placeholders", "enabled").getBoolean(true),
            ),
            serverMetadata = config.node("servers").childrenMap().map { (key, node) ->
                key.toString().lowercase() to ServerMetadata(
                    displayName = node.node("display-name").getString()?.trim()?.takeIf(String::isNotEmpty),
                    maximumPlayers = node.node("max-players").getInt(0),
                )
            }.toMap(),
            updates = UpdateConfig(
                enabled = config.node("update-checker", "enabled").getBoolean(true),
                provider = config.node("update-checker", "provider").getString("modrinth").lowercase(),
                projectId = config.node("update-checker", "project-id").getString("veloutils"),
                checkInterval = DurationParser.parse(config.node("update-checker", "check-interval").getString("6h")),
            ),
            alerts = AlertConfig(
                enabled = alerts.node("enabled").getBoolean(true),
                initialDelay = DurationParser.parse(alerts.node("initial-delay").getString("2m")),
                interval = DurationParser.parse(alerts.node("interval").getString("10m")),
                randomOrder = alerts.node("random-order").getBoolean(false),
                messages = alerts.node("messages").getList(String::class.java, emptyList()),
            ),
            ui = UiConfig(config.node("ui", "page-size").getInt(5)),
            legacyPermissions = LegacyPermissionConfig(
                enabled = config.node("compatibility", "legacy-permissions", "enabled").getBoolean(
                    config.node("compatibility", "legacy-permission-aliases").getBoolean(true),
                ),
                warn = config.node("compatibility", "legacy-permissions", "warn").getBoolean(true),
            ),
            maintenanceTransfer = MaintenanceTransferConfig(
                enabled = config.node("maintenance", "pre-activation-transfer", "enabled").getBoolean(false),
                before = DurationParser.parse(config.node("maintenance", "pre-activation-transfer", "before").getString("30s")),
                destinations = config.node("maintenance", "pre-activation-transfer", "destinations")
                    .getList(String::class.java, emptyList()).map(String::lowercase),
            ),
            serverAccessRules = config.node("server-access", "servers").childrenMap().map { (key, node) ->
                key.toString().lowercase() to ServerAccessRule(
                    permission = node.node("permission").getString()?.trim()?.takeIf(String::isNotEmpty),
                    allowlist = node.node("allowlist").getList(String::class.java, emptyList()).mapTo(mutableSetOf()) { value ->
                        runCatching { UUID.fromString(value) }.getOrElse {
                            throw ConfigValidationException(listOf("config.yml: server-access server $key contains invalid UUID '$value'"))
                        }
                    },
                    fallbackServers = node.node("fallback").getList(String::class.java, emptyList()),
                )
            }.toMap(),
        )
    }

    private fun validate(config: ProxyConfig) {
        val problems = buildList {
            if (config.protocol.maximumPayloadBytes !in 1_024..1_048_576) add("protocol.maximum-payload-bytes must be between 1024 and 1048576")
            if (config.protocol.requireAuthentication && (config.protocol.sharedSecret?.toByteArray()?.size ?: 0) < 32) {
                add("protocol.authentication.shared-secret must contain at least 32 UTF-8 bytes when authentication is required")
            }
            if (config.protocol.remoteCommandsEnabled && !config.protocol.requireAuthentication) {
                add("protocol.remote-commands requires protocol authentication")
            }
            if (config.protocol.remoteCommandsEnabled && config.protocol.commandAllowlist.isEmpty()) {
                add("protocol.remote-commands.allowlist must not be empty when remote commands are enabled")
            }
            if (config.storage.poolSize !in 1..64) add("storage pool-size must be between 1 and 64")
            if (config.storage.type != StorageType.SQLITE && config.storage.port !in 1..65_535) add("storage port is invalid")
            if (config.modules.moderation && (config.moderation.ipHashKey?.toByteArray()?.size ?: 0) < 32) {
                add("moderation.yml: ip-hash-key must contain at least 32 UTF-8 bytes when moderation is enabled")
            }
            if (config.modules.motd && config.motd.entries.isEmpty()) add("motd.entries must not be empty")
            if (config.motd.maximumPlayers !in 1..1_000_000) add("motd.maximum-players must be between 1 and 1000000")
            validateMiniMessageList("config.yml: motd.entries", config.motd.entries, this)
            validateMiniMessageList("config.yml: motd.maintenance-entries", config.motd.maintenanceEntries, this)
            validateMiniMessageList("config.yml: motd.sample-players", config.motd.samplePlayers, this)
            config.motd.virtualHosts.forEach { (host, entries) ->
                validateMiniMessageList("config.yml: motd.virtual-hosts.$host.entries", entries, this)
            }
            validateMiniMessageList("alerts.yml: messages", config.alerts.messages, this)
            val permissionPattern = Regex("[a-z0-9][a-z0-9._-]{0,127}")
            with(config.playerFormatting.permissions) {
                mapOf("colors" to colors, "decorations" to decorations, "gradients" to gradients, "full" to full)
                    .filterValues { !permissionPattern.matches(it) }
                    .forEach { (name, _) -> add("config.yml: chat.player-formatting.permissions.$name is invalid") }
            }
            if (config.ui.pageSize !in 3..20) add("config.yml: ui.page-size must be between 3 and 20")
            if (config.moderation.selfPunishmentConfirmation !in java.time.Duration.ofSeconds(10)..java.time.Duration.ofMinutes(2)) {
                add("moderation.yml: self-punishment-confirmation must be between 10s and 2m")
            }
            if (config.discord.maximumRetries !in 0..5) add("integrations.yml: discord.maximum-retries must be between 0 and 5")
            config.discord.webhooks.filterValues(String::isNotEmpty).forEach { (name, value) ->
                val uri = runCatching { URI(value) }.getOrNull()
                if (uri?.scheme != "https" || uri.host?.lowercase() !in setOf("discord.com", "discordapp.com") ||
                    !uri.path.startsWith("/api/webhooks/")
                ) add("integrations.yml: discord webhook '$name' must be an official HTTPS Discord webhook URL")
            }
            if (config.limbo.enabled && !Regex("[a-z0-9_-]{1,64}").matches(config.limbo.server)) {
                add("integrations.yml: limbo.server is invalid")
            }
            config.serverMetadata.forEach { (server, metadata) ->
                if (!Regex("[a-z0-9._-]{1,64}").matches(server)) {
                    add("config.yml: servers key '$server' is not a valid Velocity server name")
                }
                if (metadata.maximumPlayers !in 0..1_000_000) {
                    add("config.yml: servers.$server.max-players must be between 0 and 1000000")
                }
                metadata.displayName?.let { displayName ->
                    runCatching { ConfiguredMiniMessage.deserialize(displayName) }.exceptionOrNull()?.let { failure ->
                        add("config.yml: servers.$server.display-name is invalid MiniMessage: ${failure.message}")
                    }
                }
            }
            if (config.updates.provider != "modrinth") add("update-checker.provider must be modrinth")
            if (!Regex("[A-Za-z0-9_-]{1,64}").matches(config.updates.projectId)) add("update-checker.project-id is invalid")
            if (config.updates.checkInterval < java.time.Duration.ofMinutes(15)) add("update-checker.check-interval must be at least 15m")
            if (config.modules.alerts && config.alerts.enabled && config.alerts.messages.isEmpty()) add("alerts.yml: messages must not be empty")
            if (config.alerts.interval < java.time.Duration.ofSeconds(30)) add("alerts.yml: interval must be at least 30s")
            if (config.maintenanceTransfer.enabled && config.maintenanceTransfer.destinations.isEmpty()) {
                add("maintenance.pre-activation-transfer.destinations must not be empty when enabled")
            }
            if (config.maintenanceTransfer.before !in java.time.Duration.ofSeconds(1)..java.time.Duration.ofMinutes(10)) {
                add("maintenance.pre-activation-transfer.before must be between 1s and 10m")
            }
            config.maintenanceTransfer.destinations.forEach { destination ->
                if (!Regex("[a-z0-9_-]{1,64}").matches(destination)) {
                    add("maintenance.pre-activation-transfer destination '$destination' is invalid")
                }
            }
        }
        if (problems.isNotEmpty()) throw ConfigValidationException(problems)
    }

    private fun loader(fileName: String): YamlConfigurationLoader =
        YamlConfigurationLoader.builder().path(dataDirectory.resolve(fileName)).build()

    private fun loadBundled(fileName: String): ConfigurationNode =
        checkNotNull(javaClass.classLoader.getResourceAsStream(fileName)) { "Missing resource $fileName" }.use { input ->
            YamlConfigurationLoader.builder().source { input.bufferedReader() }.build().load()
        }.also { defaults ->
            // Keyed administrator collections are definitions, not scalar defaults. Injecting bundled examples here
            // could unexpectedly create access rules, hosts, webhooks, or commands on an upgraded installation.
            when (fileName) {
                "config.yml" -> {
                    defaults.node("server-access", "servers").raw(null)
                    defaults.node("motd", "virtual-hosts").raw(null)
                }
                "integrations.yml" -> defaults.node("discord", "webhooks").raw(null)
                "commands.yml" -> {
                    defaults.node("move-commands").raw(null)
                    defaults.node("message-commands").raw(null)
                }
            }
        }

    private fun validateMiniMessageList(
        path: String,
        values: List<String>,
        problems: MutableList<String>,
    ) {
        values.forEachIndexed { index, value ->
            runCatching { ConfiguredMiniMessage.deserialize(value) }.exceptionOrNull()?.let { failure ->
                problems += "$path[$index]: invalid MiniMessage: ${failure.message}"
            }
        }
    }
}

private val ConfigurationNode.string: String?
    get() = getString()
