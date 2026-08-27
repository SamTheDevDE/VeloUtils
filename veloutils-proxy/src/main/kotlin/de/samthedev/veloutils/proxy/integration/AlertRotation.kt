// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.integration

import kotlin.random.Random

internal class AlertRotation(
    private val size: Int,
    private val randomOrder: Boolean,
    private val random: Random,
) {
    private var nextIndex = 0
    private var previousIndex = -1

    init {
        require(size > 0) { "Alert rotation requires at least one message" }
    }

    fun next(): Int {
        if (!randomOrder || size == 1) {
            return nextIndex++ % size
        }

        var candidate = random.nextInt(size - 1)
        if (previousIndex >= 0 && candidate >= previousIndex) candidate++
        previousIndex = candidate
        return candidate
    }
}
