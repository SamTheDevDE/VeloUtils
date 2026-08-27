// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge.bootstrap

import de.samthedev.veloutils.bridge.messaging.BridgeProtocolGateway
import de.samthedev.veloutils.bridge.placeholder.NetworkPlaceholderCache
import de.samthedev.veloutils.bridge.placeholder.NetworkPlaceholderExpansion
import de.samthedev.veloutils.bridge.server.PlatformSchedulers
import de.samthedev.veloutils.protocol.ProtocolCodec
import de.samthedev.veloutils.protocol.ProtocolSecurity
import de.samthedev.veloutils.common.RemoteCommandPolicy
import de.samthedev.veloutils.common.Permissions
import de.samthedev.veloutils.common.DurationParser
import de.samthedev.veloutils.common.PermissionDefinition
import de.samthedev.veloutils.bridge.player.MuteEnforcement
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import org.spongepowered.configurate.yaml.YamlConfigurationLoader

public class VeloUtilsBridgePlugin : JavaPlugin(), Listener {
    private lateinit var gateway: BridgeProtocolGateway
    private lateinit var schedulers: PlatformSchedulers
    private val placeholders = NetworkPlaceholderCache()
    private var legacyPermissionsEnabled: Boolean = true
    private var warnOnLegacyPermissions: Boolean = true
    private val warnedLegacyPermissions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override fun onEnable() {
        saveDefaultConfigFile()
        val root = YamlConfigurationLoader.builder().path(dataPath.resolve("config.yml")).build().load()
        if (root.node("config-version").getInt(1) != 1) {
            logger.severe("Unsupported VeloUtils Bridge config-version. Disabling bridge.")
            server.pluginManager.disablePlugin(this)
            return
        }
        val heartbeat = runCatching { DurationParser.parse(root.node("heartbeat").getString("10s")) }.getOrElse {
            logger.severe("Invalid bridge heartbeat duration: ${it.message}. Disabling bridge.")
            server.pluginManager.disablePlugin(this)
            return
        }
        if (heartbeat.seconds !in 5..60) {
            logger.severe("Bridge heartbeat must be between 5s and 60s. Disabling bridge.")
            server.pluginManager.disablePlugin(this)
            return
        }
        legacyPermissionsEnabled = root.node("compatibility", "legacy-permissions", "enabled").getBoolean(true)
        warnOnLegacyPermissions = root.node("compatibility", "legacy-permissions", "warn").getBoolean(true)
        val authenticationRequired = root.node("protocol", "authentication", "required").getBoolean(false)
        val secret = root.node("protocol", "authentication", "shared-secret").getString("").trim().takeIf(String::isNotEmpty)
        if (authenticationRequired && (secret?.toByteArray()?.size ?: 0) < 32) {
            logger.severe("VeloUtils Bridge requires a shared secret of at least 32 bytes. Disabling bridge.")
            server.pluginManager.disablePlugin(this)
            return
        }
        if (!authenticationRequired) logger.warning("Bridge protocol authentication is disabled; enable it on both proxy and bridge before production use.")
        val remoteCommandsEnabled = root.node("protocol", "remote-commands", "enabled").getBoolean(false)
        val remoteCommandAllowlist = root.node("protocol", "remote-commands", "allowlist")
            .getList(String::class.java, emptyList()).toSet()
        if (remoteCommandsEnabled && (!authenticationRequired || remoteCommandAllowlist.isEmpty())) {
            logger.severe("Remote commands require authenticated protocol mode and a non-empty allowlist. Disabling bridge.")
            server.pluginManager.disablePlugin(this)
            return
        }
        val maximumPayloadBytes = root.node("protocol", "maximum-payload-bytes").getInt(32 * 1_024)
        if (maximumPayloadBytes !in 1_024..1_048_576) {
            logger.severe("Bridge maximum-payload-bytes must be between 1024 and 1048576. Disabling bridge.")
            server.pluginManager.disablePlugin(this)
            return
        }

        schedulers = PlatformSchedulers(this)
        val muteEnforcement = MuteEnforcement(
            schedulers,
            root.node("moderation", "mute-message").getString("<red>You are muted.</red> <gray><reason></gray>"),
        )
        gateway = BridgeProtocolGateway(
            this,
            ProtocolCodec(
                ProtocolSecurity(secret?.toByteArray(), authenticationRequired),
                maximumPayloadBytes = maximumPayloadBytes,
            ),
            schedulers.isFolia,
            schedulers,
            RemoteCommandPolicy(remoteCommandsEnabled, remoteCommandAllowlist),
            placeholders,
            muteEnforcement,
            authenticationRequired,
        )
        server.messenger.registerIncomingPluginChannel(this, BridgeProtocolGateway.CHANNEL, gateway)
        server.messenger.registerOutgoingPluginChannel(this, BridgeProtocolGateway.CHANNEL)
        server.pluginManager.registerEvents(this, this)
        server.pluginManager.registerEvents(muteEnforcement, this)
        registerCommands()
        registerPlaceholders()
        schedulers.repeatGlobal(20L, heartbeat.seconds * 20L) { sendHeartbeat() }
        logger.info("VeloUtils Bridge enabled on ${if (schedulers.isFolia) "Folia" else "Paper"} ${server.minecraftVersion}.")
    }

