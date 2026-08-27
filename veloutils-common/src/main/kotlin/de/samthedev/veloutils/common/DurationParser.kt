// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.common

import java.time.Duration

public object DurationParser {
    private val token = Regex("(\\d+)([smhdw])", RegexOption.IGNORE_CASE)
    private const val MAX_INPUT = 64

    public fun parse(input: String): Duration {
        val normalized = input.trim().replace(" ", "")
        require(normalized.isNotEmpty()) { "Duration must not be blank" }
        require(normalized.length <= MAX_INPUT) { "Duration exceeds $MAX_INPUT characters" }

        var cursor = 0
        var seconds = 0L
        token.findAll(normalized).forEach { match ->
            require(match.range.first == cursor) { "Invalid duration near '${normalized.substring(cursor)}'" }
            val amount = match.groupValues[1].toLong()
            val multiplier = when (match.groupValues[2].lowercase()) {
                "s" -> 1L
                "m" -> 60L
                "h" -> 3_600L
                "d" -> 86_400L
                "w" -> 604_800L
                else -> error("Unreachable duration unit")
            }
            seconds = Math.addExact(seconds, Math.multiplyExact(amount, multiplier))
            cursor = match.range.last + 1
        }
        require(cursor == normalized.length) { "Invalid duration near '${normalized.substring(cursor)}'" }
        require(seconds > 0) { "Duration must be greater than zero" }
        return Duration.ofSeconds(seconds)
    }

    public fun format(duration: Duration): String {
        require(!duration.isNegative) { "Duration must not be negative" }
        var remaining = duration.seconds
        val units = listOf("w" to 604_800L, "d" to 86_400L, "h" to 3_600L, "m" to 60L, "s" to 1L)
        return buildList {
            units.forEach { (suffix, size) ->
                val count = remaining / size
                if (count > 0) {
                    add("$count$suffix")
                    remaining %= size
                }
            }
        }.joinToString(" ").ifEmpty { "0s" }
    }
}

