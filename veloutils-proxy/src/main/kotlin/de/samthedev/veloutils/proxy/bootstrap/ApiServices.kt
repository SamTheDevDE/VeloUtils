// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.bootstrap

import de.samthedev.veloutils.api.MaintenanceService
import de.samthedev.veloutils.api.ModerationService
import de.samthedev.veloutils.api.ModuleAvailability
import de.samthedev.veloutils.api.ModuleService
import de.samthedev.veloutils.api.PlaceholderService
import de.samthedev.veloutils.api.NetworkService
import de.samthedev.veloutils.api.ReportService
import de.samthedev.veloutils.api.StaffService
import de.samthedev.veloutils.api.VeloUtilsApi

internal data class ApiServices(
    override val modules: ModuleService,
    override val network: NetworkService,
    override val maintenance: MaintenanceService?,
    override val staff: StaffService?,
    override val reports: ReportService?,
    override val moderation: ModerationService?,
    override val placeholders: PlaceholderService,
) : VeloUtilsApi

internal class StaticModuleService(
    enabled: Set<String>,
    known: Set<String>,
) : ModuleService {
    private val enabledModules = enabled.toSet()
    private val knownModules = known.toSet()

    override fun state(id: String): ModuleAvailability = when (id.lowercase()) {
        in enabledModules -> ModuleAvailability.ENABLED
        in knownModules -> ModuleAvailability.DISABLED
        else -> ModuleAvailability.UNAVAILABLE
    }

    override fun active(): Set<String> = enabledModules
}
