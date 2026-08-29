// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.integration.tab

import de.samthedev.veloutils.proxy.config.TabIntegrationConfig
import me.neznamy.tab.api.TabAPI
import me.neznamy.tab.api.event.EventBus
import me.neznamy.tab.api.event.EventHandler
import me.neznamy.tab.api.event.plugin.TabLoadEvent
import me.neznamy.tab.api.placeholder.PlaceholderManager
import org.slf4j.Logger
import java.util.concurrent.atomic.AtomicBoolean

internal enum class TabIntegrationDecision { DISABLED, UNAVAILABLE, ENABLED }

internal fun tabIntegrationDecision(config: TabIntegrationConfig, tabInstalled: Boolean): TabIntegrationDecision = when {
    !config.enabled || !config.placeholdersEnabled -> TabIntegrationDecision.DISABLED
    !tabInstalled -> TabIntegrationDecision.UNAVAILABLE
    else -> TabIntegrationDecision.ENABLED
}

internal class TabIntegration(
    private val provider: TabPlaceholderProvider,
    backendNames: Set<String>,
    private val logger: Logger,
) : AutoCloseable {
    private val backendNames = backendNames.filterTo(linkedSetOf()) { it.matches(Regex("[a-zA-Z0-9._-]{1,64}")) }
    private val active = AtomicBoolean()
    private val identifiers = linkedSetOf<String>()
    private var eventBus: EventBus? = null
    private val reloadHandler = EventHandler<TabLoadEvent> {
        runCatching { registerPlaceholders() }.onFailure { failure ->
            logger.error("[VeloUtils] TAB reloaded, but VeloUtils placeholders could not be re-registered: {}", failure.message, failure)
        }
    }

    fun start() {
        check(active.compareAndSet(false, true)) { "TAB integration is already active" }
        try {
            registerPlaceholders()
            eventBus = checkNotNull(TabAPI.getInstance().eventBus) { "TAB event bus is unavailable" }.also { bus ->
                bus.register(TabLoadEvent::class.java, reloadHandler)
            }
        } catch (failure: Throwable) {
            active.set(false)
            runCatching { unregisterPlaceholders() }
            throw failure
        }
    }

    @Synchronized
    private fun registerPlaceholders() {
        if (!active.get()) return
        val manager = TabAPI.getInstance().placeholderManager
        unregisterPlaceholders(manager)

        registerServer(manager, "veloutils_network_players", NETWORK_REFRESH, provider::networkPlayers)
        registerServer(manager, "veloutils_backend_count", SLOW_REFRESH, provider::backendCount)
        registerServer(manager, "veloutils_online_backend_count", NETWORK_REFRESH, provider::onlineBackendCount)
        registerServer(manager, "veloutils_network_status", NETWORK_REFRESH, provider::networkStatus)
        registerServer(manager, "veloutils_uptime", NETWORK_REFRESH, provider::uptime)

        registerPlayer(manager, "veloutils_server", PLAYER_REFRESH, provider::server)
        registerPlayer(manager, "veloutils_server_display_name", PLAYER_REFRESH, provider::serverDisplayName)
        registerPlayer(manager, "veloutils_server_players", NETWORK_REFRESH, provider::serverPlayers)
        registerPlayer(manager, "veloutils_server_max_players", SLOW_REFRESH, provider::serverMaximumPlayers)
        registerPlayer(manager, "veloutils_ping", PLAYER_REFRESH, provider::ping)
        registerPlayer(manager, "veloutils_maintenance", NETWORK_REFRESH, provider::maintenance)
        registerPlayer(manager, "veloutils_maintenance_reason", NETWORK_REFRESH, provider::maintenanceReason)

        backendNames.sorted().forEach { backend ->
            val suffix = backend.lowercase()
            registerServer(manager, "veloutils_server_status_$suffix", NETWORK_REFRESH) { provider.backendStatus(backend) }
            registerServer(manager, "veloutils_server_players_$suffix", NETWORK_REFRESH) { provider.backendPlayers(backend) }
        }
    }

    private fun registerServer(manager: PlaceholderManager, name: String, refresh: Int, supplier: () -> String) {
        val identifier = "%$name%"
        manager.registerServerPlaceholder(identifier, refresh, supplier)
        identifiers += identifier
    }

    private fun registerPlayer(
        manager: PlaceholderManager,
        name: String,
        refresh: Int,
        resolver: (java.util.UUID) -> String,
    ) {
        val identifier = "%$name%"
        manager.registerPlayerPlaceholder(identifier, refresh) { player -> resolver(player.uniqueId) }
        identifiers += identifier
    }

    @Synchronized
    private fun unregisterPlaceholders(manager: PlaceholderManager = TabAPI.getInstance().placeholderManager) {
        identifiers.forEach { identifier -> runCatching { manager.unregisterPlaceholder(identifier) } }
        identifiers.clear()
    }

    override fun close() {
        if (!active.compareAndSet(true, false)) return
        eventBus?.let { bus -> runCatching { bus.unregister(reloadHandler) } }
        eventBus = null
        runCatching { unregisterPlaceholders() }.onFailure { failure ->
            logger.debug("[VeloUtils] TAB placeholders were already unavailable during shutdown: {}", failure.message)
        }
    }

    private companion object {
        const val PLAYER_REFRESH: Int = 500
        const val NETWORK_REFRESH: Int = 1_000
        const val SLOW_REFRESH: Int = 5_000
    }
}
