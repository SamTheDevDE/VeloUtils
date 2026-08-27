// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.report

import de.samthedev.veloutils.api.Report
import de.samthedev.veloutils.api.ReportStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID

public object ReportLifecycle {
    public fun claim(report: Report, staffId: UUID, staffName: String): Report {
        require(report.status == ReportStatus.OPEN) { "Only open reports can be claimed" }
        require(staffName.isNotBlank() && staffName.length <= 16)
        return report.copy(status = ReportStatus.CLAIMED, assignedStaffId = staffId, assignedStaffName = staffName)
    }

    public fun close(report: Report, actorId: UUID?, resolution: String, clock: Clock = Clock.systemUTC()): Report {
        require(report.status != ReportStatus.CLOSED) { "Report is already closed" }
        val normalized = resolution.trim()
        require(normalized.isNotEmpty() && normalized.length <= 2_048) { "Resolution must contain 1..2048 characters" }
        return report.copy(
            status = ReportStatus.CLOSED,
            assignedStaffId = report.assignedStaffId ?: actorId,
            resolution = normalized,
            closedAt = Instant.now(clock),
        )
    }
}

