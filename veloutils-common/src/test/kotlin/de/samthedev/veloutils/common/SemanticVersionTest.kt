// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SemanticVersionTest {
    @Test
    fun `release ordering follows semantic version precedence`() {
        val snapshot = requireNotNull(SemanticVersion.parseOrNull("1.0.0-SNAPSHOT"))
        val release = requireNotNull(SemanticVersion.parseOrNull("1.0.0"))
        val next = requireNotNull(SemanticVersion.parseOrNull("v1.1.0+build.4"))

        assertTrue(release > snapshot)
        assertTrue(next > release)
        assertEquals("1.1.0", next.toString())
        assertNull(SemanticVersion.parseOrNull("latest"))
    }
}
