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
import de.samthedev.veloutils.bridge.afk.AfkConfig
import de.samthedev.veloutils.bridge.afk.AfkModule
import de.samthedev.veloutils.bridge.announcement.AnnouncementConfig
import de.samthedev.veloutils.bridge.announcement.AnnouncementModule
import de.samthedev.veloutils.bridge.chat.LocalChatConfig
import de.samthedev.veloutils.bridge.chat.LocalChatModule
import de.samthedev.veloutils.bridge.presentation.PresentationConfig
import de.samthedev.veloutils.bridge.presentation.PresentationModule
import de.samthedev.veloutils.bridge.messaging.LocalMessagingModule
import de.samthedev.veloutils.bridge.messaging.loadMessagingConfig
import de.samthedev.veloutils.core.module.ModuleDescriptor
import de.samthedev.veloutils.core.module.ModuleFactory
import de.samthedev.veloutils.core.module.ModuleId
import de.samthedev.veloutils.core.module.ModuleRuntime
import de.samthedev.veloutils.core.module.ResourceModule
import de.samthedev.veloutils.core.placeholder.PlaceholderFacade
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.ServicePriority
import org.spongepowered.configurate.yaml.YamlConfigurationLoader

public class VeloUtilsBridgePlugin : JavaPlugin(), Listener {
    private lateinit var gateway: BridgeProtocolGateway
    private lateinit var schedulers: PlatformSchedulers
    private var moduleRuntime: ModuleRuntime? = null
    private var placeholders: NetworkPlaceholderCache? = null
    private var placeholderFacade: PlaceholderFacade? = null
    private var placeholderRegistration: AutoCloseable? = null
    private var afkService: de.samthedev.veloutils.api.AfkService? = null
    private var chatService: de.samthedev.veloutils.api.ChatService? = null
    private var muteEnforcement: MuteEnforcement? = null
    private var presentationService: de.samthedev.veloutils.api.PresentationService? = null
    private var messagingService: de.samthedev.veloutils.api.MessagingService? = null
    private var localMessagingModule: LocalMessagingModule? = null
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
        val moderationEnabled = root.node("modules", "moderation").getBoolean(false)
        val serverId = root.node("server-id").getString("").trim().ifEmpty { server.name }
        val placeholdersEnabled = root.node("modules", "placeholders").getBoolean(true)
        val staffChatEnabled = root.node("modules", "staff-chat").getBoolean(true)
        val networkAlertsEnabled = root.node("modules", "network-alerts").getBoolean(true)

