package de.samthedev.veloutils.proxy.storage

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchemaMigrationsTest {
    @Test
    fun `database dialects declare their packaged drivers`() {
        assertEquals("org.sqlite.JDBC", DatabaseDialect.SQLITE.driverClassName)
        assertEquals("com.mysql.cj.jdbc.Driver", DatabaseDialect.MYSQL.driverClassName)
        assertEquals("org.postgresql.Driver", DatabaseDialect.POSTGRESQL.driverClassName)
    }

    @Test fun `sqlite migrations are repeatable`() = runBlocking {
        val database = Files.createTempFile("veloutils-test", ".db")
        val storage = JdbcStorageProvider("jdbc:sqlite:$database", null, null, DatabaseDialect.SQLITE, 1)
        try {
            storage.initialize()
            storage.initialize()
            val tables = storage.read { connection ->
                connection.prepareStatement("SELECT name FROM sqlite_master WHERE type='table'").use { statement ->
                    statement.executeQuery().use { result -> buildSet { while (result.next()) add(result.getString(1)) } }
                }
            }
            assertTrue("reports" in tables)
            assertTrue("punishments" in tables)
            assertEquals(2, storage.read { it.createStatement().executeQuery("SELECT COUNT(*) FROM veloutils_schema").use { rs -> rs.next(); rs.getInt(1) } })
            storage.transaction { connection -> connection.createStatement().use { it.executeUpdate("DELETE FROM veloutils_schema WHERE version = 2") } }
            storage.initialize()
            assertEquals(2, storage.read { it.createStatement().executeQuery("SELECT COUNT(*) FROM veloutils_schema").use { rs -> rs.next(); rs.getInt(1) } })
        } finally {
            storage.close()
            Files.deleteIfExists(database)
        }
    }
}
