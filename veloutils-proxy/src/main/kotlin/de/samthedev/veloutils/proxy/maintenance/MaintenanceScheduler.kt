// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.maintenance

import de.samthedev.veloutils.api.MaintenanceUpdate
import de.samthedev.veloutils.proxy.storage.StorageProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

public data class ScheduledMaintenance(
    val server: String?,
    val reason: String,
    val startsAt: Instant,
    val endsAt: Instant?,
)

public class MaintenanceScheduler(
    private val storage: StorageProvider,
    private val maintenance: PersistentMaintenanceService,
    private val scope: CoroutineScope,
    private val broadcastSink: (Component) -> Unit,
    private val preTransferBefore: Duration? = null,
    private val transferSink: (ScheduledMaintenance) -> Unit = {},
    private val clock: () -> Instant = Instant::now,
) : AutoCloseable {
    private val scheduled = AtomicReference<Map<String, ScheduledMaintenance>>(emptyMap())
    private val notified = ConcurrentHashMap<String, MutableSet<Long>>()
    private val transferred = ConcurrentHashMap.newKeySet<String>()
    private var job: Job? = null

    public suspend fun load() {
        val loaded = storage.read { connection ->
            connection.prepareStatement(
                "SELECT scope, reason, scheduled_start, scheduled_end FROM maintenance_state WHERE active = ? AND scheduled_start IS NOT NULL",
            ).use { statement ->
                statement.setBoolean(1, false)
                statement.executeQuery().use { result ->
                    buildMap {
                        while (result.next()) {
                            val scope = result.getString("scope")
                            val endValue = result.getLong("scheduled_end")
                            val end = if (result.wasNull()) null else Instant.ofEpochMilli(endValue)
                            put(
                                scope,
                                ScheduledMaintenance(
                                    scope.takeUnless { it == GLOBAL_SCOPE },
                                    result.getString("reason"),
                                    Instant.ofEpochMilli(result.getLong("scheduled_start")),
                                    end,
                                ),
                            )
                        }
                    }
                }
            }
        }
        scheduled.set(loaded)
    }

    public fun start() {
        check(job == null) { "Maintenance scheduler is already started" }
        job = scope.launch {
            while (isActive) {
                process(clock())
                delay(1_000L)
            }
        }
    }

    public fun snapshot(): List<ScheduledMaintenance> = scheduled.get().values.sortedBy(ScheduledMaintenance::startsAt)

    public suspend fun schedule(request: ScheduledMaintenance) {
        val now = clock()
        require(request.startsAt.isAfter(now)) { "Scheduled start must be in the future" }
        require(request.endsAt == null || request.endsAt.isAfter(request.startsAt)) { "Scheduled end must follow the start" }
        require(request.reason.isNotBlank() && request.reason.length <= 1_024)
        val scope = request.server?.lowercase() ?: GLOBAL_SCOPE
        val active = maintenance.snapshot()
        require(if (request.server == null) active.global == null else request.server.lowercase() !in active.servers) {
            "Disable active maintenance for this scope before replacing it with a schedule"
        }
        val normalized = request.copy(server = request.server?.lowercase(), reason = request.reason.trim())
        storage.transaction { connection ->
            connection.prepareStatement("DELETE FROM maintenance_state WHERE scope = ?").use {
                it.setString(1, scope)
                it.executeUpdate()
            }
            connection.prepareStatement(
                "INSERT INTO maintenance_state(scope, active, reason, activated_at, scheduled_start, scheduled_end) VALUES (?, ?, ?, ?, ?, ?)",
            ).use {
                it.setString(1, scope)
                it.setBoolean(2, false)
                it.setString(3, normalized.reason)
                it.setObject(4, null)
                it.setLong(5, normalized.startsAt.toEpochMilli())
                normalized.endsAt?.let { end -> it.setLong(6, end.toEpochMilli()) } ?: it.setObject(6, null)
                it.executeUpdate()
            }
        }
        scheduled.updateAndGet { it + (scope to normalized) }
        notified.remove(scope)
        transferred.remove(scope)
    }

    public suspend fun cancel(server: String?): Boolean {
        val scope = server?.lowercase() ?: GLOBAL_SCOPE
        if (scope !in scheduled.get()) return false
        storage.transaction { connection ->
            connection.prepareStatement("DELETE FROM maintenance_state WHERE scope = ? AND active = ?").use {
                it.setString(1, scope)
                it.setBoolean(2, false)
                it.executeUpdate()
            }
        }
        scheduled.updateAndGet { it - scope }
        notified.remove(scope)
        transferred.remove(scope)
        return true
    }

    public suspend fun process(now: Instant): List<MaintenanceUpdate> {
        val changes = mutableListOf<MaintenanceUpdate>()
        scheduled.get().forEach { (scope, request) ->
            val remaining = Duration.between(now, request.startsAt)
            if (!remaining.isPositive) {
                val update = MaintenanceUpdate.Enable(request.server, request.reason, now, request.endsAt)
                maintenance.update(update)
                scheduled.updateAndGet { it - scope }
                notified.remove(scope)
                broadcast("Maintenance is now active${request.server?.let { " on $it" }.orEmpty()}: ${request.reason}")
                changes += update
            } else {
                notifyCountdown(scope, request, remaining)
                if (preTransferBefore != null && remaining <= preTransferBefore && transferred.add(scope)) {
                    transferSink(request)
                }
            }
        }
        val active = maintenance.snapshot()
        buildList<String?> {
            active.global?.takeIf { it.scheduledEnd?.isAfter(now) == false }?.let { add(null) }
            active.servers.forEach { (server, window) -> if (window.scheduledEnd?.isAfter(now) == false) add(server) }
        }.forEach { server ->
            val update = MaintenanceUpdate.Disable(server)
            maintenance.update(update)
            broadcast("Scheduled maintenance ended${server?.let { " on $it" }.orEmpty()}.")
            changes += update
        }
        return changes
    }

    private fun notifyCountdown(scope: String, request: ScheduledMaintenance, remaining: Duration) {
        val seconds = remaining.seconds
        val threshold = COUNTDOWNS.firstOrNull { seconds in (it - 1)..it } ?: return
        val seen = notified.computeIfAbsent(scope) { ConcurrentHashMap.newKeySet() }
        if (!seen.add(threshold)) return
        broadcast("Maintenance${request.server?.let { " on $it" }.orEmpty()} begins in ${format(threshold)}: ${request.reason}")
    }

    private fun broadcast(text: String) {
        val message = Component.text(text, NamedTextColor.YELLOW)
        broadcastSink(message)
    }

    override fun close() {
        job?.cancel()
        job = null
        notified.clear()
        transferred.clear()
    }

    private companion object {
        const val GLOBAL_SCOPE: String = "global"
        val COUNTDOWNS: List<Long> = listOf(600, 300, 60, 30, 10, 5, 4, 3, 2, 1)
        fun format(seconds: Long): String = if (seconds >= 60) "${seconds / 60}m" else "${seconds}s"
    }
}
