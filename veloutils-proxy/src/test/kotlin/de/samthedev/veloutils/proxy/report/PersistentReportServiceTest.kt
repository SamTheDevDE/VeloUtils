// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.report

import de.samthedev.veloutils.api.CreateReport
import de.samthedev.veloutils.api.ReportStatus
import de.samthedev.veloutils.api.ReportType
import de.samthedev.veloutils.common.PageRequest
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `report filters paginate without loading every record`() = runBlocking {
        val database = Files.createTempFile("veloutils-report-pages", ".db")
        JdbcStorageProvider("jdbc:sqlite:$database", null, null, DatabaseDialect.SQLITE, 1).use { storage ->
            storage.initialize()
            val service = PersistentReportService(storage, Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC))
            val reporter = UUID.randomUUID()
            val staff = UUID.randomUUID()
            repeat(6) { index ->
                val report = service.create(CreateReport(ReportType.HELPOP, reporter, "Reporter", null, null, "Request $index", "lobby"))
                if (index == 0) service.claim(report.id, staff, "Moderator")
                if (index == 1) service.close(report.id, staff, "Resolved")
            }

            val first = service.page(ReportFilter.ALL, null, PageRequest(1, 5))
            val second = service.page(ReportFilter.ALL, null, PageRequest(2, 5))
            assertEquals(6, first.totalItems)
            assertEquals(5, first.items.size)
            assertTrue(first.hasNext)
            assertEquals(1, second.items.size)
            assertFalse(second.hasNext)
            assertEquals(1, service.page(ReportFilter.CLAIMED, null, PageRequest(1, 5)).totalItems)
            assertEquals(1, service.page(ReportFilter.CLOSED, null, PageRequest(1, 5)).totalItems)
            assertEquals(2, service.page(ReportFilter.MINE, staff, PageRequest(1, 5)).totalItems)
        }
    }
}
