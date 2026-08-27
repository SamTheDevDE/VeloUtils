// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.moderation

import de.samthedev.veloutils.api.CreatePunishment
import de.samthedev.veloutils.api.PunishmentScope
import de.samthedev.veloutils.api.PunishmentType
import de.samthedev.veloutils.proxy.storage.DatabaseDialect
import de.samthedev.veloutils.proxy.storage.JdbcStorageProvider
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistentModerationServiceTest {
    @Test
    fun `ip values are matched by keyed hash and never stored raw`() = runBlocking {
        val database = Files.createTempFile("veloutils-moderation", ".db")
        JdbcStorageProvider("jdbc:sqlite:$database", null, null, DatabaseDialect.SQLITE, 1).use { storage ->
            storage.initialize()
            val now = Instant.parse("2026-08-27T12:00:00Z")
            val target = UUID.randomUUID()
            val address = InetAddress.getByName("192.0.2.10")
            val service = PersistentModerationService(
                storage,
                IpAddressHasher("test-only-key-material-at-least-32-bytes".toByteArray()),
                Clock.fixed(now, ZoneOffset.UTC),
            )
            val punishment = service.punish(
                CreatePunishment(
                    PunishmentType.IP_BAN, target, "Target", null, "CONSOLE", "Abuse", null,
                    PunishmentScope.NETWORK, null, address,
                ),
            )
            assertEquals(listOf(punishment.id), service.activeFor(UUID.randomUUID(), address).map { it.id })
            val stored = storage.read { connection ->
                connection.prepareStatement("SELECT ip_hash FROM punishments WHERE id = ?").use { statement ->
                    statement.setLong(1, punishment.id.value)
                    statement.executeQuery().use { result ->
                        result.next()
                        result.getString(1)
                    }
                }
            }
            assertFalse("192.0.2.10" in stored)
            assertEquals(64, stored.length)
            assertFalse(service.revoke(punishment.id, null, "Appeal accepted").active)
            assertTrue(service.activeFor(target, address).isEmpty())
        }
    }
}
