// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.core.module

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@JvmInline
public value class ModuleId(public val value: String) {
    init {
        require(value.matches(Regex("[a-z][a-z0-9-]{0,63}"))) { "Invalid module id: $value" }
    }

    override fun toString(): String = value
}

public enum class ModuleState {
    DISCOVERED,
    VALIDATED,
    INITIALIZED,
    ENABLED,
    DISABLED,
    FAILED,
}

public data class ModuleDescriptor(
    val id: ModuleId,
    val dependencies: Set<ModuleId> = emptySet(),
    val reloadable: Boolean = false,
)

/** A module instance is constructed only after its descriptor has been selected for startup. */
public interface ManagedModule {
    public fun validate() {}
    public fun initialize() {}
    public fun enable() {}
    public fun reload() {}
    public fun disable() {}
}

public fun interface ModuleFactory {
    public fun create(): ManagedModule
}

/** Adapts one owned listener/task/integration bundle to the lifecycle without exposing it as a public service. */
public class ResourceModule(private val acquire: () -> AutoCloseable) : ManagedModule {
    private var resource: AutoCloseable? = null

    override fun enable() {
        check(resource == null) { "Resource module is already enabled" }
        resource = acquire()
    }

    override fun disable() {
        resource?.close()
        resource = null
    }
}

public data class ModuleSnapshot(val id: ModuleId, val state: ModuleState, val detail: String? = null)

/**
 * Owns internal module lifecycles. Disabled modules never invoke their factory, which prevents their listeners,
 * commands, tasks, caches, repositories, and integration hooks from being allocated accidentally.
 */
public class ModuleRuntime(registrations: Map<ModuleDescriptor, ModuleFactory>) : AutoCloseable {
    private data class Entry(
        val descriptor: ModuleDescriptor,
        val factory: ModuleFactory,
        var state: ModuleState = ModuleState.DISCOVERED,
        var instance: ManagedModule? = null,
        var detail: String? = null,
    )

    private val lock = ReentrantLock()
    private val entries: Map<ModuleId, Entry>
    private var enableOrder: List<ModuleId> = emptyList()
    private var started: Boolean = false

    init {
        val duplicate = registrations.keys.groupingBy(ModuleDescriptor::id).eachCount().entries.firstOrNull { it.value > 1 }
        require(duplicate == null) { "Duplicate module id: ${duplicate?.key}" }
        entries = registrations.entries.associate { (descriptor, factory) -> descriptor.id to Entry(descriptor, factory) }
        entries.values.forEach { entry ->
            val missing = entry.descriptor.dependencies - entries.keys
            require(missing.isEmpty()) { "Module ${entry.descriptor.id} has unknown dependencies: $missing" }
        }
    }

    public fun start(enabled: Set<ModuleId>) {
        lock.withLock {
            check(!started) { "Module runtime is already started" }
            val unknown = enabled - entries.keys
            require(unknown.isEmpty()) { "Unknown enabled modules: $unknown" }
            enabled.forEach { id ->
                val missing = checkNotNull(entries[id]).descriptor.dependencies - enabled
                require(missing.isEmpty()) { "Module $id requires enabled modules: $missing" }
            }
            val order = topologicalOrder(enabled)
            started = true
            val progressed = mutableListOf<Entry>()
            try {
                order.forEach { id ->
                    val entry = checkNotNull(entries[id])
                    val instance = entry.factory.create()
                    entry.instance = instance
                    progressed += entry
                    instance.validate()
                    entry.state = ModuleState.VALIDATED
                    instance.initialize()
                    entry.state = ModuleState.INITIALIZED
                    instance.enable()
                    entry.state = ModuleState.ENABLED
                }
                enableOrder = order
                entries.filterKeys { it !in enabled }.values.forEach { it.state = ModuleState.DISABLED }
            } catch (failure: Exception) {
                val failed = progressed.lastOrNull()
                failed?.state = ModuleState.FAILED
                failed?.detail = failure.message ?: failure::class.simpleName
                progressed.asReversed().forEach { entry -> runCatching { entry.instance?.disable() } }
                progressed.filterNot { it === failed }.forEach { it.state = ModuleState.DISABLED }
                enableOrder = emptyList()
                started = false
                throw ModuleStartupException(failed?.descriptor?.id, failure)
            }
        }
    }

    public fun reload(id: ModuleId) {
        lock.withLock {
            val entry = requireNotNull(entries[id]) { "Unknown module: $id" }
            check(entry.state == ModuleState.ENABLED) { "Module $id is not enabled" }
            check(entry.descriptor.reloadable) { "Module $id does not support reload" }
            checkNotNull(entry.instance).reload()
        }
    }

    public fun snapshots(): List<ModuleSnapshot> = lock.withLock {
        entries.values.map { ModuleSnapshot(it.descriptor.id, it.state, it.detail) }.sortedBy { it.id.value }
    }

    public fun isEnabled(id: ModuleId): Boolean = lock.withLock { entries[id]?.state == ModuleState.ENABLED }

    override fun close() {
        lock.withLock {
            if (!started) return
            enableOrder.asReversed().forEach { id ->
                val entry = checkNotNull(entries[id])
                runCatching { entry.instance?.disable() }
                    .onFailure { entry.detail = it.message ?: it::class.simpleName }
                entry.instance = null
                entry.state = ModuleState.DISABLED
            }
            enableOrder = emptyList()
            started = false
        }
    }

    private fun topologicalOrder(enabled: Set<ModuleId>): List<ModuleId> {
        val permanent = mutableSetOf<ModuleId>()
        val temporary = mutableSetOf<ModuleId>()
        val result = mutableListOf<ModuleId>()
        fun visit(id: ModuleId) {
            if (id in permanent) return
            require(temporary.add(id)) { "Cyclic module dependency involving $id" }
            checkNotNull(entries[id]).descriptor.dependencies.sortedBy(ModuleId::value).forEach(::visit)
            temporary.remove(id)
            permanent += id
            result += id
        }
        enabled.sortedBy(ModuleId::value).forEach(::visit)
        return result
    }
}

public class ModuleStartupException(public val module: ModuleId?, cause: Throwable) :
    IllegalStateException("Module ${module ?: "unknown"} failed to start: ${cause.message}", cause)
