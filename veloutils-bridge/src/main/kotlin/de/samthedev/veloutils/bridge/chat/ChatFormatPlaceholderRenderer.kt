// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge.chat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags

internal class ChatFormatPlaceholderRenderer(
    styledPlaceholders: Set<String>,
) {
    private val styledPlaceholders: Set<String> = styledPlaceholders
        .map(String::lowercase)
        .toSet()

    private val fullMiniMessage: MiniMessage = MiniMessage.miniMessage()

    /*
     * Dynamic styled placeholders deliberately receive only presentation tags.
     *
     * They cannot create click events, hover events, insertions, selectors,
     * NBT components, fonts, translations, keybinds, scores, or other
     * interactive/structural content.
     */
    private val styledMiniMessage: MiniMessage = MiniMessage.builder()
        .strict(true)
        .tags(
            TagResolver.builder()
                .resolver(StandardTags.color())
                .resolver(StandardTags.decorations())
                .resolver(StandardTags.gradient())
                .resolver(StandardTags.rainbow())
                .build(),
        )
        .build()

    fun keys(template: String): Set<String> =
        PLACEHOLDER
            .findAll(template)
            .map { it.groupValues[1] }
            .toSet()

    fun resolve(
        template: String,
        values: Map<String, String>,
    ): ResolvedChatFormat {
        val keys = PLACEHOLDER
            .findAll(template)
            .map { it.groupValues[1] }
            .distinct()
            .toList()

        if (keys.isEmpty()) {
            return ResolvedChatFormat(
                template = template,
                resolvers = emptyList(),
            )
        }

        /*
         * Replace VeloUtils' {placeholder} syntax with temporary MiniMessage
         * component placeholders. The actual dynamic values are NEVER
         * concatenated into the trusted template.
         */
        val resolverNames = keys.mapIndexed { index, key ->
            key to "vudynamic$index"
        }.toMap()

        val resolvedTemplate = PLACEHOLDER.replace(template) { match ->
            "<${resolverNames.getValue(match.groupValues[1])}>"
        }

        val resolvers = keys.map { key ->
            val resolverName = resolverNames.getValue(key)
            val value = values[key].orEmpty()

            if (key in styledPlaceholders) {
                Placeholder.component(
                    resolverName,
                    renderStyled(value),
                )
            } else {
                /*
                 * Ordinary placeholders are always literal text.
                 *
                 * Example:
                 *
                 *   value = "<red>Hello</red>"
                 *
                 * renders literally as:
                 *
                 *   <red>Hello</red>
                 *
                 * instead of gaining formatting privileges.
                 */
                Placeholder.unparsed(
                    resolverName,
                    value,
                )
            }
        }

        return ResolvedChatFormat(
            template = resolvedTemplate,
            resolvers = resolvers,
        )
    }

    private fun renderStyled(value: String): Component {
        if (value.isEmpty()) {
            return Component.empty()
        }

        /*
         * Reject values containing tags outside our presentation allowlist
         * before asking the restricted parser to deserialize them.
         */
        if (containsUnsupportedTag(value)) {
            return plainFallback(value)
        }

        return runCatching {
            styledMiniMessage.deserialize(value)
        }.getOrElse {
            /*
             * A malformed prefix must never be able to break chat for the
             * sender or viewers.
             */
            plainFallback(value)
        }
    }

    private fun plainFallback(value: String): Component {
        val stripped = runCatching {
            fullMiniMessage.stripTags(value)
        }.getOrDefault(value)

        return Component.text(stripped)
    }

    private fun containsUnsupportedTag(value: String): Boolean =
        TAG.findAll(value)
            .map {
                it.groupValues[1]
                    .lowercase()
                    .removePrefix("!")
            }
            .any { tag ->
                !isAllowedStyleTag(tag)
            }

    private fun isAllowedStyleTag(name: String): Boolean {
        if (name.startsWith("#")) {
            val hex = name.drop(1)

            return hex.length in setOf(3, 6) &&
                hex.all { character ->
                    character.digitToIntOrNull(16) != null
                }
        }

        return name in ALLOWED_STYLE_TAGS
    }

    internal companion object {
        private val PLACEHOLDER =
            Regex("\\{([a-z][a-z0-9_.-]{0,63})}")

        private val TAG =
            Regex("(?i)<\\s*/?\\s*([!#a-z0-9_-]+)(?=[:>\\s])")

        private val VALID_PLACEHOLDER =
            Regex("[a-z][a-z0-9_.-]{0,63}")

        private val ALLOWED_STYLE_TAGS: Set<String> = setOf(
            // Colors
            "black",
            "dark_blue",
            "dark_green",
            "dark_aqua",
            "dark_red",
            "dark_purple",
            "gold",
            "gray",
            "grey",
            "dark_gray",
            "dark_grey",
            "blue",
            "green",
            "aqua",
            "red",
            "light_purple",
            "yellow",
            "white",
            "color",
            "colour",
            "c",

            // Decorations
            "bold",
            "b",
            "italic",
            "i",
            "em",
            "underlined",
            "underline",
            "u",
            "strikethrough",
            "st",
            "obfuscated",
            "obf",

            // Presentation effects
            "gradient",
            "rainbow",
        )

        fun isValidPlaceholderKey(value: String): Boolean =
            VALID_PLACEHOLDER.matches(value)
    }
}

internal data class ResolvedChatFormat(
    val template: String,
    val resolvers: List<TagResolver>,
)