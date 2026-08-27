// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.integration

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AlertRotationTest {
    @Test
    fun `sequential rotation wraps around`() {
        val rotation = AlertRotation(3, false, Random(1))

        assertEquals(listOf(0, 1, 2, 0, 1), List(5) { rotation.next() })
    }

    @Test
    fun `random rotation avoids immediate repeats`() {
        val rotation = AlertRotation(3, true, Random(1))
        val selections = List(100) { rotation.next() }

        selections.zipWithNext().forEach { (first, second) -> assertNotEquals(first, second) }
    }
}
