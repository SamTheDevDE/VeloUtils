// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge.afk

import de.samthedev.veloutils.bridge.server.PlatformSchedulers
import de.samthedev.veloutils.api.AfkService
import de.samthedev.veloutils.api.AfkStatus
import de.samthedev.veloutils.common.DurationParser
import de.samthedev.veloutils.common.Permissions
import de.samthedev.veloutils.core.module.ManagedModule
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

public data class AfkConfig(
    val timeout: Duration,
    val scanInterval: Duration,
    val kickAfter: Duration?,
    val announce: Boolean,
    val enterMessage: String,
    val leaveMessage: String,
    val kickMessage: String,
) {
    public companion object {
        public fun load(plugin: JavaPlugin): AfkConfig {
            val path = plugin.dataPath.resolve("modules/afk.yml")
            if (Files.notExists(path)) plugin.saveResource("modules/afk.yml", false)
            val root = YamlConfigurationLoader.builder().path(path).build().load()
            require(root.node("config-version").getInt(1) == 1) { "Unsupported modules/afk.yml config-version" }
            val timeout = DurationParser.parse(root.node("timeout").getString("10m"))
            val interval = DurationParser.parse(root.node("scan-interval").getString("5s"))
            val kickAfter = root.node("kick-after").getString("").trim().takeIf(String::isNotEmpty)?.let(DurationParser::parse)
            require(timeout in Duration.ofMinutes(1)..Duration.ofHours(24)) { "AFK timeout must be between 1m and 24h" }
            require(interval in Duration.ofSeconds(1)..Duration.ofMinutes(1)) { "AFK scan-interval must be between 1s and 1m" }
            require(kickAfter == null || kickAfter >= Duration.ofMinutes(1)) { "AFK kick-after must be empty or at least 1m" }
            return AfkConfig(
                timeout,
                interval,
                kickAfter,
                root.node("announce").getBoolean(true),
                root.node("messages", "enter").getString("<yellow><player> is now AFK.</yellow>"),
                root.node("messages", "leave").getString("<yellow><player> is no longer AFK.</yellow>"),
                root.node("messages", "kick").getString("<red>You were disconnected for being AFK too long.</red>"),
            )
        }
    }
}

public data class AfkSnapshot(val afk: Boolean, val since: Instant?, val lastActivity: Instant)

public class AfkStateService(private val clock: Clock = Clock.systemUTC()) : AfkService {
    private data class State(val lastActivity: Instant, val afkSince: Instant? = null, val kickIssued: Boolean = false)
    private val states = ConcurrentHashMap<UUID, State>()

    public fun join(playerId: UUID) { states[playerId] = State(clock.instant()) }
    public fun quit(playerId: UUID) { states.remove(playerId) }

    public fun activity(playerId: UUID): Boolean {
        val now = clock.instant()
        var wasAfk = false
        states.compute(playerId) { _, state ->
            wasAfk = state?.afkSince != null
            State(now)
        }
        return wasAfk
    }

    public fun toggle(playerId: UUID): Boolean {
        val now = clock.instant()
        var afk = false
        states.compute(playerId) { _, state ->
            afk = state?.afkSince == null
            if (afk) State(state?.lastActivity ?: now, now) else State(now)
        }
        return afk
    }

    override suspend fun setAfk(playerId: UUID, afk: Boolean): AfkStatus? {
        val now = clock.instant()
        states.computeIfPresent(playerId) { _, state ->
            if (afk) state.copy(afkSince = state.afkSince ?: now, kickIssued = false) else State(now)
        }
        return snapshot(playerId)?.let { AfkStatus(playerId, it.afk, it.since, it.lastActivity) }
    }

    public fun update(playerId: UUID, timeout: Duration, kickAfter: Duration?): AfkTransition {
        val now = clock.instant()
        var transition = AfkTransition.NONE
        states.computeIfPresent(playerId) { _, state ->
            when {
                state.afkSince == null && Duration.between(state.lastActivity, now) >= timeout -> {
                    transition = AfkTransition.ENTERED
                    state.copy(afkSince = now)
                }
                state.afkSince != null && !state.kickIssued && kickAfter != null &&
                    Duration.between(state.afkSince, now) >= kickAfter -> {
                    transition = AfkTransition.KICK
                    state.copy(kickIssued = true)
                }
                else -> state
            }
        }
        return transition
    }

