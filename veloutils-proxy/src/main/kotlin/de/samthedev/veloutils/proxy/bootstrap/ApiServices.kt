// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.bootstrap

import de.samthedev.veloutils.api.AccessDecision
import de.samthedev.veloutils.api.CreatePunishment
import de.samthedev.veloutils.api.CreateReport
import de.samthedev.veloutils.api.MaintenanceService
import de.samthedev.veloutils.api.MaintenanceSnapshot
import de.samthedev.veloutils.api.MaintenanceUpdate
import de.samthedev.veloutils.api.ModerationService
import de.samthedev.veloutils.api.NetworkService
import de.samthedev.veloutils.api.Punishment
import de.samthedev.veloutils.api.PunishmentId
import de.samthedev.veloutils.api.Report
import de.samthedev.veloutils.api.ReportId
import de.samthedev.veloutils.api.ReportService
import de.samthedev.veloutils.api.StaffMemberSnapshot
import de.samthedev.veloutils.api.StaffService
import de.samthedev.veloutils.api.StaffSessionSnapshot
import de.samthedev.veloutils.api.VeloUtilsApi
import java.net.InetAddress
import java.time.Duration
import java.time.Instant
import java.util.UUID

internal data class ApiServices(
    override val network: NetworkService,
    override val maintenance: MaintenanceService,
    override val staff: StaffService,
    override val reports: ReportService,
    override val moderation: ModerationService,
) : VeloUtilsApi

internal class DisabledMaintenanceService : MaintenanceService {
    override fun snapshot(): MaintenanceSnapshot = MaintenanceSnapshot(null, emptyMap(), emptySet())
    override fun access(playerId: UUID, permissions: Set<String>, server: String?): AccessDecision = AccessDecision.Allowed
    override suspend fun update(request: MaintenanceUpdate): MaintenanceSnapshot = disabled("maintenance")
}

internal class DisabledStaffService : StaffService {
    override fun onlineStaff(): List<StaffMemberSnapshot> = emptyList()
    override fun session(playerId: UUID): StaffSessionSnapshot? = null
    override suspend fun trackedTime(playerId: UUID, from: Instant, until: Instant): Duration = Duration.ZERO
}

internal class DisabledReportService : ReportService {
    override suspend fun create(request: CreateReport): Report = disabled("reports")
    override suspend fun find(id: ReportId): Report? = null
    override suspend fun history(subject: UUID, limit: Int): List<Report> = emptyList()
    override suspend fun claim(id: ReportId, staffId: UUID, staffName: String): Report = disabled("reports")
    override suspend fun close(id: ReportId, actorId: UUID?, resolution: String): Report = disabled("reports")
}

internal class DisabledModerationService : ModerationService {
    override suspend fun punish(request: CreatePunishment): Punishment = disabled("moderation")
    override suspend fun revoke(id: PunishmentId, actorId: UUID?, reason: String): Punishment = disabled("moderation")
    override suspend fun activeFor(playerId: UUID, address: InetAddress?): List<Punishment> = emptyList()
    override suspend fun history(playerId: UUID, limit: Int): List<Punishment> = emptyList()
}

private fun disabled(module: String): Nothing = throw IllegalStateException("The VeloUtils $module module is disabled")
