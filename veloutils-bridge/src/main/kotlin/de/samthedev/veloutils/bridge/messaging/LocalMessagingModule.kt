// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge.messaging

import de.samthedev.veloutils.api.MessageDelivery
import de.samthedev.veloutils.api.MessagingService
import de.samthedev.veloutils.bridge.server.PlatformSchedulers
import de.samthedev.veloutils.common.InputPolicies
import de.samthedev.veloutils.common.Permissions
import de.samthedev.veloutils.core.module.ManagedModule
import de.samthedev.veloutils.protocol.PrivateMessageDeliveryPayload
import de.samthedev.veloutils.protocol.DeliveryStatus
import de.samthedev.veloutils.protocol.IgnoreUpdateResponsePayload
import de.samthedev.veloutils.protocol.IgnoreListResponsePayload
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal data class MessagingConfig(val outgoing: String, val incoming: String, val spy: String)

internal fun loadMessagingConfig(plugin: JavaPlugin): MessagingConfig {
    val path = plugin.dataPath.resolve("modules/messaging.yml")
    if (Files.notExists(path)) plugin.saveResource("modules/messaging.yml", false)
    val root = YamlConfigurationLoader.builder().path(path).build().load()
    require(root.node("config-version").getInt(1) == 1) { "Unsupported modules/messaging.yml config-version" }
    return MessagingConfig(
        root.node("formats", "outgoing").getString("<gray>You → <target>:</gray> <white><message></white>"),
        root.node("formats", "incoming").getString("<gray><sender> → You:</gray> <white><message></white>"),
        root.node("formats", "spy").getString("<dark_gray>[Spy] <sender> → <target>:</dark_gray> <gray><message></gray>"),
    )
}

