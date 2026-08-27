// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.common

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionsTest {
    @Test
    fun `canonical and legacy permissions grant the same capability`() {
        assertTrue(Permissions.REPORTS_CLOSE.isGranted { it == "veloutils.reports.close" })
        assertTrue(Permissions.REPORTS_CLOSE.isGranted { it == "veloutils.report.manage" })
        assertFalse(Permissions.REPORTS_CLOSE.isGranted(legacyEnabled = false) { it == "veloutils.report.manage" })
    }

    @Test
    fun `all canonical permission nodes are unique`() {
        assertTrue(Permissions.ALL.map(PermissionDefinition::node).let { it.size == it.distinct().size })
    }
}
