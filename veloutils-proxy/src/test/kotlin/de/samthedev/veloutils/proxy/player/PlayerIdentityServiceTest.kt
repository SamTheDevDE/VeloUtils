// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.player

import de.samthedev.veloutils.proxy.storage.DatabaseDialect
import de.samthedev.veloutils.proxy.storage.JdbcStorageProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerIdentityServiceTest {
    @Test
    fun `known offline players resolve case insensitively by name and uuid`() = runBlocking {
        val database = Files.createTempFile("veloutils-identities", ".db")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            JdbcStorageProvider("jdbc:sqlite:$database", null, null, DatabaseDialect.SQLITE, 1).use { storage ->
                storage.initialize()
                val id = UUID.randomUUID()
                val service = PlayerIdentityService(
                    storage,
                    scope,
                    Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC),
                )

                service.remember(id, "SamTheDevDE", "lobby")

                assertEquals(id, service.resolve("samthedevde")?.playerId)
                assertEquals("SamTheDevDE", service.resolve(id.toString())?.name)
                assertEquals("lobby", service.resolve("SAMTHEDEVDE")?.lastServer)
                assertEquals(listOf("SamTheDevDE"), service.suggestions("sam"))
            }
        } finally {
            scope.cancel()
        }
    }
}