internal class LocalMessagingModule(
    private val plugin: JavaPlugin,
    private val schedulers: PlatformSchedulers,
    private val config: MessagingConfig,
    private val networkSend: (Player, String, String) -> Boolean,
    private val networkIgnore: (Player, String, Boolean, (IgnoreUpdateResponsePayload) -> Unit) -> Unit,
    private val networkIgnoreList: (Player, (IgnoreListResponsePayload) -> Unit) -> Unit,
) : ManagedModule, Listener, CommandExecutor, MessagingService {
    private val miniMessage = MiniMessage.miniMessage()
    private val ignored = ConcurrentHashMap<UUID, MutableMap<UUID, String>>()
    private val lastPartner = ConcurrentHashMap<UUID, UUID>()
    private val lastPartnerName = ConcurrentHashMap<UUID, String>()
    private val spies = ConcurrentHashMap.newKeySet<UUID>()
    private val commands = listOf("msg", "reply", "ignore", "unignore", "ignorelist", "socialspy")
    private val ignoreStore = LocalIgnoreStore(plugin.dataPath.resolve("messaging-state.yml"), schedulers)

    override fun validate() {
        listOf(config.outgoing, config.incoming, config.spy).forEach { template ->
            miniMessage.deserialize(
                template,
                Placeholder.unparsed("sender", "Sender"),
                Placeholder.unparsed("target", "Target"),
                Placeholder.unparsed("message", "Message"),
            )
        }
    }

    override fun enable() {
        ignoreStore.load().forEach { (owner, entries) -> ignored[owner] = ConcurrentHashMap(entries) }
        plugin.server.pluginManager.registerEvents(this, plugin)
        commands.forEach { plugin.getCommand(it)?.setExecutor(this) }
        plugin.server.onlinePlayers.forEach(::synchronizeIgnores)
    }

    override fun disable() {
        commands.forEach { plugin.getCommand(it)?.setExecutor(null) }
        HandlerList.unregisterAll(this)
        persistIgnores()
        ignored.clear()
        lastPartner.clear()
        lastPartnerName.clear()
        spies.clear()
    }

    override fun isIgnoring(playerId: UUID, otherId: UUID): Boolean = ignored[playerId]?.containsKey(otherId) == true

    override fun send(senderId: UUID, targetId: UUID, message: String): MessageDelivery {
        val sender = plugin.server.getPlayer(senderId) ?: return MessageDelivery.SENDER_OFFLINE
        val target = plugin.server.getPlayer(targetId) ?: return MessageDelivery.TARGET_OFFLINE
        if (isIgnoring(targetId, senderId)) return MessageDelivery.IGNORED
        val safe = runCatching { InputPolicies.CHAT.validate(message) }.getOrNull() ?: return MessageDelivery.INVALID
        deliver(sender, target, safe)
        return MessageDelivery.SENT
    }

    public fun receiveNetwork(payload: PrivateMessageDeliveryPayload): DeliveryStatus {
        val targetId = runCatching { UUID.fromString(payload.targetId) }.getOrNull() ?: return DeliveryStatus.INVALID_MESSAGE
        val senderId = runCatching { UUID.fromString(payload.senderId) }.getOrNull() ?: return DeliveryStatus.INVALID_MESSAGE
        val target = plugin.server.getPlayer(targetId) ?: return DeliveryStatus.NO_RECIPIENTS
        if (target.name != payload.targetName) return DeliveryStatus.NO_RECIPIENTS
        if (isIgnoring(targetId, senderId)) return DeliveryStatus.IGNORED
        val message = runCatching { InputPolicies.CHAT.validate(payload.message) }.getOrNull() ?: return DeliveryStatus.INVALID_MESSAGE
        lastPartner[targetId] = senderId
        lastPartnerName[targetId] = payload.senderName
        val values = arrayOf(
            Placeholder.unparsed("sender", payload.senderName),
            Placeholder.unparsed("target", payload.targetName),
            Placeholder.unparsed("message", message),
        )
        schedulers.entity(target) { target.sendMessage(miniMessage.deserialize(config.incoming, *values)) }
        spies.asSequence().filter { it != targetId }.mapNotNull(plugin.server::getPlayer).forEach { spy ->
            schedulers.entity(spy) { spy.sendMessage(miniMessage.deserialize(config.spy, *values)) }
        }
        return DeliveryStatus.SENT
    }

    @EventHandler
    public fun onJoin(event: PlayerJoinEvent) { synchronizeIgnores(event.player) }

    @EventHandler
    public fun onQuit(event: PlayerQuitEvent) {
        lastPartner.remove(event.player.uniqueId)
        spies.remove(event.player.uniqueId)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage("This command can only be used by a player.")
            return true
        }
        when (command.name.lowercase()) {
            "msg" -> message(player, args)
            "reply" -> reply(player, args)
            "ignore" -> ignore(player, args, true)
            "unignore" -> ignore(player, args, false)
            "ignorelist" -> listIgnored(player)
            "socialspy" -> toggleSpy(player)
        }
        return true
    }

    private fun message(sender: Player, args: Array<out String>) {
        if (!sender.hasPermission(Permissions.MESSAGING_USE.node)) return deny(sender)
        if (args.size < 2) return sender.sendMessage(Component.text("Usage: /msg <player> <message>"))
        val target = plugin.server.getPlayerExact(args[0])
        if (target == null) {
            val safe = runCatching { InputPolicies.CHAT.validate(args.drop(1).joinToString(" ")) }.getOrNull()
                ?: return sender.sendMessage(Component.text("That message is invalid."))
            if (networkSend(sender, args[0], safe)) {
                lastPartnerName[sender.uniqueId] = args[0]
            } else sender.sendMessage(Component.text("That player is not online here or the proxy bridge is unavailable."))
            return
        }
        if (target.uniqueId == sender.uniqueId) return sender.sendMessage(Component.text("You cannot message yourself."))
        when (send(sender.uniqueId, target.uniqueId, args.drop(1).joinToString(" "))) {
            MessageDelivery.IGNORED -> sender.sendMessage(Component.text("That player is ignoring you."))
            MessageDelivery.INVALID -> sender.sendMessage(Component.text("That message is invalid."))
            MessageDelivery.SENT -> Unit
            else -> sender.sendMessage(Component.text("That player is no longer available."))
        }
    }

    private fun reply(sender: Player, args: Array<out String>) {
        if (args.isEmpty()) return sender.sendMessage(Component.text("Usage: /reply <message>"))
        val partner = lastPartner[sender.uniqueId]
        val target = partner?.let(plugin.server::getPlayer)
        val targetName = target?.name ?: lastPartnerName[sender.uniqueId]
            ?: return sender.sendMessage(Component.text("Nobody has messaged you recently."))
        message(sender, arrayOf(targetName, *args))
    }

    private fun ignore(sender: Player, args: Array<out String>, add: Boolean) {
        if (args.size != 1) return sender.sendMessage(Component.text("Usage: /${if (add) "ignore" else "unignore"} <player>"))
        val target = plugin.server.getPlayerExact(args[0])
        if (target != null) {
            if (target.uniqueId == sender.uniqueId) return sender.sendMessage(Component.text("You cannot ignore yourself."))
            updateLocalIgnore(sender.uniqueId, target.uniqueId, target.name, add)
            sender.sendMessage(Component.text("${target.name} is ${if (add) "now" else "no longer"} ignored."))
            networkIgnore(sender, target.name, add) { response ->
                if (!response.success) plugin.logger.fine("Proxy ignore synchronization failed: ${response.detail}")
            }
            return
        }
        networkIgnore(sender, args[0], add) { response ->
            schedulers.entity(sender) {
                val resolvedTarget = response.target
                if (response.success && resolvedTarget != null) {
                    val targetId = runCatching { UUID.fromString(resolvedTarget.playerId) }.getOrNull()
                    if (targetId != null) updateLocalIgnore(sender.uniqueId, targetId, resolvedTarget.playerName, response.ignored)
                }
                sender.sendMessage(Component.text(response.detail))
            }
        }
    }

    private fun listIgnored(sender: Player) {
        val names = ignored[sender.uniqueId].orEmpty().values.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        sender.sendMessage(Component.text(if (names.isEmpty()) "You are not ignoring anyone." else "Ignored: ${names.joinToString()}"))
    }

    private fun toggleSpy(sender: Player) {
        if (!sender.hasPermission(Permissions.MESSAGING_SOCIAL_SPY.node)) return deny(sender)
        val enabled = if (spies.remove(sender.uniqueId)) false else spies.add(sender.uniqueId)
        sender.sendMessage(Component.text("Social spy ${if (enabled) "enabled" else "disabled"}."))
    }

    private fun deliver(sender: Player, target: Player, message: String) {
        lastPartner[sender.uniqueId] = target.uniqueId
        lastPartner[target.uniqueId] = sender.uniqueId
        lastPartnerName[sender.uniqueId] = target.name
        lastPartnerName[target.uniqueId] = sender.name
        val values = arrayOf(
            Placeholder.unparsed("sender", sender.name),
            Placeholder.unparsed("target", target.name),
            Placeholder.unparsed("message", message),
        )
        schedulers.entity(sender) { sender.sendMessage(miniMessage.deserialize(config.outgoing, *values)) }
        schedulers.entity(target) { target.sendMessage(miniMessage.deserialize(config.incoming, *values)) }
        spies.asSequence().filter { it != sender.uniqueId && it != target.uniqueId }.mapNotNull(plugin.server::getPlayer).forEach { spy ->
            schedulers.entity(spy) { spy.sendMessage(miniMessage.deserialize(config.spy, *values)) }
        }
    }

    private fun deny(sender: Player) { sender.sendMessage(Component.text("You do not have permission to do that.")) }

    private fun synchronizeIgnores(player: Player) {
        networkIgnoreList(player) { response ->
            val networkEntries = response.entries.mapNotNull { entry ->
                runCatching { UUID.fromString(entry.playerId) }.getOrNull()?.let { it to entry.playerName }
            }.toMap()
            val localEntries = ignored[player.uniqueId].orEmpty().toMap()
            ignored[player.uniqueId] = ConcurrentHashMap(localEntries + networkEntries)
            localEntries.filterKeys { it !in networkEntries }.forEach { (_, name) ->
                networkIgnore(player, name, true) { }
            }
            persistIgnores()
        }
    }

    private fun updateLocalIgnore(owner: UUID, target: UUID, name: String, add: Boolean) {
        val entries = ignored.computeIfAbsent(owner) { ConcurrentHashMap() }
        if (add) entries[target] = name else entries.remove(target)
        persistIgnores()
    }

    private fun persistIgnores() {
        ignoreStore.save(ignored.mapValues { (_, entries) -> entries.toMap() })
    }
}
