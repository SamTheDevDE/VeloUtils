// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.permission

import com.velocitypowered.api.command.CommandSource
import de.samthedev.veloutils.common.PermissionDefinition
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

public class PermissionService(
    private val legacyEnabled: Boolean,
    private val warnOnLegacy: Boolean,
    private val logger: Logger,
) {
    private val warnedAliases = ConcurrentHashMap.newKeySet<String>()

    public fun has(source: CommandSource, permission: PermissionDefinition): Boolean {
        if (source.hasPermission(permission.node)) return true
        if (!legacyEnabled) return false
        val alias = permission.legacyAliases.firstOrNull(source::hasPermission) ?: return false
        if (warnOnLegacy && warnedAliases.add(alias)) {
            logger.warn("[VeloUtils] Legacy permission '{}' is in use; migrate to '{}'.", alias, permission.node)
        }
        return true
    }
}
