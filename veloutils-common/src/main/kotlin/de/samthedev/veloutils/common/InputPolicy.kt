// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.common

public data class InputPolicy(
    val label: String,
    val maximumLength: Int,
    val allowNewLines: Boolean = false,
) {
    init {
        require(label.isNotBlank())
        require(maximumLength in 1..32_767)
    }

    public fun validate(value: String): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "$label must not be blank" }
        require(normalized.length <= maximumLength) { "$label exceeds $maximumLength characters" }
        require(allowNewLines || ('\n' !in normalized && '\r' !in normalized)) { "$label must be one line" }
        require(normalized.none { it == '\u0000' || (it.isISOControl() && it != '\t') }) {
            "$label contains control characters"
        }
        return normalized
    }
}

public object InputPolicies {
    public val CHAT: InputPolicy = InputPolicy("Chat message", 512)
    public val ALERT: InputPolicy = InputPolicy("Alert", 1_024)
    public val REPORT_REASON: InputPolicy = InputPolicy("Report reason", 1_024)
    public val HELP_REQUEST: InputPolicy = InputPolicy("Help request", 1_024)
    public val PUNISHMENT_REASON: InputPolicy = InputPolicy("Punishment reason", 1_024)
    public val COMMAND: InputPolicy = InputPolicy("Backend command", 2_048)
}

/** Escapes untrusted input so MiniMessage treats it as literal text. */
public fun escapeMiniMessage(input: String): String = buildString(input.length) {
    input.forEach { character ->
        when (character) {
            '\\', '<' -> append('\\').append(character)
            else -> append(character)
        }
    }
}

