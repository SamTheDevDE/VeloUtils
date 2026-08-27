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
            migration.statements.forEach { sql -> executeMigrationStatement(connection, sql) }
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

    private fun executeMigrationStatement(connection: Connection, sql: String) {
        val addColumn = Regex("ALTER TABLE ([A-Za-z0-9_]+) ADD COLUMN ([A-Za-z0-9_]+)", RegexOption.IGNORE_CASE)
            .find(sql)
        if (addColumn != null && columnExists(connection, addColumn.groupValues[1], addColumn.groupValues[2])) return

        val createIndex = Regex(
            "CREATE INDEX(?: IF NOT EXISTS)? ([A-Za-z0-9_]+) ON ([A-Za-z0-9_]+)",
            RegexOption.IGNORE_CASE,
        ).find(sql)
        if (createIndex != null) {
            if (indexExists(connection, createIndex.groupValues[2], createIndex.groupValues[1])) return
            val portableSql = sql.replace(Regex("CREATE INDEX IF NOT EXISTS", RegexOption.IGNORE_CASE), "CREATE INDEX")
            connection.createStatement().use { it.execute(portableSql) }
            return
        }
        connection.createStatement().use { it.execute(sql) }
    }

    private fun columnExists(connection: Connection, table: String, column: String): Boolean =
        connection.prepareStatement("SELECT * FROM $table WHERE 1 = 0").use { statement ->
            statement.executeQuery().use { result ->
                (1..result.metaData.columnCount).any { result.metaData.getColumnName(it).equals(column, true) }
            }
        }

    private fun indexExists(connection: Connection, table: String, index: String): Boolean =
        connection.metaData.getIndexInfo(connection.catalog, null, table, false, false).use { result ->
            while (result.next()) {
                if (result.getString("INDEX_NAME")?.equals(index, true) == true) return@use true
            }
            false
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
