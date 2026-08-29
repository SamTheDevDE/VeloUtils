// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.core.chat

import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ChatPolicyTest {
    private fun config() = ChatPolicyConfig(
        Duration.ofSeconds(2), Duration.ofSeconds(10), 100, 5, 0.7,
        listOf("blocked"), listOf(Regex("bad\\d+", RegexOption.IGNORE_CASE)),
    )

    @Test
    fun `cooldown and duplicate state are enforced without unbounded history`() {
        var time = 0L
        val policy = ChatPolicy(config()) { time }
        val player = UUID.randomUUID()
        assertIs<ChatDecision.Accepted>(policy.evaluate(player, "hello"))
        time += Duration.ofSeconds(1).toNanos()
        assertEquals(ChatDecision.Reason.COOLDOWN, assertIs<ChatDecision.Rejected>(policy.evaluate(player, "other")).reason)
        time += Duration.ofSeconds(2).toNanos()
        assertEquals(ChatDecision.Reason.DUPLICATE, assertIs<ChatDecision.Rejected>(policy.evaluate(player, " hello ")).reason)
    }

    @Test
    fun `caps and optional filters transform accepted plain text`() {
        val policy = ChatPolicy(config())
        val result = assertIs<ChatDecision.Accepted>(policy.evaluate(UUID.randomUUID(), "BLOCKED BAD123 MESSAGE"))
        assertEquals("*** *** message", result.message)
    }
}
