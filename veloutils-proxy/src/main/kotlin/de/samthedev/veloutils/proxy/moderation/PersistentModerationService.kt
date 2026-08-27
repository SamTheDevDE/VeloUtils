// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.moderation

import de.samthedev.veloutils.api.CreatePunishment
import de.samthedev.veloutils.api.ModerationService
import de.samthedev.veloutils.api.Punishment
import de.samthedev.veloutils.api.PunishmentId
import de.samthedev.veloutils.api.PunishmentScope
import de.samthedev.veloutils.api.PunishmentType
import de.samthedev.veloutils.common.InputPolicies
import de.samthedev.veloutils.proxy.storage.StorageProvider
import java.net.InetAddress
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.time.Clock
import java.time.Instant
import java.util.UUID

public class PersistentModerationService(
    private val storage: StorageProvider,
    private val ipHasher: IpAddressHasher?,
    private val clock: Clock = Clock.systemUTC(),
) : ModerationService {
    override suspend fun punish(request: CreatePunishment): Punishment {
        validate(request)
        val createdAt = Instant.now(clock)
        val ipHash = if (request.type == PunishmentType.IP_BAN) {
            val address = requireNotNull(request.address) { "IP bans require a current address" }
            requireNotNull(ipHasher) { "IP hash storage is not configured" }.hash(address)
        } else null
        val persistent = request.type in setOf(PunishmentType.BAN, PunishmentType.IP_BAN, PunishmentType.MUTE)
        return storage.transaction { connection ->
            val id = connection.prepareStatement(
                """INSERT INTO punishments(type, target_uuid, target_name, actor_uuid, actor_name, reason,
                    created_at, expires_at, active, scope, server_name, ip_hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
                Statement.RETURN_GENERATED_KEYS,
            ).use { statement ->
                statement.setString(1, request.type.name)
                statement.setString(2, request.targetId.toString())
                statement.setString(3, request.targetName)
                statement.setString(4, request.actorId?.toString())
                statement.setString(5, request.actorName)
                statement.setString(6, InputPolicies.PUNISHMENT_REASON.validate(request.reason))
                statement.setLong(7, createdAt.toEpochMilli())
                statement.setObject(8, request.expiresAt?.toEpochMilli())
                statement.setBoolean(9, persistent)
                statement.setString(10, request.scope.name)
                statement.setString(11, request.server?.lowercase())
                statement.setString(12, ipHash)
                check(statement.executeUpdate() == 1) { "Punishment was not inserted" }
                statement.generatedKeys.use { keys -> check(keys.next()) { "Database did not return a punishment id" }; keys.getLong(1) }
            }
            requirePunishment(connection, id)
        }
    }

    override suspend fun revoke(id: PunishmentId, actorId: UUID?, reason: String): Punishment {
        val validatedReason = InputPolicies.PUNISHMENT_REASON.validate(reason)
        return storage.transaction { connection ->
            connection.prepareStatement(
                "UPDATE punishments SET active = ?, revoked_at = ?, revoked_by_uuid = ?, revocation_reason = ? WHERE id = ? AND active = ?",
            ).use { statement ->
                statement.setBoolean(1, false)
                statement.setLong(2, clock.millis())
                statement.setString(3, actorId?.toString())
                statement.setString(4, validatedReason)
                statement.setLong(5, id.value)
                statement.setBoolean(6, true)
                check(statement.executeUpdate() == 1) { "Punishment does not exist or is already inactive" }
            }
            requirePunishment(connection, id.value)
        }
    }

    override suspend fun activeFor(playerId: UUID, address: InetAddress?): List<Punishment> {
        val addressHash = address?.let { ipHasher?.hash(it) }
        val candidates = storage.read { connection ->
            connection.prepareStatement(
                "SELECT * FROM punishments WHERE active = ? AND (target_uuid = ? OR ip_hash = ?)",
            ).use { statement ->
                statement.setBoolean(1, true)
                statement.setString(2, playerId.toString())
                statement.setString(3, addressHash ?: "")
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toPunishment()) } }
            }
        }
        return ModerationPolicy.effective(candidates, Instant.now(clock), null)
    }

    override suspend fun history(playerId: UUID, limit: Int): List<Punishment> {
        require(limit in 1..200)
        return storage.read { connection ->
            connection.prepareStatement(
                "SELECT * FROM punishments WHERE target_uuid = ? ORDER BY created_at DESC LIMIT ?",
            ).use { statement ->
                statement.setString(1, playerId.toString())
                statement.setInt(2, limit)
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toPunishment()) } }
            }
        }
    }

    private fun validate(request: CreatePunishment) {
        require(Regex("[A-Za-z0-9_]{1,16}").matches(request.targetName)) { "Target name is invalid" }
        require(request.actorName == "CONSOLE" || Regex("[A-Za-z0-9_]{1,16}").matches(request.actorName)) { "Actor name is invalid" }
        val expiresAt = request.expiresAt
        require(expiresAt == null || expiresAt.isAfter(Instant.now(clock))) { "Expiry must be in the future" }
        require(request.scope == PunishmentScope.NETWORK || !request.server.isNullOrBlank()) { "Server scope requires a server" }
        InputPolicies.PUNISHMENT_REASON.validate(request.reason)
    }

    private fun find(connection: Connection, id: Long): Punishment? = connection.prepareStatement(
        "SELECT * FROM punishments WHERE id = ?",
    ).use { statement ->
        statement.setLong(1, id)
        statement.executeQuery().use { result -> if (result.next()) result.toPunishment() else null }
    }

    private fun requirePunishment(connection: Connection, id: Long): Punishment =
        checkNotNull(find(connection, id)) { "Punishment disappeared during transaction" }

    private fun ResultSet.toPunishment(): Punishment = Punishment(
        id = PunishmentId(getLong("id")),
        type = PunishmentType.valueOf(getString("type")),
        targetId = UUID.fromString(getString("target_uuid")),
        targetName = getString("target_name"),
        actorId = getString("actor_uuid")?.let(UUID::fromString),
        actorName = getString("actor_name"),
        reason = getString("reason"),
        createdAt = Instant.ofEpochMilli(getLong("created_at")),
        expiresAt = getLong("expires_at").takeUnless { wasNull() }?.let(Instant::ofEpochMilli),
        active = getBoolean("active"),
        scope = PunishmentScope.valueOf(getString("scope")),
        server = getString("server_name"),
    )
}
