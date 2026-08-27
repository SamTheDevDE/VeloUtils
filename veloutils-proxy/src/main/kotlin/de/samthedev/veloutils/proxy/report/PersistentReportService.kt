// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.report

import de.samthedev.veloutils.api.CreateReport
import de.samthedev.veloutils.api.Report
import de.samthedev.veloutils.api.ReportId
import de.samthedev.veloutils.api.ReportService
import de.samthedev.veloutils.api.ReportStatus
import de.samthedev.veloutils.api.ReportType
import de.samthedev.veloutils.common.InputPolicies
import de.samthedev.veloutils.proxy.storage.StorageProvider
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.time.Clock
import java.time.Instant
import java.util.UUID

public class PersistentReportService(
    private val storage: StorageProvider,
    private val clock: Clock = Clock.systemUTC(),
) : ReportService {
    override suspend fun create(request: CreateReport): Report {
        validate(request)
        val createdAt = Instant.now(clock)
        return storage.transaction { connection ->
            val id = connection.prepareStatement(
                """INSERT INTO reports(type, reporter_uuid, reporter_name, target_uuid, target_name, reason,
                    created_at, server_name, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
                Statement.RETURN_GENERATED_KEYS,
            ).use { statement ->
                statement.setString(1, request.type.name)
                statement.setString(2, request.reporterId.toString())
                statement.setString(3, request.reporterName)
                statement.setString(4, request.targetId?.toString())
                statement.setString(5, request.targetName)
                statement.setString(6, InputPolicies.REPORT_REASON.validate(request.reason))
                statement.setLong(7, createdAt.toEpochMilli())
                statement.setString(8, request.server?.lowercase())
                statement.setString(9, ReportStatus.OPEN.name)
                check(statement.executeUpdate() == 1) { "Report was not inserted" }
                statement.generatedKeys.use { keys -> check(keys.next()) { "Database did not return a report id" }; keys.getLong(1) }
            }
            requireReport(connection, id)
        }
    }

    override suspend fun find(id: ReportId): Report? = storage.read { connection -> find(connection, id.value) }

    override suspend fun history(subject: UUID, limit: Int): List<Report> {
        require(limit in 1..100)
        return storage.read { connection ->
            connection.prepareStatement(
                "SELECT * FROM reports WHERE reporter_uuid = ? OR target_uuid = ? ORDER BY created_at DESC LIMIT ?",
            ).use { statement ->
                statement.setString(1, subject.toString())
                statement.setString(2, subject.toString())
                statement.setInt(3, limit)
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toReport()) } }
            }
        }
    }

    override suspend fun claim(id: ReportId, staffId: UUID, staffName: String): Report {
        require(validName(staffName)) { "Staff name is invalid" }
        return storage.transaction { connection ->
            connection.prepareStatement(
                "UPDATE reports SET status = ?, assigned_staff_uuid = ?, assigned_staff_name = ? WHERE id = ? AND status = ?",
            ).use { statement ->
                statement.setString(1, ReportStatus.CLAIMED.name)
                statement.setString(2, staffId.toString())
                statement.setString(3, staffName)
                statement.setLong(4, id.value)
                statement.setString(5, ReportStatus.OPEN.name)
                check(statement.executeUpdate() == 1) { "Report does not exist or is no longer open" }
            }
            requireReport(connection, id.value)
        }
    }

    override suspend fun close(id: ReportId, actorId: UUID?, resolution: String): Report {
        val validated = InputPolicies.HELP_REQUEST.validate(resolution)
        return storage.transaction { connection ->
            connection.prepareStatement(
                """UPDATE reports SET status = ?, assigned_staff_uuid = COALESCE(assigned_staff_uuid, ?),
                    resolution = ?, closed_at = ? WHERE id = ? AND status <> ?""".trimIndent(),
            ).use { statement ->
                statement.setString(1, ReportStatus.CLOSED.name)
                statement.setString(2, actorId?.toString())
                statement.setString(3, validated)
                statement.setLong(4, clock.millis())
                statement.setLong(5, id.value)
                statement.setString(6, ReportStatus.CLOSED.name)
                check(statement.executeUpdate() == 1) { "Report does not exist or is already closed" }
            }
            requireReport(connection, id.value)
        }
    }

    private fun validate(request: CreateReport) {
        require(validName(request.reporterName)) { "Reporter name is invalid" }
        val server = request.server
        require(server == null || server.length in 1..64) { "Server name is invalid" }
        when (request.type) {
            ReportType.PLAYER -> require(request.targetId != null && request.targetName?.let(::validName) == true) {
                "Player reports require a valid target"
            }
            ReportType.HELPOP -> require(request.targetId == null && request.targetName == null) { "Help requests cannot have a target" }
        }
        InputPolicies.REPORT_REASON.validate(request.reason)
    }

    private fun find(connection: Connection, id: Long): Report? = connection.prepareStatement(
        "SELECT * FROM reports WHERE id = ?",
    ).use { statement ->
        statement.setLong(1, id)
        statement.executeQuery().use { result -> if (result.next()) result.toReport() else null }
    }

    private fun requireReport(connection: Connection, id: Long): Report =
        checkNotNull(find(connection, id)) { "Report disappeared during transaction" }

    private fun ResultSet.toReport(): Report = Report(
        id = ReportId(getLong("id")),
        type = ReportType.valueOf(getString("type")),
        reporterId = UUID.fromString(getString("reporter_uuid")),
        reporterName = getString("reporter_name"),
        targetId = getString("target_uuid")?.let(UUID::fromString),
        targetName = getString("target_name"),
        reason = getString("reason"),
        createdAt = Instant.ofEpochMilli(getLong("created_at")),
        server = getString("server_name"),
        status = ReportStatus.valueOf(getString("status")),
        assignedStaffId = getString("assigned_staff_uuid")?.let(UUID::fromString),
        assignedStaffName = getString("assigned_staff_name"),
        resolution = getString("resolution"),
        closedAt = getLong("closed_at").takeUnless { wasNull() }?.let(Instant::ofEpochMilli),
    )

    private fun validName(name: String): Boolean = Regex("[A-Za-z0-9_]{1,16}").matches(name)
}
