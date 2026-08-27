// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.config

import de.samthedev.veloutils.common.DurationParser
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Files
import java.nio.file.Path
import java.time.DayOfWeek
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID
import java.net.URI
import de.samthedev.veloutils.common.ServerAccessRule

public class ConfigRepository(private val dataDirectory: Path) {
    public companion object {
        public const val CURRENT_VERSION: Int = 1
        public val FILES: List<String> = listOf(
            "config.yml",
            "messages.yml",
            "commands.yml",
            "maintenance.yml",
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
        FILES.forEach(::installAndMergeDefaults)
        val documents = FILES.associateWith { fileName -> loader(fileName).load() }
        documents.forEach { (fileName, document) -> migrate(document, fileName) }
        val parsed = parse(
            checkNotNull(documents["config.yml"]),
            checkNotNull(documents["storage.yml"]),
            checkNotNull(documents["moderation.yml"]),
            checkNotNull(documents["integrations.yml"]),
            checkNotNull(documents["alerts.yml"]),
        )
        validate(parsed)
        current.set(parsed)
        return parsed
    }

    private fun installAndMergeDefaults(fileName: String) {
        val target = dataDirectory.resolve(fileName)
        val resource = checkNotNull(javaClass.classLoader.getResourceAsStream(fileName)) { "Missing resource $fileName" }
        resource.use { input ->
            if (Files.notExists(target)) {
                Files.copy(input, target)
                return
            }
        }
        val defaults = javaClass.classLoader.getResourceAsStream(fileName).use { input ->
            checkNotNull(input)
            YamlConfigurationLoader.builder().source { input.bufferedReader() }.build().load()
        }
        val diskLoader = loader(fileName)
        val disk = diskLoader.load()
        mergeMissing(disk, defaults)
        diskLoader.save(disk)
    }

    private fun mergeMissing(target: ConfigurationNode, defaults: ConfigurationNode) {
        if (target.virtual() && !defaults.virtual()) {
            target.from(defaults)
            return
        }
        defaults.childrenMap().forEach { (key, defaultChild) -> mergeMissing(target.node(key), defaultChild) }
    }

    private fun migrate(node: ConfigurationNode, fileName: String) {
        val version = node.node("config-version").int
        if (version > CURRENT_VERSION) throw ConfigValidationException(listOf("$fileName uses unsupported config-version $version"))
        if (version < 1) {
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
            configVersion = config.node("config-version").getInt(CURRENT_VERSION),
            debug = config.node("debug").getBoolean(false),
            modules = ModuleConfig(
                maintenance = module("maintenance"), reports = module("reports"), staff = module("staff"), staffChat = module("staff-chat"),
                moderation = module("moderation"), motd = module("motd"), serverAccess = module("server-access"),
                networkCommands = module("network-commands"), discord = module("discord", false),
                alerts = module("alerts"), tebex = module("tebex", false),
            ),
            protocol = ProtocolConfig(
                requireAuthentication = config.node("protocol", "authentication", "required").getBoolean(true),
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
                storeIpHashes = moderation.node("store-ip-hashes").getBoolean(true),
                ipHashKey = moderation.node("ip-hash-key").getString()?.trim()?.takeIf(String::isNotEmpty),
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
            staffWeekStart = runCatching { DayOfWeek.valueOf(config.node("staff", "week-start").getString("MONDAY").uppercase()) }
                .getOrElse { throw ConfigValidationException(listOf("config.yml: staff.week-start is invalid")) },
            legacyPermissionAliases = config.node("compatibility", "legacy-permission-aliases").getBoolean(false),
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
            if (config.modules.moderation && config.moderation.storeIpHashes &&
                (config.moderation.ipHashKey?.toByteArray()?.size ?: 0) < 32
            ) {
                add("moderation.yml: ip-hash-key must contain at least 32 UTF-8 bytes when moderation IP hashing is enabled")
            }
            if (config.modules.motd && config.motd.entries.isEmpty()) add("motd.entries must not be empty")
            if (config.motd.maximumPlayers !in 1..1_000_000) add("motd.maximum-players must be between 1 and 1000000")
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
            if (config.updates.provider != "modrinth") add("update-checker.provider must be modrinth")
            if (!Regex("[A-Za-z0-9_-]{1,64}").matches(config.updates.projectId)) add("update-checker.project-id is invalid")
            if (config.updates.checkInterval < java.time.Duration.ofMinutes(15)) add("update-checker.check-interval must be at least 15m")
            if (config.modules.alerts && config.alerts.enabled && config.alerts.messages.isEmpty()) add("alerts.yml: messages must not be empty")
            if (config.alerts.interval < java.time.Duration.ofSeconds(30)) add("alerts.yml: interval must be at least 30s")
        }
        if (problems.isNotEmpty()) throw ConfigValidationException(problems)
    }

    private fun loader(fileName: String): YamlConfigurationLoader =
        YamlConfigurationLoader.builder().path(dataDirectory.resolve(fileName)).build()
}

private val ConfigurationNode.string: String?
    get() = getString()
