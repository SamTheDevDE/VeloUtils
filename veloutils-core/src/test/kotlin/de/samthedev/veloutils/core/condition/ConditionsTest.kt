// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.core.condition

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConditionsTest {
    @Test
    fun `basic and advanced selectors compile together`() {
        val condition = BasicSelectors(servers = setOf("lobby"), groups = setOf("owner"))
            .compile("permission('veloutils.staff') && world != 'events'")

        assertTrue(condition.matches(SelectionContext("Lobby", "spawn", "Owner", setOf("veloutils.staff"))))
        assertFalse(condition.matches(SelectionContext("Lobby", "events", "Owner", setOf("veloutils.staff"))))
        assertFalse(condition.matches(SelectionContext("survival", "spawn", "Owner", setOf("veloutils.staff"))))
    }

    @Test
    fun `logical operators and parentheses are supported`() {
        val condition = ConditionParser.parse("server == 'lobby' || (group == 'admin' && !permission('hidden'))")
        assertTrue(condition.matches(SelectionContext(server = "lobby")))
        assertTrue(condition.matches(SelectionContext(group = "admin")))
        assertFalse(condition.matches(SelectionContext(group = "admin", permissions = setOf("hidden"))))
    }
}
