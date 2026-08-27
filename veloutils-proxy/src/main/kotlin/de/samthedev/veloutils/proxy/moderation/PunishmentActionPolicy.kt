// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.moderation

import de.samthedev.veloutils.api.PunishmentType

public enum class PunishmentAction { UNBAN, UNMUTE, HISTORY, CHECK_BAN }

public object PunishmentActionPolicy {
    public fun available(
        type: PunishmentType,
        effective: Boolean,
        canUnban: Boolean,
        canUnmute: Boolean,
        canViewHistory: Boolean,
        canCheckBan: Boolean,
    ): Set<PunishmentAction> = buildSet {
        if (effective && type in setOf(PunishmentType.BAN, PunishmentType.IP_BAN) && canUnban) add(PunishmentAction.UNBAN)
        if (effective && type == PunishmentType.MUTE && canUnmute) add(PunishmentAction.UNMUTE)
        if (canViewHistory) add(PunishmentAction.HISTORY)
        if (canCheckBan) add(PunishmentAction.CHECK_BAN)
    }
}
