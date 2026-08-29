// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge.announcement

import de.samthedev.veloutils.bridge.server.PlatformSchedulers
import de.samthedev.veloutils.common.DurationParser
import de.samthedev.veloutils.core.module.ManagedModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.java.JavaPlugin
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

public data class AnnouncementConfig(
    val initialDelay: Duration,
    val interval: Duration,
    val randomNonRepeating: Boolean,
    val messages: List<String>,
) {
    public companion object {
        public fun load(plugin: JavaPlugin): AnnouncementConfig {
            val path = plugin.dataPath.resolve("modules/announcements.yml")
            if (Files.notExists(path)) plugin.saveResource("modules/announcements.yml", false)
            val root = YamlConfigurationLoader.builder().path(path).build().load()
            require(root.node("config-version").getInt(1) == 1) { "Unsupported modules/announcements.yml config-version" }
            val initialDelay = DurationParser.parse(root.node("initial-delay").getString("2m"))
            val interval = DurationParser.parse(root.node("interval").getString("10m"))
            val messages = root.node("messages").getList(String::class.java, emptyList())
            require(!initialDelay.isNegative) { "Announcement initial-delay must not be negative" }
            require(interval >= Duration.ofSeconds(30)) { "Announcement interval must be at least 30s" }
            require(messages.isNotEmpty()) { "At least one local announcement is required" }
            return AnnouncementConfig(initialDelay, interval, root.node("random-non-repeating").getBoolean(false), messages)
        }
    }
}

public class AnnouncementModule(
    private val plugin: JavaPlugin,
    private val schedulers: PlatformSchedulers,
    config: AnnouncementConfig,
) : ManagedModule {
    private val messages: List<Component> = config.messages.map(MiniMessage.miniMessage()::deserialize)
    private val initialDelaySeconds = config.initialDelay.seconds.coerceAtLeast(1)
    private val intervalSeconds = config.interval.seconds
    private val randomNonRepeating = config.randomNonRepeating
    private val index = AtomicInteger()
    @Volatile private var randomizedCycle: List<Component> = emptyList()
    private var task: AutoCloseable? = null

    override fun enable() {
        task = schedulers.repeatAsync(initialDelaySeconds, intervalSeconds) {
            val message = next()
            schedulers.global { plugin.server.broadcast(message) }
        }
    }

    override fun disable() {
        task?.close()
        task = null
        randomizedCycle = emptyList()
    }

    private fun next(): Component {
        if (!randomNonRepeating) return messages[Math.floorMod(index.getAndIncrement(), messages.size)]
        synchronized(this) {
            if (randomizedCycle.isEmpty()) randomizedCycle = messages.shuffled()
            return randomizedCycle.first().also { randomizedCycle = randomizedCycle.drop(1) }
        }
    }
}
