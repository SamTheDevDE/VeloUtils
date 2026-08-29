// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.storage

import java.sql.Connection

public interface StorageProvider : AutoCloseable {
    public suspend fun initialize()
    public suspend fun <T> read(block: (Connection) -> T): T
    public suspend fun <T> transaction(block: (Connection) -> T): T
    override fun close()
}

public enum class DatabaseDialect(public val driverClassName: String) {
    SQLITE("org.sqlite.JDBC"),
    MYSQL("com.mysql.cj.jdbc.Driver"),
    POSTGRESQL("org.postgresql.Driver"),
}

public data class Migration(val version: Int, val statements: List<String>)

public object SchemaMigrations {
    public fun forDialect(dialect: DatabaseDialect): List<Migration> {
        val identity = when (dialect) {
            DatabaseDialect.SQLITE -> "INTEGER PRIMARY KEY AUTOINCREMENT"
            DatabaseDialect.MYSQL -> "BIGINT PRIMARY KEY AUTO_INCREMENT"
            DatabaseDialect.POSTGRESQL -> "BIGSERIAL PRIMARY KEY"
        }
        val boolean = when (dialect) {
            DatabaseDialect.SQLITE -> "INTEGER"
            else -> "BOOLEAN"
        }
        return listOf(Migration(1, listOf(
            """CREATE TABLE IF NOT EXISTS player_identities (
                player_uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(16) NOT NULL,
                first_seen BIGINT NOT NULL, last_seen BIGINT NOT NULL, ip_hash VARCHAR(128)
            )""".trimIndent(),
            """CREATE TABLE IF NOT EXISTS reports (
                id $identity, type VARCHAR(16) NOT NULL, reporter_uuid VARCHAR(36) NOT NULL,
                reporter_name VARCHAR(16) NOT NULL, target_uuid VARCHAR(36), target_name VARCHAR(16),
                reason VARCHAR(1024) NOT NULL, created_at BIGINT NOT NULL, server_name VARCHAR(64),
                status VARCHAR(16) NOT NULL, assigned_staff_uuid VARCHAR(36), assigned_staff_name VARCHAR(16),
                resolution VARCHAR(2048), closed_at BIGINT
            )""".trimIndent(),
            "CREATE INDEX IF NOT EXISTS idx_reports_target ON reports(target_uuid, created_at)",
            "CREATE INDEX IF NOT EXISTS idx_reports_status ON reports(status, created_at)",
            """CREATE TABLE IF NOT EXISTS punishments (
                id $identity, type VARCHAR(16) NOT NULL, target_uuid VARCHAR(36) NOT NULL,
                target_name VARCHAR(16) NOT NULL, actor_uuid VARCHAR(36), actor_name VARCHAR(16) NOT NULL,
                reason VARCHAR(1024) NOT NULL, created_at BIGINT NOT NULL, expires_at BIGINT,
                active $boolean NOT NULL, scope VARCHAR(16) NOT NULL, server_name VARCHAR(64), ip_hash VARCHAR(128),
                revoked_at BIGINT, revoked_by_uuid VARCHAR(36), revocation_reason VARCHAR(1024)
            )""".trimIndent(),
            "CREATE INDEX IF NOT EXISTS idx_punishments_target ON punishments(target_uuid, active)",
            "CREATE INDEX IF NOT EXISTS idx_punishments_ip ON punishments(ip_hash, active)",
            """CREATE TABLE IF NOT EXISTS staff_sessions (
                id $identity, player_uuid VARCHAR(36) NOT NULL, started_at BIGINT NOT NULL,
                ended_at BIGINT, duration_seconds BIGINT, server_times_json TEXT NOT NULL
            )""".trimIndent(),
            "CREATE INDEX IF NOT EXISTS idx_staff_sessions_player ON staff_sessions(player_uuid, started_at)",
            """CREATE TABLE IF NOT EXISTS maintenance_state (
                scope VARCHAR(80) PRIMARY KEY, active $boolean NOT NULL, reason VARCHAR(1024) NOT NULL,
                activated_at BIGINT, scheduled_start BIGINT, scheduled_end BIGINT
            )""".trimIndent(),
            """CREATE TABLE IF NOT EXISTS maintenance_allowlist (
                player_uuid VARCHAR(36) PRIMARY KEY, added_at BIGINT NOT NULL, added_by VARCHAR(36)
            )""".trimIndent(),
            """CREATE TABLE IF NOT EXISTS player_preferences (
                player_uuid VARCHAR(36) NOT NULL, preference_key VARCHAR(80) NOT NULL,
                preference_value VARCHAR(512) NOT NULL, PRIMARY KEY(player_uuid, preference_key)
            )""".trimIndent(),
        )), Migration(2, listOf(
            "ALTER TABLE player_identities ADD COLUMN normalized_name VARCHAR(16)",
            "ALTER TABLE player_identities ADD COLUMN last_server VARCHAR(64)",
            "UPDATE player_identities SET normalized_name = LOWER(name) WHERE normalized_name IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_player_identities_name ON player_identities(normalized_name)",
            "CREATE INDEX IF NOT EXISTS idx_punishments_target_created ON punishments(target_uuid, created_at)",
            "CREATE INDEX IF NOT EXISTS idx_reports_status_created ON reports(status, created_at)",
        )), Migration(3, listOf(
            """CREATE TABLE IF NOT EXISTS message_ignores (
                owner_uuid VARCHAR(36) NOT NULL, ignored_uuid VARCHAR(36) NOT NULL,
                ignored_name VARCHAR(16) NOT NULL, created_at BIGINT NOT NULL,
                PRIMARY KEY(owner_uuid, ignored_uuid)
            )""".trimIndent(),
            "CREATE INDEX IF NOT EXISTS idx_message_ignores_owner ON message_ignores(owner_uuid, created_at)",
        )))
    }
}
