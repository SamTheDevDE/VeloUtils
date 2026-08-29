// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.bootstrap

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import de.samthedev.veloutils.protocol.ProtocolCodec
import de.samthedev.veloutils.protocol.ProtocolSecurity
import de.samthedev.veloutils.proxy.command.NetworkCommand
import de.samthedev.veloutils.proxy.command.NetworkCommandKind
import de.samthedev.veloutils.proxy.command.RootCommand
import de.samthedev.veloutils.proxy.command.ServerExecuteCommand
import de.samthedev.veloutils.proxy.command.MaintenanceCommand
import de.samthedev.veloutils.proxy.command.ReportCreateCommand
import de.samthedev.veloutils.proxy.command.ReportManageCommand
import de.samthedev.veloutils.proxy.command.reportCooldowns
import de.samthedev.veloutils.proxy.command.ModerationCommand
import de.samthedev.veloutils.proxy.command.ModerationCommandKind
import de.samthedev.veloutils.proxy.command.PunishmentCommand
import de.samthedev.veloutils.proxy.command.StaffCommand
import de.samthedev.veloutils.proxy.command.StaffCommandKind
import de.samthedev.veloutils.proxy.command.ConfiguredCommandLoader
import de.samthedev.veloutils.proxy.command.ConfiguredMoveCommand
import de.samthedev.veloutils.proxy.command.ConfiguredMessageCommand
import de.samthedev.veloutils.proxy.config.ConfigRepository
import de.samthedev.veloutils.proxy.config.StorageType
import de.samthedev.veloutils.proxy.messaging.ProxyProtocolGateway
import de.samthedev.veloutils.proxy.messaging.MessagingPreferencesRepository
import de.samthedev.veloutils.proxy.maintenance.MaintenanceListener
import de.samthedev.veloutils.proxy.maintenance.PersistentMaintenanceService
import de.samthedev.veloutils.proxy.maintenance.MaintenanceScheduler
import de.samthedev.veloutils.proxy.report.PersistentReportService
import de.samthedev.veloutils.proxy.moderation.IpAddressHasher
import de.samthedev.veloutils.proxy.moderation.ModerationEnforcement
import de.samthedev.veloutils.proxy.moderation.PersistentModerationService
import de.samthedev.veloutils.proxy.staff.VelocityStaffService
import de.samthedev.veloutils.api.ReportType
import de.samthedev.veloutils.api.VeloUtilsApi
import de.samthedev.veloutils.api.PlaceholderContext
import de.samthedev.veloutils.api.NetworkService
import de.samthedev.veloutils.api.MaintenanceService
import de.samthedev.veloutils.api.StaffService
import de.samthedev.veloutils.api.ReportService
import de.samthedev.veloutils.api.ModerationService
import de.samthedev.veloutils.api.ModuleService
import de.samthedev.veloutils.proxy.network.BridgeStatusRegistry
import de.samthedev.veloutils.proxy.network.VelocityNetworkService
import de.samthedev.veloutils.proxy.network.ServerAccessListener
import de.samthedev.veloutils.proxy.network.MotdListener
import de.samthedev.veloutils.proxy.storage.DatabaseDialect
import de.samthedev.veloutils.proxy.storage.JdbcStorageProvider
import de.samthedev.veloutils.proxy.storage.StorageProvider
import de.samthedev.veloutils.proxy.permission.PermissionService
import de.samthedev.veloutils.proxy.player.PlayerIdentityService
import de.samthedev.veloutils.proxy.moderation.SelfPunishmentConfirmations
import de.samthedev.veloutils.proxy.util.ConfiguredMessages
import de.samthedev.veloutils.common.RemoteCommandPolicy
import de.samthedev.veloutils.common.Permissions
import de.samthedev.veloutils.core.placeholder.PlaceholderFacade
import de.samthedev.veloutils.core.module.ModuleDescriptor
import de.samthedev.veloutils.core.module.ModuleFactory
import de.samthedev.veloutils.core.module.ModuleId
import de.samthedev.veloutils.core.module.ModuleRuntime
import de.samthedev.veloutils.core.module.ResourceModule
import de.samthedev.veloutils.proxy.integration.DiscordWebhookService
import de.samthedev.veloutils.proxy.integration.LimboFallbackAdapter
import de.samthedev.veloutils.proxy.integration.ModrinthUpdateProvider
import de.samthedev.veloutils.proxy.integration.NetworkEventSink
import de.samthedev.veloutils.proxy.integration.NoopNetworkEventSink
import de.samthedev.veloutils.proxy.integration.RotatingAlertService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.Logger
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CopyOnWriteArrayList

