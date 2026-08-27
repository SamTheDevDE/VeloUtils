// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.maintenance

import de.samthedev.veloutils.api.MaintenanceUpdate
import de.samthedev.veloutils.proxy.storage.DatabaseDialect
import de.samthedev.veloutils.proxy.storage.JdbcStorageProvider
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PersistentMaintenanceServiceTest {
    @Test
    fun `maintenance state survives a storage restart`() = runBlocking {
        val database = Files.createTempFile("veloutils-maintenance", ".db")
        val playerId = UUID.randomUUID()
        val activatedAt = Instant.parse("2026-08-27T12:00:00Z")

        JdbcStorageProvider("jdbc:sqlite:$database", null, null, DatabaseDialect.SQLITE, 1).use { first ->
            first.initialize()
            val service = PersistentMaintenanceService(first)
            service.load()
            service.update(MaintenanceUpdate.Enable("survival", "Upgrade", activatedAt))
            service.update(MaintenanceUpdate.Allow(playerId))
        }

        JdbcStorageProvider("jdbc:sqlite:$database", null, null, DatabaseDialect.SQLITE, 1).use { second ->
            second.initialize()
            val restored = PersistentMaintenanceService(second)
            restored.load()
            assertEquals("Upgrade", assertNotNull(restored.snapshot().servers["survival"]).reason)
            assertTrue(playerId in restored.snapshot().allowedPlayers)
        }
    }
}
