// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.report

import de.samthedev.veloutils.api.CreateReport
import de.samthedev.veloutils.api.ReportStatus
import de.samthedev.veloutils.api.ReportType
import de.samthedev.veloutils.proxy.storage.DatabaseDialect
import de.samthedev.veloutils.proxy.storage.JdbcStorageProvider
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PersistentReportServiceTest {
    @Test
    fun `report lifecycle is transactional and persistent`() = runBlocking {
        val database = Files.createTempFile("veloutils-reports", ".db")
        JdbcStorageProvider("jdbc:sqlite:$database", null, null, DatabaseDialect.SQLITE, 1).use { storage ->
            storage.initialize()
            val clock = Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC)
            val service = PersistentReportService(storage, clock)
            val reporter = UUID.randomUUID()
            val target = UUID.randomUUID()
            val staff = UUID.randomUUID()
            val created = service.create(CreateReport(ReportType.PLAYER, reporter, "Reporter", target, "Target", "Cheating", "survival"))
            assertEquals(ReportStatus.OPEN, created.status)
            assertEquals(ReportStatus.CLAIMED, service.claim(created.id, staff, "Moderator").status)
            assertEquals(ReportStatus.CLOSED, service.close(created.id, staff, "Reviewed evidence").status)
            assertEquals(listOf(created.id), service.history(target).map { it.id })
            assertFailsWith<IllegalStateException> { service.claim(created.id, staff, "Moderator") }
        }
    }
}