public class VeloUtilsPlugin @Inject constructor(
    private val proxy: ProxyServer,
    private val logger: Logger,
    @DataDirectory private val dataDirectory: Path,
) : VeloUtilsApi {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val config = ConfigRepository(dataDirectory)
    private val messages = ConfiguredMessages(dataDirectory.resolve("messages.yml"), warning = { warning ->
        logger.warn("[VeloUtils] {}", warning)
    })
    private val bridgeStatuses = BridgeStatusRegistry()
    private val placeholderFacade = PlaceholderFacade()
    private var storage: StorageProvider? = null
    private var protocolGateway: ProxyProtocolGateway? = null
    private val publishedApi = AtomicReference<VeloUtilsApi?>()
    private val ownedResources = CopyOnWriteArrayList<AutoCloseable>()

    override val network: NetworkService get() = checkNotNull(api().network)
    override val modules: ModuleService get() = api().modules
    override val maintenance: MaintenanceService? get() = api().maintenance
    override val staff: StaffService? get() = api().staff
    override val reports: ReportService? get() = api().reports
    override val moderation: ModerationService? get() = api().moderation

    private fun api(): VeloUtilsApi = checkNotNull(publishedApi.get()) { "VeloUtils has not finished initializing" }

    @Subscribe
    public fun initialize(event: ProxyInitializeEvent) {
        logger.info("[VeloUtils] Loading VeloUtils {}...", BuildInfo.VERSION)
        scope.launch(Dispatchers.IO) {
            runCatching {
                val snapshot = config.load()
                messages.reload()
                val permissions = PermissionService(
                    snapshot.legacyPermissions.enabled,
                    snapshot.legacyPermissions.warn,
                    logger,
                )
                val eventSink: NetworkEventSink = if (snapshot.modules.discord) {
                    DiscordWebhookService(snapshot.discord, scope, logger).also(ownedResources::add)
                } else NoopNetworkEventSink
                if (!snapshot.protocol.requireAuthentication) {
                    logger.warn("[VeloUtils] Bridge protocol authentication is disabled. Configure a shared secret before production use.")
                }
                val codec = ProtocolCodec(
                    ProtocolSecurity(snapshot.protocol.sharedSecret?.toByteArray(), snapshot.protocol.requireAuthentication),
                    maximumPayloadBytes = snapshot.protocol.maximumPayloadBytes,
                )
                val activeStorage = createStorage(snapshot.storage).also { it.initialize() }
                storage = activeStorage
                val identities = PlayerIdentityService(activeStorage, scope).also { it.loadRecent() }
                val messagingPreferences = if (snapshot.modules.messaging) MessagingPreferencesRepository(activeStorage) else null
                val gateway = ProxyProtocolGateway(
                    codec,
                    bridgeStatuses,
                    logger,
                    RemoteCommandPolicy(snapshot.protocol.remoteCommandsEnabled, snapshot.protocol.commandAllowlist),
                    snapshot.protocol.requestTimeout,
                    proxy,
                    messages,
                    snapshot.modules.staffChat,
                    snapshot.modules.chat,
                    snapshot.modules.messaging,
                    messagingPreferences,
                    identities,
                    scope,
                    eventSink,
                    permissions,
                    snapshot.protocol.requireAuthentication,
                    snapshot.playerFormatting,
                )
                protocolGateway = gateway
                proxy.channelRegistrar.register(ProxyProtocolGateway.CHANNEL)
                proxy.eventManager.register(this@VeloUtilsPlugin, gateway)

                val network = VelocityNetworkService(proxy, bridgeStatuses)
                proxy.eventManager.register(this@VeloUtilsPlugin, identities)
                registerCommands(network, gateway, snapshot, permissions)
                if (snapshot.modules.networkCommands) {
                    val configuredCommands = ConfiguredCommandLoader.load(dataDirectory.resolve("commands.yml"))
                    configuredCommands.move.forEach { definition ->
                        proxy.commandManager.register(
                            proxy.commandManager.metaBuilder(definition.name).aliases(*definition.aliases.toTypedArray())
                                .plugin(this@VeloUtilsPlugin).build(),
                            ConfiguredMoveCommand(definition, network, messages, scope),
                        )
                    }
                    configuredCommands.message.forEach { definition ->
                        proxy.commandManager.register(
                            proxy.commandManager.metaBuilder(definition.name).aliases(*definition.aliases.toTypedArray())
                                .plugin(this@VeloUtilsPlugin).build(),
                            ConfiguredMessageCommand(definition, messages),
                        )
                    }
                }
                var maintenanceService: PersistentMaintenanceService? = null
                var maintenanceApi: MaintenanceService? = null
                var reportApi: ReportService? = null
                var moderationApi: ModerationService? = null
                var staffApi: StaffService? = null
                if (snapshot.modules.maintenance) {
                    val maintenance = PersistentMaintenanceService(activeStorage).also { it.load() }
                    maintenanceService = maintenance
                    maintenanceApi = maintenance
                    val maintenanceScheduler = MaintenanceScheduler(
                        activeStorage,
                        maintenance,
                        scope,
                        broadcastSink = { message ->
                            proxy.allPlayers.forEach { it.sendMessage(message) }
                            proxy.consoleCommandSource.sendMessage(message)
                        },
                        preTransferBefore = snapshot.maintenanceTransfer.before.takeIf {
                            snapshot.maintenanceTransfer.enabled
                        },
                        transferSink = { scheduled ->
                            val candidates = proxy.allPlayers.filter { player ->
                                !permissions.has(player, Permissions.MAINTENANCE_BYPASS) &&
                                    (scheduled.server == null || player.currentServer
                                        .map { it.serverInfo.name.equals(scheduled.server, true) }
                                        .orElse(false))
                            }
                            candidates.forEach { player ->
                                scope.launch {
                                    network.connect(player.uniqueId, snapshot.maintenanceTransfer.destinations)
                                }
                            }
                        },
                    ).also {
                        it.load()
                        it.start()
                    }
                    ownedResources += maintenanceScheduler
                    proxy.eventManager.register(this@VeloUtilsPlugin, MaintenanceListener(maintenance, messages))
                    proxy.commandManager.register(
                        proxy.commandManager.metaBuilder("maintenance").plugin(this@VeloUtilsPlugin).build(),
                        MaintenanceCommand(proxy, maintenance, maintenanceScheduler, messages, identities, permissions, scope, eventSink),
                    )
                }
                if (snapshot.modules.reports) {
                    val reports = PersistentReportService(activeStorage)
                    reportApi = reports
                    val cooldowns = reportCooldowns()
                    proxy.commandManager.register(
                        proxy.commandManager.metaBuilder("report").plugin(this@VeloUtilsPlugin).build(),
                        ReportCreateCommand(ReportType.PLAYER, proxy, reports, messages, permissions, scope, cooldowns, eventSink),
                    )
                    proxy.commandManager.register(
                        proxy.commandManager.metaBuilder("helpop").plugin(this@VeloUtilsPlugin).build(),
                        ReportCreateCommand(ReportType.HELPOP, proxy, reports, messages, permissions, scope, cooldowns, eventSink),
                    )
                    proxy.commandManager.register(
                        proxy.commandManager.metaBuilder("reports").plugin(this@VeloUtilsPlugin).build(),
                        ReportManageCommand(proxy, reports, permissions, snapshot.ui.pageSize, scope),
                    )
                }
                if (snapshot.modules.serverAccess) {
                    proxy.eventManager.register(
                        this@VeloUtilsPlugin,
                        ServerAccessListener(proxy, snapshot.serverAccessRules, messages, permissions),
                    )
                }
                if (snapshot.modules.moderation) {
                    val hasher = snapshot.moderation.ipHashKey?.toByteArray()?.let(::IpAddressHasher)
                    val moderation = PersistentModerationService(activeStorage, hasher)
                    moderationApi = moderation
                    val enforcement = ModerationEnforcement(moderation, messages, scope, gateway)
                    proxy.eventManager.register(this@VeloUtilsPlugin, enforcement)
                    if (!snapshot.protocol.requireAuthentication) {
                        logger.warn(
                            "[VeloUtils] Mute commands are disabled because authenticated bridge messaging is required for mute enforcement.",
                        )
                    }
                    val confirmations = SelfPunishmentConfirmations(snapshot.moderation.selfPunishmentConfirmation)
                    ModerationCommandKind.entries.filter { kind ->
                        snapshot.protocol.requireAuthentication ||
                            kind !in setOf(ModerationCommandKind.MUTE, ModerationCommandKind.TEMPMUTE, ModerationCommandKind.UNMUTE)
                    }.forEach { kind ->
                        val name = kind.name.lowercase()
                        proxy.commandManager.register(
                            proxy.commandManager.metaBuilder(name).plugin(this@VeloUtilsPlugin).build(),
                            ModerationCommand(
                                kind, proxy, moderation, identities, gateway, permissions, confirmations,
                                snapshot.ui.pageSize, scope, eventSink,
                            ),
                        )
                    }
                    proxy.commandManager.register(
                        proxy.commandManager.metaBuilder("punishment").plugin(this@VeloUtilsPlugin).build(),
                        PunishmentCommand(moderation, identities, permissions, scope),
                    )
                }
                if (snapshot.modules.staff) {
                    val staff = VelocityStaffService(proxy, activeStorage, scope, permissions, eventSink = eventSink)
                    staffApi = staff
                    proxy.eventManager.register(this@VeloUtilsPlugin, staff)
                    proxy.commandManager.register(
                        proxy.commandManager.metaBuilder("stafflist").plugin(this@VeloUtilsPlugin).build(),
                        StaffCommand(StaffCommandKind.LIST, staff, identities, permissions, snapshot.ui.pageSize, scope),
                    )
                    proxy.commandManager.register(
                        proxy.commandManager.metaBuilder("stafftime").plugin(this@VeloUtilsPlugin).build(),
                        StaffCommand(StaffCommandKind.TIME, staff, identities, permissions, snapshot.ui.pageSize, scope),
                    )
                }
                if (snapshot.modules.motd) {
                    proxy.eventManager.register(
                        this@VeloUtilsPlugin,
                        MotdListener(
                            proxy,
                            snapshot.motd,
                            dataDirectory,
                            maintenance = { maintenanceService?.snapshot() },
                            placeholderValues = { placeholderFacade.resolve(PlaceholderContext(server = "proxy")) },
                        ),
                    )
                }
                if (snapshot.limbo.enabled) {
                    proxy.eventManager.register(this@VeloUtilsPlugin, LimboFallbackAdapter(proxy, snapshot.limbo.server))
                    logger.info("[VeloUtils] Limbo fallback adapter targets {}.", snapshot.limbo.server)
                }
                val announcementsModule = ModuleId("announcements")
                ownedResources += ModuleRuntime(
                    mapOf(
                        ModuleDescriptor(announcementsModule) to ModuleFactory {
                            ResourceModule { RotatingAlertService(proxy, snapshot.alerts, eventSink, scope) }
                        },
                    ),
                ).also { runtime ->
                    runtime.start(if (snapshot.modules.alerts) setOf(announcementsModule) else emptySet())
                }
                if (snapshot.updates.enabled) {
                    ownedResources += ModrinthUpdateProvider(snapshot.updates, BuildInfo.VERSION, scope, logger).also { it.start() }
                }
                ownedResources += placeholderFacade.register("veloutils") { context ->
                    buildMap {
                        put("network_online", proxy.playerCount.toString())
                        put("server", context.server ?: "proxy")
                        put("maintenance", (maintenanceService?.snapshot()?.global != null).toString())
                        context.server?.lowercase()?.let { serverName ->
                            put("server_online", proxy.getServer(serverName).isPresent.toString())
                            put("server_players", proxy.getServer(serverName).map { it.playersConnected.size }.orElse(0).toString())
                        }
                    }
                }
                val knownModules = setOf(
                    "maintenance", "reports", "staff", "staff-chat", "chat", "messaging", "moderation", "motd", "server-access",
                    "network", "discord", "announcements", "placeholders",
                )
                val enabledModules = buildSet {
                    add("placeholders")
                    if (snapshot.modules.maintenance) add("maintenance")
                    if (snapshot.modules.reports) add("reports")
                    if (snapshot.modules.staff) add("staff")
                    if (snapshot.modules.staffChat) add("staff-chat")
                    if (snapshot.modules.chat) add("chat")
                    if (snapshot.modules.messaging) add("messaging")
                    if (snapshot.modules.moderation) add("moderation")
                    if (snapshot.modules.motd) add("motd")
                    if (snapshot.modules.serverAccess) add("server-access")
                    if (snapshot.modules.networkCommands) add("network")
                    if (snapshot.modules.discord) add("discord")
                    if (snapshot.modules.alerts) add("announcements")
                }
                publishedApi.set(
                    ApiServices(
                        StaticModuleService(enabledModules, knownModules),
                        network,
                        maintenanceApi,
                        staffApi,
                        reportApi,
                        moderationApi,
                        placeholderFacade,
                    ),
                )
                logger.info("[VeloUtils] Started successfully with {} registered servers.", proxy.allServers.size)
            }.onFailure { failure ->
                logger.error("[VeloUtils] Startup failed: {} Check the VeloUtils configuration and database connectivity.", failure.message, failure)
            }
        }
    }

    private fun registerCommands(
        network: VelocityNetworkService,
        gateway: ProxyProtocolGateway,
        snapshot: de.samthedev.veloutils.proxy.config.ProxyConfig,
        permissions: PermissionService,
    ) {
        val manager = proxy.commandManager
        manager.register(
            manager.metaBuilder("veloutils").aliases("vu").plugin(this).build(),
            RootCommand(BuildInfo.VERSION, config, messages, network, permissions, scope),
        )
        if (snapshot.modules.networkCommands) {
            mapOf(
                "find" to NetworkCommandKind.FIND,
                "goto" to NetworkCommandKind.GOTO,
                "vlist" to NetworkCommandKind.LIST,
                "network" to NetworkCommandKind.NETWORK,
                "serverinfo" to NetworkCommandKind.SERVER_INFO,
                "send" to NetworkCommandKind.SEND,
                "sendall" to NetworkCommandKind.SEND_ALL,
            ).forEach { (name, kind) ->
                manager.register(
                    manager.metaBuilder(name).plugin(this).build(),
                    NetworkCommand(kind, proxy, network, permissions, snapshot.ui.pageSize, scope),
                )
            }
        }
        if (snapshot.protocol.remoteCommandsEnabled) {
            manager.register(
                manager.metaBuilder("serverexecute").plugin(this).build(),
                ServerExecuteCommand(proxy, gateway, permissions),
            )
        }
    }

    private fun createStorage(settings: de.samthedev.veloutils.proxy.config.StorageConfig): StorageProvider {
        val (url, dialect) = when (settings.type) {
            StorageType.SQLITE -> "jdbc:sqlite:${dataDirectory.resolve("data.db")}" to DatabaseDialect.SQLITE
            StorageType.MYSQL -> "jdbc:mysql://${settings.host}:${settings.port}/${settings.database}?useUnicode=true&characterEncoding=utf8&useSSL=true" to DatabaseDialect.MYSQL
            StorageType.POSTGRESQL -> "jdbc:postgresql://${settings.host}:${settings.port}/${settings.database}" to DatabaseDialect.POSTGRESQL
        }
        val user = settings.username.takeUnless { settings.type == StorageType.SQLITE }
        val password = settings.password.takeUnless { settings.type == StorageType.SQLITE }
        return JdbcStorageProvider(url, user, password, dialect, settings.poolSize)
    }

    @Subscribe
    public fun shutdown(event: ProxyShutdownEvent) {
        storage?.close()
        storage = null
        publishedApi.set(null)
        protocolGateway?.close()
        protocolGateway = null
        ownedResources.reversed().forEach { resource -> runCatching(resource::close) }
        ownedResources.clear()
        scope.cancel("Velocity shutdown")
        logger.info("[VeloUtils] Shutdown complete.")
    }
}
