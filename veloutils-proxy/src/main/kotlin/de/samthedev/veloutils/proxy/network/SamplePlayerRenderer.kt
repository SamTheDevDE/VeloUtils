// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.network

import de.samthedev.veloutils.proxy.util.DynamicMiniMessageTemplate
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

/** Converts the component-capable configured format to the legacy string used by the status protocol. */
public class SamplePlayerRenderer(sources: List<String>) {
    private val serializer = LegacyComponentSerializer.builder()
        .character(LegacyComponentSerializer.SECTION_CHAR)
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build()
    private val templates = sources.mapIndexed { index, source ->
        DynamicMiniMessageTemplate(source, "config.yml: motd.sample-players[$index]")
    }

    public fun render(values: Map<String, String>): List<String> = templates.take(MAXIMUM_SAMPLES).map { template ->
        serializer.serialize(template.render(values))
    }

    private companion object {
        const val MAXIMUM_SAMPLES: Int = 12
    }
}
