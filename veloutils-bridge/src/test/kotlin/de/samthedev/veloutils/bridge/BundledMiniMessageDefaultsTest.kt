// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import kotlin.test.Test
import kotlin.test.fail

class BundledMiniMessageDefaultsTest {
    private val parser = MiniMessage.builder().strict(true).build()
    private val placeholders = TagResolver.resolver(
        setOf("player", "server", "message", "reason", "channel", "target", "sender").map { name ->
            Placeholder.component(name, Component.text(name))
        },
    )

    @Test
    fun `every bundled bridge MiniMessage value is valid in strict mode`() {
        RESOURCES.forEach { resource ->
            val root = checkNotNull(javaClass.classLoader.getResourceAsStream(resource)) { "Missing resource $resource" }.use { input ->
                YamlConfigurationLoader.builder().source { input.bufferedReader() }.build().load()
            }
            visit(resource, root)
        }
    }

    private fun visit(path: String, node: ConfigurationNode) {
        when {
            node.isList -> node.childrenList().forEachIndexed { index, child -> visit("$path[$index]", child) }
            node.isMap -> node.childrenMap().forEach { (key, child) -> visit("$path.$key", child) }
            else -> (node.raw() as? String)?.takeIf { '<' in it }?.let { source ->
                runCatching { parser.deserialize(source, placeholders) }.onFailure { failure ->
                    fail("$path: invalid bundled MiniMessage: ${failure.message}")
                }
            }
        }
    }

    private companion object {
        val RESOURCES: List<String> = listOf(
            "config.yml",
            "modules/afk.yml",
            "modules/announcements.yml",
            "modules/chat.yml",
            "modules/messaging.yml",
            "modules/presentation.yml",
        )
    }
}
