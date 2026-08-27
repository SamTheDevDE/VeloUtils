// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.common

/** Validates the executable name and shape of an explicitly authorized backend command. */
public class RemoteCommandPolicy(
    private val enabled: Boolean,
    allowlist: Set<String>,
) {
    private val allowedRoots = allowlist.mapTo(mutableSetOf()) { it.lowercase().removePrefix("/") }

    public fun validate(command: String): String {
        check(enabled) { "Remote command execution is disabled" }
        val normalized = InputPolicies.COMMAND.validate(command).removePrefix("/")
        require('\n' !in normalized && '\r' !in normalized) { "Command must be one line" }
        val root = normalized.substringBefore(' ').lowercase()
        require(root in allowedRoots) { "Command root is not authorized" }
        return normalized
    }
}
