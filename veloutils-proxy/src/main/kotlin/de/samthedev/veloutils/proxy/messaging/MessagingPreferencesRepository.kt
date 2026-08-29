// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.messaging

import de.samthedev.veloutils.proxy.storage.StorageProvider
import java.time.Instant
import java.util.UUID

public data class IgnoredPlayer(val playerId: UUID, val name: String)

/** Persistent, network-authoritative private-message preferences. */
public class MessagingPreferencesRepository(private val storage: StorageProvider) {
    public suspend fun setIgnoring(ownerId: UUID, target: IgnoredPlayer, ignored: Boolean) {
        require(ownerId != target.playerId) { "A player cannot ignore themselves" }
        storage.transaction { connection ->
            connection.prepareStatement("DELETE FROM message_ignores WHERE owner_uuid = ? AND ignored_uuid = ?").use {
                it.setString(1, ownerId.toString())
                it.setString(2, target.playerId.toString())
                it.executeUpdate()
            }
            if (ignored) connection.prepareStatement(
                "INSERT INTO message_ignores(owner_uuid, ignored_uuid, ignored_name, created_at) VALUES (?, ?, ?, ?)",
            ).use {
                it.setString(1, ownerId.toString())
                it.setString(2, target.playerId.toString())
                it.setString(3, target.name)
                it.setLong(4, Instant.now().toEpochMilli())
                it.executeUpdate()
            }
        }
    }

    public suspend fun isIgnoring(ownerId: UUID, targetId: UUID): Boolean = storage.read { connection ->
        connection.prepareStatement(
            "SELECT 1 FROM message_ignores WHERE owner_uuid = ? AND ignored_uuid = ?",
        ).use {
            it.setString(1, ownerId.toString())
            it.setString(2, targetId.toString())
            it.executeQuery().use { result -> result.next() }
        }
    }

    public suspend fun list(ownerId: UUID, limit: Int = 200): List<IgnoredPlayer> {
        require(limit in 1..200)
        return storage.read { connection ->
            connection.prepareStatement(
                "SELECT ignored_uuid, ignored_name FROM message_ignores WHERE owner_uuid = ? ORDER BY created_at DESC LIMIT ?",
            ).use {
                it.setString(1, ownerId.toString())
                it.setInt(2, limit)
                it.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(IgnoredPlayer(UUID.fromString(result.getString(1)), result.getString(2)))
                    }
                }
            }
        }
    }
}
