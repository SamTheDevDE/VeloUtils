// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.core.chat

import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

public data class ChatPolicyConfig(
    val cooldown: Duration,
    val duplicateWindow: Duration,
    val maximumLength: Int,
    val capsMinimumLength: Int,
    val capsMaximumRatio: Double,
    val literalFilters: List<String>,
    val regexFilters: List<Regex>,
)

public sealed interface ChatDecision {
    public data class Accepted(val message: String) : ChatDecision
    public data class Rejected(val reason: Reason, val retryAfter: Duration? = null) : ChatDecision
    public enum class Reason { BLANK, TOO_LONG, COOLDOWN, DUPLICATE }
}

/** Thread-safe, allocation-bounded policy state: at most one entry per player that has chatted. */
public class ChatPolicy(private val config: ChatPolicyConfig, private val nanoTime: () -> Long = System::nanoTime) {
    private data class State(val acceptedAtNanos: Long, val normalized: String)
    private val states = ConcurrentHashMap<UUID, State>()

    init {
        require(!config.cooldown.isNegative)
        require(!config.duplicateWindow.isNegative)
        require(config.maximumLength in 1..2_048)
        require(config.capsMinimumLength >= 1)
        require(config.capsMaximumRatio in 0.0..1.0)
        require(config.literalFilters.none(String::isBlank))
    }

    public fun evaluate(playerId: UUID, input: String, bypassCooldown: Boolean = false): ChatDecision {
        var message = input.trim()
        if (message.isEmpty()) return ChatDecision.Rejected(ChatDecision.Reason.BLANK)
        if (message.length > config.maximumLength) return ChatDecision.Rejected(ChatDecision.Reason.TOO_LONG)
        val now = nanoTime()
        val normalized = message.lowercase().replace(WHITESPACE, " ")
        val previous = states[playerId]
        if (!bypassCooldown && previous != null) {
            val elapsed = Duration.ofNanos((now - previous.acceptedAtNanos).coerceAtLeast(0))
            if (elapsed < config.cooldown) return ChatDecision.Rejected(ChatDecision.Reason.COOLDOWN, config.cooldown - elapsed)
            if (normalized == previous.normalized && elapsed < config.duplicateWindow) {
                return ChatDecision.Rejected(ChatDecision.Reason.DUPLICATE)
            }
        }
        val letters = message.count(Char::isLetter)
        val uppercase = message.count(Char::isUpperCase)
        if (letters >= config.capsMinimumLength && uppercase.toDouble() / letters > config.capsMaximumRatio) {
            message = message.lowercase()
        }
        config.literalFilters.forEach { blocked -> message = message.replace(blocked, "***", ignoreCase = true) }
        config.regexFilters.forEach { filter -> message = filter.replace(message, "***") }
        states[playerId] = State(now, normalized)
        return ChatDecision.Accepted(message)
    }

    public fun remove(playerId: UUID) { states.remove(playerId) }
    public fun clear() { states.clear() }

    private companion object {
        val WHITESPACE: Regex = Regex("\\s+")
    }
}
