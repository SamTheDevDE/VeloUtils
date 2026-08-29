// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge.bootstrap

import de.samthedev.veloutils.api.AfkService
import de.samthedev.veloutils.api.MaintenanceService
import de.samthedev.veloutils.api.ModerationService
import de.samthedev.veloutils.api.ModuleAvailability
import de.samthedev.veloutils.api.ModuleService
import de.samthedev.veloutils.api.NetworkService
import de.samthedev.veloutils.api.ReportService
import de.samthedev.veloutils.api.PlaceholderService
import de.samthedev.veloutils.api.MessagingService
import de.samthedev.veloutils.api.StaffService
import de.samthedev.veloutils.api.VeloUtilsApi
import de.samthedev.veloutils.api.ChatService

internal class PaperApiServices(
    enabled: Set<String>,
    override val afk: AfkService?,
    override val chat: ChatService?,
    override val placeholders: PlaceholderService?,
    override val messaging: MessagingService?,
) : VeloUtilsApi {
    override val modules: ModuleService = PaperModuleService(enabled)
    override val network: NetworkService? = null
    override val maintenance: MaintenanceService? = null
    override val staff: StaffService? = null
    override val reports: ReportService? = null
    override val moderation: ModerationService? = null
}

private class PaperModuleService(enabled: Set<String>) : ModuleService {
    private val active = enabled.toSet()
    private val known = setOf(
        "afk", "announcements", "chat", "messaging", "moderation", "placeholders", "staff-chat", "network-alerts",
    )

    override fun state(id: String): ModuleAvailability = when (id.lowercase()) {
        in active -> ModuleAvailability.ENABLED
        in known -> ModuleAvailability.DISABLED
        else -> ModuleAvailability.UNAVAILABLE
    }

    override fun active(): Set<String> = active
}
