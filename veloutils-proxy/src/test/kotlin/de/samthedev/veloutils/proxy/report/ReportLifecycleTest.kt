package de.samthedev.veloutils.proxy.report

import de.samthedev.veloutils.api.Report
import de.samthedev.veloutils.api.ReportId
import de.samthedev.veloutils.api.ReportStatus
import de.samthedev.veloutils.api.ReportType
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReportLifecycleTest {
    private fun report() = Report(
        ReportId(1), ReportType.PLAYER, UUID.randomUUID(), "Reporter", UUID.randomUUID(), "Target",
        "reason", Instant.EPOCH, "lobby", ReportStatus.OPEN,
    )

    @Test fun `claim and close preserve audit information`() {
        val staff = UUID.randomUUID()
        val claimed = ReportLifecycle.claim(report(), staff, "Moderator")
        val closed = ReportLifecycle.close(claimed, staff, "Handled", Clock.fixed(Instant.ofEpochSecond(50), ZoneOffset.UTC))
        assertEquals(ReportStatus.CLOSED, closed.status)
        assertEquals(staff, closed.assignedStaffId)
        assertEquals(Instant.ofEpochSecond(50), closed.closedAt)
    }

    @Test fun `closed report cannot be claimed`() {
        val closed = ReportLifecycle.close(report(), null, "done")
        assertFailsWith<IllegalArgumentException> { ReportLifecycle.claim(closed, UUID.randomUUID(), "Staff") }
    }
}
