// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.moderation

import de.samthedev.veloutils.api.CreatePunishment
import de.samthedev.veloutils.api.PunishmentScope
import de.samthedev.veloutils.api.PunishmentId
import de.samthedev.veloutils.api.PunishmentType
import de.samthedev.veloutils.common.PageRequest
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

    @Test
    fun `offline history is paginated and active bans can be revoked by player or id`() = runBlocking {
        val database = Files.createTempFile("veloutils-offline-moderation", ".db")
        JdbcStorageProvider("jdbc:sqlite:$database", null, null, DatabaseDialect.SQLITE, 1).use { storage ->
            storage.initialize()
            val now = Instant.parse("2026-08-27T12:00:00Z")
            val target = UUID.randomUUID()
            val service = PersistentModerationService(storage, null, Clock.fixed(now, ZoneOffset.UTC))
            service.punish(createBan(target, "First"))
            service.punish(createBan(target, "Second"))
            service.punish(createBan(target, "Third"))

            val firstPage = service.historyPage(target, PageRequest(1, 2))
            val secondPage = service.historyPage(target, PageRequest(2, 2))
            assertEquals(3, firstPage.totalItems)
            assertEquals(2, firstPage.items.size)
            assertTrue(firstPage.hasNext)
            assertEquals(1, secondPage.items.size)
            assertTrue(secondPage.hasPrevious)

            val matches = service.activeForTypes(target, setOf(PunishmentType.BAN))
            assertEquals(3, matches.size)
            val playerSelected = matches.first()
            service.revoke(playerSelected.id, null, "Player-oriented unban")
            assertEquals(2, service.activeForTypes(target, setOf(PunishmentType.BAN)).size)
            val idSelected = matches.first { it.id != playerSelected.id }
            assertFalse(service.revoke(PunishmentId(idSelected.id.value), null, "ID unban").active)
            assertFalse(requireNotNull(service.find(playerSelected.id)).punishment.active)
            assertFalse(requireNotNull(service.find(idSelected.id)).punishment.active)
        }
    }

    private fun createBan(target: UUID, reason: String): CreatePunishment = CreatePunishment(
        PunishmentType.BAN,
        target,
        "OfflinePlayer",
        null,
        "CONSOLE",
        reason,
        null,
        PunishmentScope.NETWORK,
        null,
        null,
    )
}
