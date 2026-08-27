// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.staff

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.proxy.ProxyServer
import de.samthedev.veloutils.api.StaffMemberSnapshot
import de.samthedev.veloutils.api.StaffService
import de.samthedev.veloutils.api.StaffSessionSnapshot
import de.samthedev.veloutils.proxy.storage.StorageProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import de.samthedev.veloutils.proxy.integration.NetworkEventKind
import de.samthedev.veloutils.proxy.integration.NetworkEventSink
import de.samthedev.veloutils.proxy.integration.NoopNetworkEventSink
import de.samthedev.veloutils.common.Permissions
import de.samthedev.veloutils.proxy.permission.PermissionService
import de.samthedev.veloutils.proxy.ui.ChatUi
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

public class VelocityStaffService(
    private val proxy: ProxyServer,
    private val storage: StorageProvider,
    private val scope: CoroutineScope,
    private val permissions: PermissionService,
    private val clock: Clock = Clock.systemUTC(),
    private val eventSink: NetworkEventSink = NoopNetworkEventSink,
) : StaffService {
    private data class ActiveSession(
        val name: String,
        val startedAt: Instant,
        var currentServer: String?,
        var transitionAt: Instant,
        val serverSeconds: MutableMap<String, Long>,
    )

    private val sessions = ConcurrentHashMap<UUID, ActiveSession>()
    private val json = Json

    @Subscribe
    public fun onLogin(event: LoginEvent) {
        if (!permissions.has(event.player, Permissions.STAFF_MEMBER) || permissions.has(event.player, Permissions.STAFF_TIME_EXCLUDE)) return
        val now = Instant.now(clock)
        sessions[event.player.uniqueId] = ActiveSession(event.player.username, now, null, now, mutableMapOf())
        eventSink.emit(NetworkEventKind.STAFF_ACTIVITY, "Staff joined", event.player.username)
        notifyActivity("${event.player.username} joined the network.")
    }

    @Subscribe
    public fun onServerChange(event: ServerPostConnectEvent) {
        val session = sessions[event.player.uniqueId] ?: return
        synchronized(session) {
            accumulate(session)
            session.currentServer = event.player.currentServer.map { it.serverInfo.name }.orElse(null)
            session.transitionAt = Instant.now(clock)
            eventSink.emit(
                NetworkEventKind.STAFF_ACTIVITY,
                "Staff changed server",
                "${event.player.username}: ${event.previousServer?.serverInfo?.name ?: "connecting"} → ${session.currentServer ?: "unknown"}",
            )
            notifyActivity("${event.player.username} moved to ${session.currentServer ?: "an unknown server"}.")
        }
    }

    @Subscribe
    public fun onDisconnect(event: DisconnectEvent) {
        val session = sessions.remove(event.player.uniqueId) ?: return
        eventSink.emit(NetworkEventKind.STAFF_ACTIVITY, "Staff left", event.player.username)
        notifyActivity("${event.player.username} left the network.")
        synchronized(session) { accumulate(session) }
        val endedAt = Instant.now(clock)
        val total = Duration.between(session.startedAt, endedAt).seconds.coerceAtLeast(0)
        val serverTimes = session.serverSeconds.toMap()
        scope.launch {
            storage.transaction { connection ->
                connection.prepareStatement(
                    "INSERT INTO staff_sessions(player_uuid, started_at, ended_at, duration_seconds, server_times_json) VALUES (?, ?, ?, ?, ?)",
                ).use { statement ->
                    statement.setString(1, event.player.uniqueId.toString())
                    statement.setLong(2, session.startedAt.toEpochMilli())
                    statement.setLong(3, endedAt.toEpochMilli())
                    statement.setLong(4, total)
                    statement.setString(5, json.encodeToString(serverTimes))
                    statement.executeUpdate()
                }
            }
        }
    }

    override fun onlineStaff(): List<StaffMemberSnapshot> = sessions.mapNotNull { (playerId, session) ->
        proxy.getPlayer(playerId).orElse(null)?.let { player ->
            StaffMemberSnapshot(playerId, session.name, player.currentServer.map { it.serverInfo.name }.orElse(null), null, session.startedAt)
        }
    }.sortedBy { it.name.lowercase() }

    override fun session(playerId: UUID): StaffSessionSnapshot? = sessions[playerId]?.let { active ->
        synchronized(active) {
            val seconds = active.serverSeconds.toMutableMap()
            active.currentServer?.let { server ->
                seconds.merge(server, Duration.between(active.transitionAt, Instant.now(clock)).seconds.coerceAtLeast(0), Long::plus)
            }
            StaffSessionSnapshot(playerId, active.startedAt, active.currentServer, seconds.mapValues { Duration.ofSeconds(it.value) })
        }
    }

    override suspend fun trackedTime(playerId: UUID, from: Instant, until: Instant): Duration {
        require(until.isAfter(from))
        val storedSeconds = storage.read { connection ->
            connection.prepareStatement(
                "SELECT COALESCE(SUM(duration_seconds), 0) FROM staff_sessions WHERE player_uuid = ? AND started_at >= ? AND started_at < ?",
            ).use { statement ->
                statement.setString(1, playerId.toString())
                statement.setLong(2, from.toEpochMilli())
                statement.setLong(3, until.toEpochMilli())
                statement.executeQuery().use { result -> result.next(); result.getLong(1) }
            }
        }
        val activeSeconds = sessions[playerId]?.let { session ->
            Duration.between(maxOf(session.startedAt, from), minOf(Instant.now(clock), until)).seconds.coerceAtLeast(0)
        } ?: 0
        return Duration.ofSeconds(storedSeconds + activeSeconds)
    }

    private fun accumulate(session: ActiveSession) {
        val now = Instant.now(clock)
        session.currentServer?.let { server ->
            session.serverSeconds.merge(server, Duration.between(session.transitionAt, now).seconds.coerceAtLeast(0), Long::plus)
        }
        session.transitionAt = now
    }

    private fun notifyActivity(message: String) {
        proxy.allPlayers.filter { permissions.has(it, Permissions.STAFF_ACTIVITY_NOTIFY) }
            .forEach { it.sendMessage(ChatUi.info(message)) }
    }
}
