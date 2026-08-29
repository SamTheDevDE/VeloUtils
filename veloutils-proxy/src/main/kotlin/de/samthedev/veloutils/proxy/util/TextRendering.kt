// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.util

import de.samthedev.veloutils.proxy.config.PlayerFormattingConfig
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import java.util.concurrent.ConcurrentHashMap

/** Trusted administrator templates are deliberately parsed in strict mode. */
public object ConfiguredMiniMessage {
    private val parser: MiniMessage = MiniMessage.builder().strict(true).build()

    public fun deserialize(source: String, vararg resolvers: TagResolver): Component =
        parser.deserialize(source, *resolvers)
}

/** Compiles trusted MiniMessage once while leaving brace placeholders as safe component insertion points. */
public class DynamicMiniMessageTemplate(source: String, path: String) {
    private val keys = PLACEHOLDER.findAll(source).mapTo(linkedSetOf()) { it.groupValues[1] }
    private val component: Component = runCatching {
        ConfiguredMiniMessage.deserialize(PLACEHOLDER.replace(source) { marker(it.groupValues[1]) })
    }.getOrElse { throw IllegalArgumentException("$path: invalid MiniMessage: ${it.message}", it) }

    public fun render(values: Map<String, String>): Component = keys.fold(component) { rendered, key ->
        rendered.replaceText { builder ->
            builder.matchLiteral(marker(key)).replacement(Component.text(values.getOrDefault(key, "")))
        }
    }

    private companion object {
        val PLACEHOLDER: Regex = Regex("\\{([a-z][a-z0-9_.-]{0,63})}")
        fun marker(key: String): String = "\uE100$key\uE101"
    }
}

public data class PlayerFormattingCapabilities(
    val colors: Boolean = false,
    val decorations: Boolean = false,
    val gradients: Boolean = false,
)

/**
 * Renders untrusted player input with an explicit presentation-only tag allowlist.
 * Interactive, data-driven, font, insertion, selector, score, NBT, URL, and reset tags are never registered.
 */
public class PlayerMessageRenderer(private val config: PlayerFormattingConfig) {
    private val parsers = ConcurrentHashMap<PlayerFormattingCapabilities, MiniMessage>()

    public fun capabilities(hasPermission: (String) -> Boolean): PlayerFormattingCapabilities {
        if (!config.enabled) return PlayerFormattingCapabilities()
        val full = hasPermission(config.permissions.full)
        return PlayerFormattingCapabilities(
            colors = config.defaults.colors || full || hasPermission(config.permissions.colors),
            decorations = config.defaults.decorations || full || hasPermission(config.permissions.decorations),
            gradients = config.defaults.gradients || full || hasPermission(config.permissions.gradients),
        )
    }

    public fun render(input: String, capabilities: PlayerFormattingCapabilities): Component {
        if (!config.enabled || capabilities == PlayerFormattingCapabilities()) return Component.text(input)
        val parser = parsers.computeIfAbsent(capabilities) { selected ->
            val allowed = buildList {
                if (selected.colors) add(StandardTags.color())
                if (selected.decorations) add(StandardTags.decorations())
                if (selected.gradients) add(StandardTags.gradient())
            }
            MiniMessage.builder().tags(TagResolver.resolver(allowed)).build()
        }
        return runCatching { parser.deserialize(input) }.getOrElse { Component.text(input) }
    }
}
