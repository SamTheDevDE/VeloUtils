// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.command

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import de.samthedev.veloutils.common.Permissions
import de.samthedev.veloutils.proxy.config.ConfigRepository
import de.samthedev.veloutils.proxy.network.VelocityNetworkService
import de.samthedev.veloutils.proxy.permission.PermissionService
import de.samthedev.veloutils.proxy.ui.ChatUi
import de.samthedev.veloutils.proxy.util.ConfiguredMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI

public class RootCommand(
    private val version: String,
    private val config: ConfigRepository,
    private val messages: ConfiguredMessages,
    private val network: VelocityNetworkService,
    private val permissions: PermissionService,
    private val scope: CoroutineScope,
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        when (invocation.arguments().firstOrNull()?.lowercase()) {
            null, "help" -> dashboard(source)
            "version" -> ifAllowed(source, Permissions.ADMIN_VERSION) {
                source.sendMessage(ChatUi.header("Version"))
                source.sendMessage(ChatUi.field("VeloUtils", version))
                source.sendMessage(ChatUi.field("Author", "SamTheDevDE"))
            }
            "status" -> ifAllowed(source, Permissions.ADMIN_STATUS) { showStatus(source) }
            "debug" -> ifAllowed(source, Permissions.ADMIN_DEBUG) { showDebug(source) }
            "reload" -> ifAllowed(source, Permissions.ADMIN_RELOAD) { reload(source) }
            "config" -> ifAllowed(source, Permissions.ADMIN_CONFIG) { configCommand(source, invocation.arguments().drop(1)) }
            else -> {
                source.sendMessage(ChatUi.error("Unknown VeloUtils subcommand '${invocation.arguments()[0]}'."))
                dashboard(source)
            }
        }
    }

    private fun dashboard(source: CommandSource) {
        source.sendMessage(ChatUi.header("Administration"))
        source.sendMessage(ChatUi.field("Version", version))
        source.sendMessage(ChatUi.field("Proxy servers", network.snapshot().servers.size.toString()))
        val controls = buildList {
            if (permissions.has(source, Permissions.ADMIN_STATUS)) add(ChatUi.button(source, "Status", "/veloutils status", "View network and bridge status"))
            if (permissions.has(source, Permissions.ADMIN_RELOAD)) add(ChatUi.button(source, "Reload", "/veloutils reload", "Reload messages and validate configuration"))
            if (permissions.has(source, Permissions.ADMIN_DEBUG)) add(ChatUi.button(source, "Debug", "/veloutils debug", "View redacted diagnostics"))
            if (permissions.has(source, Permissions.ADMIN_CONFIG)) add(ChatUi.button(source, "Config", "/veloutils config validate", "Validate configuration files"))
            add(ChatUi.link(source, "Documentation", URI("https://github.com/SamTheDevDE/VeloUtils"), "Open the VeloUtils documentation"))
        }
        if (controls.isEmpty()) source.sendMessage(ChatUi.info("No administrative controls are available to you."))
        else source.sendMessage(ChatUi.join(*controls.toTypedArray()))
    }

    private fun showStatus(source: CommandSource) {
        val snapshot = network.snapshot()
        source.sendMessage(ChatUi.header("Network status"))
        source.sendMessage(ChatUi.field("Players", snapshot.playerCount.toString()))
        source.sendMessage(ChatUi.field("Servers", snapshot.servers.size.toString()))
        snapshot.servers.take(10).forEach { server ->
            val bridge = server.bridge
            val bridgeText = bridge?.let { "Bridge ${it.pluginVersion} • protocol ${it.protocolVersion}" } ?: "Bridge unavailable"
            source.sendMessage(ChatUi.field(server.name, "${server.playerCount} players • $bridgeText"))
        }
        if (snapshot.servers.size > 10) {
            source.sendMessage(ChatUi.info("${snapshot.servers.size - 10} additional servers are hidden from this compact status view."))
            if (permissions.has(source, Permissions.NETWORK_STATUS)) {
                source.sendMessage(ChatUi.button(source, "Network pages", "/network", "Open the paginated network overview"))
            }
        }
    }

    private fun showDebug(source: CommandSource) {
        val snapshot = config.snapshot()
        source.sendMessage(ChatUi.header("Redacted diagnostics"))
        source.sendMessage(ChatUi.field("Storage", snapshot.storage.type.name))
        val enabledModules = listOf(
            "maintenance" to snapshot.modules.maintenance,
            "reports" to snapshot.modules.reports,
            "staff" to snapshot.modules.staff,
            "staff-chat" to snapshot.modules.staffChat,
            "moderation" to snapshot.modules.moderation,
            "motd" to snapshot.modules.motd,
            "server-access" to snapshot.modules.serverAccess,
            "network-commands" to snapshot.modules.networkCommands,
            "discord" to snapshot.modules.discord,
            "alerts" to snapshot.modules.alerts,
        ).filter(Pair<String, Boolean>::second).joinToString { it.first }
        source.sendMessage(ChatUi.field("Enabled modules", enabledModules.ifEmpty { "None" }))
        source.sendMessage(ChatUi.field("Protocol authentication", snapshot.protocol.requireAuthentication.toString()))
        source.sendMessage(ChatUi.field("Shared secret", "<redacted>"))
        source.sendMessage(ChatUi.field("Registered servers", network.snapshot().servers.size.toString()))
        source.sendMessage(ChatUi.info("Diagnostic output intentionally excludes credentials, addresses, and webhook URLs."))
    }

    private fun reload(source: CommandSource) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                config.validateFiles()
                messages.reload()
            }.onSuccess {
                source.sendMessage(ChatUi.success("Reloaded messages.yml and validated every configuration file."))
                source.sendMessage(ChatUi.warning("Module, protocol, storage, integration, command registration, MOTD, and scheduler changes require a proxy restart."))
            }.onFailure { failure ->
                source.sendMessage(ChatUi.error("Reload failed: ${failure.message ?: "unknown validation error"}. The active runtime state was not changed."))
            }
        }
    }

    private fun configCommand(source: CommandSource, arguments: List<String>) {
        when (arguments.firstOrNull()?.lowercase()) {
            "validate" -> scope.launch(Dispatchers.IO) {
                runCatching { config.validateFiles(); messages.validate() }
                    .onSuccess { source.sendMessage(ChatUi.success("All configuration files and MiniMessage templates are valid.")) }
                    .onFailure { source.sendMessage(ChatUi.error("Configuration validation failed: ${it.message ?: "unknown error"}.")) }
            }
            "diff" -> scope.launch(Dispatchers.IO) {
                runCatching { config.missingDefaults() }.onSuccess { missing ->
                    source.sendMessage(ChatUi.header("Configuration defaults"))
                    if (missing.isEmpty()) source.sendMessage(ChatUi.success("No bundled settings are missing."))
                    missing.forEach { (file, paths) ->
                        source.sendMessage(ChatUi.warning("$file is missing ${paths.size} optional setting(s): ${paths.joinToString()}. Fixed settings use bundled defaults where applicable."))
                    }
                    source.sendMessage(ChatUi.info("VeloUtils does not rewrite existing YAML during normal startup."))
                }.onFailure { source.sendMessage(ChatUi.error("Configuration comparison failed: ${it.message}.")) }
            }
            else -> ChatUi.usage(source, "/veloutils config <validate|diff>", "Validates files or lists defaults that are currently omitted.")
                .forEach(source::sendMessage)
        }
    }

    private inline fun ifAllowed(source: CommandSource, permission: de.samthedev.veloutils.common.PermissionDefinition, action: () -> Unit) {
        if (permissions.has(source, permission)) action() else source.sendMessage(ChatUi.error("You do not have permission to use that administrative action."))
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        val source = invocation.source()
        val arguments = invocation.arguments()
        if (arguments.size <= 1) {
            val input = arguments.firstOrNull().orEmpty()
            return buildList {
                if (permissions.has(source, Permissions.ADMIN_STATUS)) add("status")
                if (permissions.has(source, Permissions.ADMIN_RELOAD)) add("reload")
                if (permissions.has(source, Permissions.ADMIN_VERSION)) add("version")
                if (permissions.has(source, Permissions.ADMIN_DEBUG)) add("debug")
                if (permissions.has(source, Permissions.ADMIN_CONFIG)) add("config")
            }.filter { it.startsWith(input, true) }
        }
        if (arguments.firstOrNull()?.equals("config", true) == true && arguments.size == 2 && permissions.has(source, Permissions.ADMIN_CONFIG)) {
            return listOf("validate", "diff").filter { it.startsWith(arguments[1], true) }
        }
        return emptyList()
    }
}
