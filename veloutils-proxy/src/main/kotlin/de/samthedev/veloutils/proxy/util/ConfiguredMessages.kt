// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Path
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import net.kyori.adventure.text.format.NamedTextColor

public class ConfiguredMessages(
    private val file: Path,
    private val warning: (String) -> Unit = {},
    private val bundledMessages: () -> InputStream? = {
        ConfiguredMessages::class.java.classLoader.getResourceAsStream("messages.yml")
    },
) {
    private val legacyMiniMessage = MiniMessage.miniMessage()
    private data class Templates(val values: Map<String, Component>, val defaultedKeys: Set<String>)
    private val templates = AtomicReference(Templates(emptyMap(), emptySet()))
    private val warnedKeys = ConcurrentHashMap.newKeySet<String>()
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

    private fun loadTemplates(): Templates {
        val disk = YamlConfigurationLoader.builder().path(file).build().load()
        val defaults = checkNotNull(bundledMessages()) { "Missing bundled messages.yml" }.use { input ->
            YamlConfigurationLoader.builder().source { input.bufferedReader() }.build().load()
        }
        val flattened = mutableMapOf<String, String>()
        fun visit(prefix: String, node: org.spongepowered.configurate.ConfigurationNode, overwrite: Boolean) {
            node.childrenMap().forEach { (key, child) ->
                val path = if (prefix.isEmpty()) key.toString() else "$prefix.$key"
                if (child.childrenMap().isEmpty()) {
                    if (path in supportedKeys) child.string?.let { if (overwrite || path !in flattened) flattened[path] = it }
                } else visit(path, child, overwrite)
            }
        }
        visit("", defaults, overwrite = false)
        val bundledKeys = flattened.keys.toSet()
        visit("", disk, overwrite = true)
        val diskKeys = supportedKeys.filterTo(mutableSetOf()) { key -> !disk.node(*key.split('.').toTypedArray()).virtual() }
        val placeholders = TagResolver.resolver(placeholderNames.map { name ->
            Placeholder.component(name, Component.text(marker(name)))
        })
        val compiled = flattened.mapValues { (key, template) ->
            val legacyCompatibility = "<reset>" in template.lowercase()
            runCatching {
                if (legacyCompatibility) legacyMiniMessage.deserialize(template, placeholders)
                else ConfiguredMiniMessage.deserialize(template, placeholders)
            }
                .getOrElse { throw IllegalArgumentException("messages.yml: invalid MiniMessage at '$key': ${it.message}", it) }
        }
        return Templates(compiled, bundledKeys - diskKeys)
    }

    public fun render(key: String, placeholders: Map<String, Component> = emptyMap()): Component {
        val snapshot = templates.get()
        if (key in snapshot.defaultedKeys && warnedKeys.add("default:$key")) {
            warning("messages.yml is missing '$key'; using the bundled default.")
        }
        val template = snapshot.values[key] ?: run {
            if (warnedKeys.add("missing:$key")) warning("Message '$key' is missing from messages.yml and bundled defaults.")
            return Component.text("Missing message: $key", NamedTextColor.RED)
        }
        return placeholders.entries.fold(template) { component, (name, replacement) ->
            component.replaceText { builder -> builder.matchLiteral(marker(name)).replacement(replacement) }
        }
    }

    private fun marker(name: String): String = "\uE000$name\uE001"
}