    private fun saveDefaultConfigFile() {
        if (java.nio.file.Files.notExists(dataPath.resolve("config.yml"))) saveResource("config.yml", false)
    }

    @EventHandler
    public fun onJoin(event: PlayerJoinEvent) {
        gateway.hello(event.player)
    }

    private fun sendHeartbeat() {
        val carrier = server.onlinePlayers.firstOrNull() ?: return
        schedulers.entity(carrier) { gateway.heartbeat(carrier) }
    }

    private fun registerCommands() {
        getCommand("vualert")?.setExecutor { sender, _, _, arguments ->
            if (!hasPermission(sender, Permissions.ALERT_BROADCAST)) {
                sender.sendMessage("You do not have permission to broadcast network alerts.")
                return@setExecutor true
            }
            val carrier = (sender as? Player) ?: server.onlinePlayers.firstOrNull()
            if (carrier == null) {
                sender.sendMessage("No connected player is available as a secure plugin-message carrier.")
                return@setExecutor true
            }
            runCatching { gateway.alert(sender, carrier, arguments.joinToString(" ")) }
                .onFailure { sender.sendMessage("Alert rejected: ${it.message ?: "invalid message"}") }
            true
        }
        mapOf("sc" to "staff", "ac" to "admin").forEach { (command, channel) ->
            getCommand(command)?.setExecutor { sender, _, _, arguments ->
                val permission = if (channel == "staff") Permissions.CHAT_STAFF_USE else Permissions.CHAT_ADMIN_USE
                if (!hasPermission(sender, permission)) {
                    sender.sendMessage("You do not have permission to use ${channel.replaceFirstChar(Char::uppercase)} Chat.")
                    return@setExecutor true
                }
                val player = sender as? Player
                if (player == null) {
                    sender.sendMessage("${channel.replaceFirstChar(Char::uppercase)} Chat requires a player sender; use /vualert for console broadcasts.")
                    return@setExecutor true
                }
                runCatching { gateway.chat(player, channel, arguments.joinToString(" ")) }
                    .onFailure { player.sendMessage("Message rejected: ${it.message ?: "invalid message"}") }
                true
            }
        }
    }

    private fun hasPermission(sender: org.bukkit.command.CommandSender, permission: PermissionDefinition): Boolean {
        if (sender.hasPermission(permission.node)) return true
        if (!legacyPermissionsEnabled) return false
        val alias = permission.legacyAliases.firstOrNull(sender::hasPermission) ?: return false
        if (warnOnLegacyPermissions && warnedLegacyPermissions.add(alias)) {
            logger.warning("Legacy permission '$alias' is in use; migrate to '${permission.node}'.")
        }
        return true
    }

    private fun registerPlaceholders() {
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            NetworkPlaceholderExpansion(this, placeholders).register()
            logger.info("PlaceholderAPI integration enabled.")
        } else logger.info("PlaceholderAPI not installed; placeholder integration remains disabled.")
    }

    override fun onDisable() {
        server.messenger.unregisterIncomingPluginChannel(this)
        server.messenger.unregisterOutgoingPluginChannel(this)
    }
}
