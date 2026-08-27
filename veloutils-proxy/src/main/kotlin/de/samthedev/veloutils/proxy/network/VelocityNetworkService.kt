// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.network

import com.velocitypowered.api.proxy.ProxyServer
import de.samthedev.veloutils.api.BridgeSnapshot
import de.samthedev.veloutils.api.ConnectionOutcome
import de.samthedev.veloutils.api.NetworkService
import de.samthedev.veloutils.api.NetworkSnapshot
import de.samthedev.veloutils.api.PlainUserText
import de.samthedev.veloutils.api.ServerSnapshot
import de.samthedev.veloutils.common.FallbackSelector
import net.kyori.adventure.text.Component
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

public class BridgeStatusRegistry(private val clock: Clock = Clock.systemUTC()) {
    private val status = ConcurrentHashMap<String, BridgeSnapshot>()

    public fun update(server: String, snapshot: BridgeSnapshot) { status[server.lowercase()] = snapshot }
    public fun get(server: String): BridgeSnapshot? = status[server.lowercase()]?.takeIf {
        clock.millis() - it.lastHeartbeat.toEpochMilli() <= 30_000
    }
    public fun entries(): Map<String, BridgeSnapshot> = status.mapNotNull { (name, value) -> get(name)?.let { name to value } }.toMap()
}

public class VelocityNetworkService(
    private val proxy: ProxyServer,
    private val bridgeStatuses: BridgeStatusRegistry,
    private val clock: Clock = Clock.systemUTC(),
) : NetworkService {
    override fun snapshot(): NetworkSnapshot = NetworkSnapshot(
        Instant.now(clock),
        proxy.playerCount,
        proxy.allServers.map { server ->
            ServerSnapshot(
                server.serverInfo.name,
                online = true,
                playerCount = server.playersConnected.size,
                bridge = bridgeStatuses.get(server.serverInfo.name),
            )
        }.sortedBy(ServerSnapshot::name),
    )

    override fun server(name: String): ServerSnapshot? = proxy.getServer(name).map { server ->
        ServerSnapshot(server.serverInfo.name, true, server.playersConnected.size, bridgeStatuses.get(name))
    }.orElse(null)

    override suspend fun connect(playerId: UUID, destinations: List<String>): ConnectionOutcome {
        val player = proxy.getPlayer(playerId).orElse(null) ?: return ConnectionOutcome.PlayerOffline
        val available = proxy.allServers.mapTo(mutableSetOf()) { it.serverInfo.name.lowercase() }
        val selected = FallbackSelector.select(
            destinations, player.currentServer.map { it.serverInfo.name }.orElse(null), available,
        ) { destination -> player.hasPermission("veloutils.server.$destination") || player.hasPermission("veloutils.server-access.bypass") }
            ?: return ConnectionOutcome.NoDestinationAvailable
        val target = proxy.getServer(selected).orElse(null) ?: return ConnectionOutcome.NoDestinationAvailable
        val result = player.createConnectionRequest(target).connect().await()
        return if (result.isSuccessful) ConnectionOutcome.Connected(selected)
        else ConnectionOutcome.Denied(result.reasonComponent.orElse(Component.text("Connection failed")).toString())
    }

    override suspend fun broadcast(message: PlainUserText) {
        val component = Component.text(message.value)
        proxy.allPlayers.forEach { it.sendMessage(component) }
        proxy.consoleCommandSource.sendMessage(component)
    }
}

private suspend fun <T> CompletableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    whenComplete { value, failure ->
        if (failure == null) continuation.resume(value) else continuation.resumeWithException(failure)
    }
    continuation.invokeOnCancellation { cancel(true) }
}
