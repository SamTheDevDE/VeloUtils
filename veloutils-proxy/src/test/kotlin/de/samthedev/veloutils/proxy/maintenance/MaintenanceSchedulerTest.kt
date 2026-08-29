// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.maintenance

import de.samthedev.veloutils.proxy.storage.DatabaseDialect
import de.samthedev.veloutils.proxy.storage.JdbcStorageProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.time.Instant
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MaintenanceSchedulerTest {
    @Test
    fun `schedule persists activates and expires`() = runBlocking {
        val database = Files.createTempFile("veloutils-maintenance-schedule", ".db")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val now = Instant.parse("2026-08-28T12:00:00Z")
        JdbcStorageProvider("jdbc:sqlite:$database", null, null, DatabaseDialect.SQLITE, 1).use { storage ->
            storage.initialize()
            val maintenance = PersistentMaintenanceService(storage).also { it.load() }
            val notices = mutableListOf<String>()
            val scheduler = MaintenanceScheduler(storage, maintenance, scope, { notices += it.toString() }) { now }
            scheduler.load()
            scheduler.schedule(ScheduledMaintenance("survival", "Upgrade", now.plusSeconds(60), now.plusSeconds(120)))

            var transfers = 0
            val restored = MaintenanceScheduler(
                storage,
                maintenance,
                scope,
                { notices += it.toString() },
                preTransferBefore = Duration.ofSeconds(30),
                transferSink = { transfers++ },
                clock = { now },
            )
            restored.load()
            assertEquals(1, restored.snapshot().size)
            restored.process(now.plusSeconds(31))
            restored.process(now.plusSeconds(32))
            assertEquals(1, transfers)
            restored.process(now.plusSeconds(61))
            assertEquals("Upgrade", assertNotNull(maintenance.snapshot().servers["survival"]).reason)
            assertFailsWith<IllegalArgumentException> {
                restored.schedule(ScheduledMaintenance("survival", "Replacement", now.plusSeconds(180), null))
            }
            restored.process(now.plusSeconds(121))
            assertTrue("survival" !in maintenance.snapshot().servers)
            assertTrue(notices.isNotEmpty())
            scheduler.close()
            restored.close()
        }
        scope.cancel()
    }
}
