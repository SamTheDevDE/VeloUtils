// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.util.concurrent.Executors

public class JdbcStorageProvider(
    jdbcUrl: String,
    username: String?,
    password: String?,
    private val dialect: DatabaseDialect,
    poolSize: Int,
) : StorageProvider {
    private val executor = Executors.newFixedThreadPool(poolSize.coerceIn(1, 64)) { runnable ->
        Thread(runnable, "veloutils-storage").apply { isDaemon = true }
    }
    private val dispatcher: ExecutorCoroutineDispatcher = executor.asCoroutineDispatcher()
    private val dataSource = HikariDataSource(HikariConfig().apply {
        this.jdbcUrl = jdbcUrl
        driverClassName = dialect.driverClassName
        this.username = username
        this.password = password
        maximumPoolSize = poolSize.coerceIn(1, 64)
        minimumIdle = 1
        connectionTimeout = 10_000
        validationTimeout = 3_000
        poolName = "VeloUtils"
        isAutoCommit = true
        if (dialect == DatabaseDialect.SQLITE) {
            maximumPoolSize = 1
            connectionInitSql = "PRAGMA foreign_keys=ON"
        }
    })

    override suspend fun initialize(): Unit = withContext(dispatcher) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE IF NOT EXISTS veloutils_schema (version INTEGER PRIMARY KEY, installed_at BIGINT NOT NULL)")
            }
            val installed = connection.prepareStatement("SELECT version FROM veloutils_schema").use { statement ->
                statement.executeQuery().use { result -> buildSet { while (result.next()) add(result.getInt(1)) } }
            }
            SchemaMigrations.forDialect(dialect).filterNot { it.version in installed }.forEach { migration ->
                migrate(connection, migration)
            }
        }
    }

    private fun migrate(connection: Connection, migration: Migration) {
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            migration.statements.forEach { sql -> connection.createStatement().use { it.execute(sql) } }
            connection.prepareStatement("INSERT INTO veloutils_schema(version, installed_at) VALUES (?, ?)").use {
                it.setInt(1, migration.version)
                it.setLong(2, System.currentTimeMillis())
                it.executeUpdate()
            }
            connection.commit()
        } catch (failure: Throwable) {
            connection.rollback()
            throw failure
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }

    override suspend fun <T> read(block: (Connection) -> T): T = withContext(dispatcher) {
        dataSource.connection.use(block)
    }

    override suspend fun <T> transaction(block: (Connection) -> T): T = withContext(dispatcher) {
        dataSource.connection.use { connection ->
            val old = connection.autoCommit
            connection.autoCommit = false
            try {
                block(connection).also { connection.commit() }
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            } finally {
                connection.autoCommit = old
            }
        }
    }

    override fun close() {
        dataSource.close()
        dispatcher.close()
        executor.shutdown()
    }
}
