// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.messaging

import de.samthedev.veloutils.proxy.storage.DatabaseDialect
import de.samthedev.veloutils.proxy.storage.JdbcStorageProvider
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessagingPreferencesRepositoryTest {
    @Test
    fun `ignore relationships persist and can be removed`() = runBlocking {
        val database = Files.createTempFile("veloutils-message-ignores", ".db")
        JdbcStorageProvider("jdbc:sqlite:$database", null, null, DatabaseDialect.SQLITE, 1).use { storage ->
            storage.initialize()
            val repository = MessagingPreferencesRepository(storage)
            val owner = UUID.randomUUID()
            val target = IgnoredPlayer(UUID.randomUUID(), "Target")

            repository.setIgnoring(owner, target, true)
            assertTrue(repository.isIgnoring(owner, target.playerId))
            assertEquals(listOf(target), repository.list(owner))

            repository.setIgnoring(owner, target, false)
            assertFalse(repository.isIgnoring(owner, target.playerId))
            assertTrue(repository.list(owner).isEmpty())
        }
    }
}
