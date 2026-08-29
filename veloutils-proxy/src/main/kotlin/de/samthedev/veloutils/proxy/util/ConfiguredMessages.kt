// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

public class ConfiguredMessages(private val file: Path) {
    private val miniMessage = MiniMessage.builder().strict(true).build()
    private val legacyMiniMessage = MiniMessage.miniMessage()
    private val templates = AtomicReference<Map<String, Component>>(emptyMap())
    private val supportedKeys = setOf(
        "no-permission",
        "players-only",
        "server-unavailable",
        "connecting",
        "maintenance.denied",
        "maintenance.enabled",
        "maintenance.disabled",
        "server-access.denied",
        "report.created",
        "moderation.banned",
        "staff-chat.staff-format",
        "staff-chat.admin-format",
        "chat.global-format",
    )
    private val placeholderNames = setOf(
        "player", "server", "usage", "reason", "id", "type", "reporter", "channel", "message",
    )

    public fun reload() {
        templates.set(loadTemplates())
    }

    public fun validate() {
        loadTemplates()
    }

    private fun loadTemplates(): Map<String, Component> {
        val root = YamlConfigurationLoader.builder().path(file).build().load()
        val flattened = mutableMapOf<String, String>()
        fun visit(prefix: String, node: org.spongepowered.configurate.ConfigurationNode) {
            node.childrenMap().forEach { (key, child) ->
                val path = if (prefix.isEmpty()) key.toString() else "$prefix.$key"
                if (child.childrenMap().isEmpty()) {
                    if (path in supportedKeys) child.string?.let { flattened[path] = it }
                } else visit(path, child)
            }
        }
        visit("", root)
        val placeholders = TagResolver.resolver(placeholderNames.map { name ->
            Placeholder.component(name, Component.text(marker(name)))
        })
        return flattened.mapValues { (key, template) ->
            val parser = if ("<reset>" in template.lowercase()) legacyMiniMessage else miniMessage
            runCatching { parser.deserialize(template, placeholders) }
                .getOrElse { throw IllegalArgumentException("messages.yml: invalid MiniMessage at '$key': ${it.message}", it) }
        }
    }

    public fun render(key: String, placeholders: Map<String, Component> = emptyMap()): Component {
        val template = templates.get()[key] ?: miniMessage.deserialize("<red>Missing message: ${escapeKey(key)}")
        return placeholders.entries.fold(template) { component, (name, replacement) ->
            component.replaceText { builder -> builder.matchLiteral(marker(name)).replacement(replacement) }
        }
    }

    private fun escapeKey(value: String): String = value.replace("<", "").replace(">", "")
    private fun marker(name: String): String = "\uE000$name\uE001"
}