    public fun localSnapshot(playerId: UUID): AfkSnapshot? = states[playerId]?.let {
        AfkSnapshot(it.afkSince != null, it.afkSince, it.lastActivity)
    }

    override fun snapshot(playerId: UUID): AfkStatus? = localSnapshot(playerId)?.let {
        AfkStatus(playerId, it.afk, it.since, it.lastActivity)
    }
}

public enum class AfkTransition { NONE, ENTERED, KICK }

public class AfkModule(
    private val plugin: JavaPlugin,
    private val schedulers: PlatformSchedulers,
    private val config: AfkConfig,
    public val states: AfkStateService = AfkStateService(),
) : ManagedModule, Listener, CommandExecutor {
    private val miniMessage = MiniMessage.miniMessage()
    private var scanTask: AutoCloseable? = null

    override fun validate() {
        miniMessage.deserialize(config.enterMessage, Placeholder.unparsed("player", "Player"))
        miniMessage.deserialize(config.leaveMessage, Placeholder.unparsed("player", "Player"))
        miniMessage.deserialize(config.kickMessage)
    }

    override fun enable() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.getCommand("afk")?.setExecutor(this)
        plugin.server.onlinePlayers.forEach { states.join(it.uniqueId) }
        scanTask = schedulers.repeatGlobal(config.scanInterval.seconds * 20L, config.scanInterval.seconds * 20L, ::scan)
    }

    override fun disable() {
        scanTask?.close()
        scanTask = null
        plugin.getCommand("afk")?.setExecutor(null)
        HandlerList.unregisterAll(this)
        plugin.server.onlinePlayers.forEach { states.quit(it.uniqueId) }
    }

    @EventHandler
    public fun onJoin(event: PlayerJoinEvent) { states.join(event.player.uniqueId) }

    @EventHandler
    public fun onQuit(event: PlayerQuitEvent) { states.quit(event.player.uniqueId) }

    @EventHandler(ignoreCancelled = true)
    public fun onMove(event: PlayerMoveEvent) {
        val destination = event.to
        if (event.from.blockX != destination.blockX || event.from.blockY != destination.blockY || event.from.blockZ != destination.blockZ) {
            recordActivity(event.player)
        }
    }

    @EventHandler(ignoreCancelled = true)
    public fun onInteract(event: PlayerInteractEvent) { recordActivity(event.player) }

    @EventHandler(ignoreCancelled = true)
    public fun onCommand(event: PlayerCommandPreprocessEvent) {
        if (!event.message.startsWith("/afk", ignoreCase = true)) recordActivity(event.player)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("This command can only be used by a player.")
            return true
        }
        if (!player.hasPermission(Permissions.AFK_TOGGLE.node)) {
            player.sendMessage("You do not have permission to toggle AFK.")
            return true
        }
        val entered = states.toggle(player.uniqueId)
        announce(player, entered)
        return true
    }

    private fun recordActivity(player: Player) {
        if (states.activity(player.uniqueId)) announce(player, false)
    }

    private fun scan() {
        plugin.server.onlinePlayers.forEach { player ->
            if (player.hasPermission(Permissions.AFK_BYPASS.node)) return@forEach
            when (states.update(player.uniqueId, config.timeout, config.kickAfter)) {
                AfkTransition.ENTERED -> announce(player, true)
                AfkTransition.KICK -> schedulers.entity(player) { player.kick(miniMessage.deserialize(config.kickMessage)) }
                AfkTransition.NONE -> Unit
            }
        }
    }

    private fun announce(player: Player, entered: Boolean) {
        if (!config.announce) return
        val template = if (entered) config.enterMessage else config.leaveMessage
        val message = miniMessage.deserialize(template, Placeholder.unparsed("player", player.name))
        schedulers.global { plugin.server.broadcast(message) }
    }
}