        schedulers = PlatformSchedulers(this)
        placeholders = if (placeholdersEnabled) NetworkPlaceholderCache() else null
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
            { muteEnforcement },
            { payload -> localMessagingModule?.receiveNetwork(payload) ?: de.samthedev.veloutils.protocol.DeliveryStatus.MODULE_DISABLED },
            authenticationRequired,
        )
        server.messenger.registerIncomingPluginChannel(this, BridgeProtocolGateway.CHANNEL, gateway)
        server.messenger.registerOutgoingPluginChannel(this, BridgeProtocolGateway.CHANNEL)
        server.pluginManager.registerEvents(this, this)
        registerCommands(staffChatEnabled, networkAlertsEnabled)
        if (placeholdersEnabled) {
            val cache = checkNotNull(placeholders)
            val facade = PlaceholderFacade().also { placeholderFacade = it }
            placeholderRegistration = facade.register("veloutils") { context ->
                buildMap {
                    put("server", serverId)
                    put("network_online", cache.get("network_players") ?: server.onlinePlayers.size.toString())
                    put("maintenance", cache.get("maintenance") ?: "false")
                    put("afk", context.playerId?.let { afkService?.snapshot(it)?.afk?.toString() } ?: "false")
                    cache.snapshot().forEach(::put)
                }
            }
            registerPlaceholders(cache, serverId)
        }
        val afk = ModuleId("afk")
        val announcements = ModuleId("announcements")
        val chat = ModuleId("chat")
        val moderation = ModuleId("moderation")
        val presentation = ModuleId("presentation")
        val messaging = ModuleId("messaging")
        moduleRuntime = ModuleRuntime(
            mapOf(
                ModuleDescriptor(afk) to ModuleFactory {
                    AfkModule(this, schedulers, AfkConfig.load(this)).also { afkService = it.states }
                },
                ModuleDescriptor(announcements) to ModuleFactory {
                    AnnouncementModule(this, schedulers, AnnouncementConfig.load(this))
                },
                ModuleDescriptor(chat) to ModuleFactory {
                    LocalChatModule(
                        this,
                        schedulers,
                        serverId,
                        LocalChatConfig.load(this),
                        placeholderFacade,
                        gateway::chat,
                    ).also { chatService = it }
                },
                ModuleDescriptor(moderation) to ModuleFactory {
                    ResourceModule {
                        val enforcement = MuteEnforcement(
                            schedulers,
                            root.node("moderation", "mute-message").getString("<red>You are muted.</red> <gray><reason></gray>"),
                        ).also { muteEnforcement = it }
                        server.pluginManager.registerEvents(enforcement, this)
                        AutoCloseable {
                            HandlerList.unregisterAll(enforcement)
                            if (muteEnforcement === enforcement) muteEnforcement = null
                        }
                    }
                },
                ModuleDescriptor(presentation) to ModuleFactory {
                    PresentationModule(this, schedulers, serverId, PresentationConfig.load(this, schedulers), placeholderFacade) {
                        afkService as? de.samthedev.veloutils.bridge.afk.AfkStateService
                    }
                        .also { presentationService = it }
                },
                ModuleDescriptor(messaging) to ModuleFactory {
                    LocalMessagingModule(
                        this,
                        schedulers,
                        loadMessagingConfig(this),
                        gateway::privateMessage,
                        gateway::updateIgnore,
                        gateway::requestIgnoreList,
                    ).also {
                        messagingService = it
                        localMessagingModule = it
                    }
                },
            ),
        ).also { runtime ->
            val enabled = buildSet {
                if (root.node("modules", "afk").getBoolean(false)) add(afk)
                if (root.node("modules", "announcements").getBoolean(false)) add(announcements)
                if (root.node("modules", "chat").getBoolean(false)) add(chat)
                if (moderationEnabled) add(moderation)
                if (root.node("modules", "presentation").getBoolean(false)) add(presentation)
                if (root.node("modules", "messaging").getBoolean(false)) add(messaging)
            }
            runtime.start(enabled)
            val apiEnabled = enabled.mapTo(mutableSetOf()) { it.value }.apply {
                if (placeholdersEnabled) add("placeholders")
                if (staffChatEnabled) add("staff-chat")
                if (networkAlertsEnabled) add("network-alerts")
            }
            server.servicesManager.register(
                de.samthedev.veloutils.api.VeloUtilsApi::class.java,
                PaperApiServices(apiEnabled, afkService, chatService, placeholderFacade, presentationService, messagingService),
                this,
                ServicePriority.Normal,
            )
        }
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

    private fun registerCommands(staffChatEnabled: Boolean, networkAlertsEnabled: Boolean) {
        if (networkAlertsEnabled) getCommand("vualert")?.setExecutor { sender, _, _, arguments ->
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
        if (staffChatEnabled) mapOf("sc" to "staff", "ac" to "admin").forEach { (command, channel) ->
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

    private fun registerPlaceholders(cache: NetworkPlaceholderCache, serverId: String) {
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            NetworkPlaceholderExpansion(this, serverId, cache) { afkService }.register()
            logger.info("PlaceholderAPI integration enabled.")
        } else logger.info("PlaceholderAPI not installed; placeholder integration remains disabled.")
    }

    override fun onDisable() {
        moduleRuntime?.close()
        moduleRuntime = null
        afkService = null
        chatService = null
        muteEnforcement = null
        presentationService = null
        messagingService = null
        localMessagingModule = null
        placeholderRegistration?.close()
        placeholderRegistration = null
        placeholderFacade = null
        placeholders = null
        server.servicesManager.unregisterAll(this)
        server.messenger.unregisterIncomingPluginChannel(this)
        server.messenger.unregisterOutgoingPluginChannel(this)
    }
}
