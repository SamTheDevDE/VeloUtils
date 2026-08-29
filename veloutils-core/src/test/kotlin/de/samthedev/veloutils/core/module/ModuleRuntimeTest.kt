// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.core.module

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleRuntimeTest {
    @Test
    fun `disabled module never allocates its implementation`() {
        var allocations = 0
        val id = ModuleId("afk")
        val runtime = ModuleRuntime(mapOf(ModuleDescriptor(id) to ModuleFactory { allocations++; object : ManagedModule {} }))

        runtime.start(emptySet())

        assertEquals(0, allocations)
        assertFalse(runtime.isEnabled(id))
        assertFailsWith<IllegalStateException> { runtime.start(emptySet()) }
    }

    @Test
    fun `dependencies enable first and disable last`() {
        val events = mutableListOf<String>()
        fun module(name: String) = ModuleFactory {
            object : ManagedModule {
                override fun validate() { events += "$name.validate" }
                override fun initialize() { events += "$name.initialize" }
                override fun enable() { events += "$name.enable" }
                override fun disable() { events += "$name.disable" }
            }
        }
        val core = ModuleId("core")
        val chat = ModuleId("chat")
        val runtime = ModuleRuntime(
            mapOf(
                ModuleDescriptor(chat, setOf(core)) to module("chat"),
                ModuleDescriptor(core) to module("core"),
            ),
        )

        runtime.start(setOf(chat, core))
        runtime.close()

        assertEquals(
            listOf("core.validate", "core.initialize", "core.enable", "chat.validate", "chat.initialize", "chat.enable", "chat.disable", "core.disable"),
            events,
        )
    }

    @Test
    fun `startup failure rolls back initialized modules`() {
        var coreDisabled = false
        val core = ModuleId("core")
        val broken = ModuleId("broken")
        val runtime = ModuleRuntime(
            mapOf(
                ModuleDescriptor(core) to ModuleFactory { object : ManagedModule { override fun disable() { coreDisabled = true } } },
                ModuleDescriptor(broken, setOf(core)) to ModuleFactory {
                    object : ManagedModule { override fun enable() = error("boom") }
                },
            ),
        )

        assertFailsWith<ModuleStartupException> { runtime.start(setOf(core, broken)) }
        assertTrue(coreDisabled)
        assertFalse(runtime.isEnabled(core))
    }
}
