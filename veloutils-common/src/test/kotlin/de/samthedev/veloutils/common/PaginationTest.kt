// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaginationTest {
    @Test
    fun `empty result still has one page without navigation`() {
        val page = Page<String>(emptyList(), 1, 5, 0)
        assertEquals(1, page.totalPages)
        assertFalse(page.hasPrevious)
        assertFalse(page.hasNext)
    }

    @Test
    fun `exact page boundary does not create an extra page`() {
        val page = Page(List(5) { "$it" }, 1, 5, 5)
        assertEquals(1, page.totalPages)
        assertFalse(page.hasNext)
    }

    @Test
    fun `middle page exposes both directions`() {
        val page = Page(List(5) { "$it" }, 2, 5, 13)
        assertEquals(3, page.totalPages)
        assertTrue(page.hasPrevious)
        assertTrue(page.hasNext)
        assertEquals(5L, PageRequest(2, 5).offset)
    }
}
