// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.player

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.proxy.Player
import de.samthedev.veloutils.proxy.storage.StorageProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

public data class PlayerIdentity(
    val playerId: UUID,
    val name: String,
    val lastSeen: Instant,
    val lastServer: String?,
)

public class PlayerIdentityService(
    private val storage: StorageProvider,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val knownNames = ConcurrentHashMap<String, String>()
    private val writes = Mutex()

    public suspend fun loadRecent(limit: Int = 2_000) {
        storage.read { connection ->
            connection.prepareStatement(
                "SELECT name, normalized_name FROM player_identities ORDER BY last_seen DESC LIMIT ?",
            ).use { statement ->
                statement.setInt(1, limit.coerceIn(1, 10_000))
                statement.executeQuery().use { result ->
                    while (result.next()) knownNames[result.getString("normalized_name") ?: result.getString("name").lowercase()] = result.getString("name")
                }
            }
        }
    }

    public suspend fun resolve(input: String): PlayerIdentity? {
        val normalized = input.trim().lowercase()
        if (normalized.isEmpty()) return null
        val uuid = runCatching { UUID.fromString(input) }.getOrNull()
        return storage.read { connection ->
            val sql = if (uuid == null) {
                "SELECT * FROM player_identities WHERE normalized_name = ? OR LOWER(name) = ? ORDER BY last_seen DESC LIMIT 1"
            } else "SELECT * FROM player_identities WHERE player_uuid = ? LIMIT 1"
            connection.prepareStatement(sql).use { statement ->
                if (uuid == null) {
                    statement.setString(1, normalized)
                    statement.setString(2, normalized)
                } else statement.setString(1, uuid.toString())
                statement.executeQuery().use { result ->
                    if (!result.next()) null else PlayerIdentity(
                        UUID.fromString(result.getString("player_uuid")),
                        result.getString("name"),
                        Instant.ofEpochMilli(result.getLong("last_seen")),
                        result.getString("last_server"),
                    )
                }
            }
        }
    }

    public fun suggestions(prefix: String, limit: Int = 20): List<String> {
        val normalized = prefix.lowercase()
        return knownNames.entries.asSequence()
            .filter { (key, _) -> key.startsWith(normalized) }
            .sortedByDescending { it.key == normalized }
            .map(Map.Entry<String, String>::value)
            .distinct()
            .take(limit.coerceIn(1, 50))
            .toList()
    }

    @Subscribe
    public fun onLogin(event: PostLoginEvent) {
        rememberLater(event.player, null)
    }

    @Subscribe
    public fun onServerConnected(event: ServerPostConnectEvent) {
        rememberLater(event.player, event.player.currentServer.map { it.serverInfo.name }.orElse(null))
    }

    public suspend fun remember(playerId: UUID, name: String, server: String?) {
        val normalized = name.lowercase()
        knownNames[normalized] = name
        val now = clock.millis()
        writes.withLock {
            storage.transaction { connection ->
                val updated = connection.prepareStatement(
                    "UPDATE player_identities SET name = ?, normalized_name = ?, last_seen = ?, last_server = ? WHERE player_uuid = ?",
                ).use { statement ->
                    statement.setString(1, name)
                    statement.setString(2, normalized)
                    statement.setLong(3, now)
                    statement.setString(4, server)
                    statement.setString(5, playerId.toString())
                    statement.executeUpdate()
                }
                if (updated == 0) connection.prepareStatement(
                    "INSERT INTO player_identities(player_uuid, name, normalized_name, first_seen, last_seen, last_server) VALUES (?, ?, ?, ?, ?, ?)",
                ).use { statement ->
                    statement.setString(1, playerId.toString())
                    statement.setString(2, name)
                    statement.setString(3, normalized)
                    statement.setLong(4, now)
                    statement.setLong(5, now)
                    statement.setString(6, server)
                    statement.executeUpdate()
                }
            }
        }
    }

    private fun rememberLater(player: Player, server: String?) {
        scope.launch { remember(player.uniqueId, player.username, server) }
    }
}
