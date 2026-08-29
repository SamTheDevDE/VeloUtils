// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import de.samthedev.veloutils.api.ConnectionOutcome
import de.samthedev.veloutils.common.BoundedExpiringMap
import de.samthedev.veloutils.common.DurationParser
import de.samthedev.veloutils.proxy.network.VelocityNetworkService
import de.samthedev.veloutils.proxy.ui.ChatUi
import de.samthedev.veloutils.proxy.util.ConfiguredMessages
import de.samthedev.veloutils.proxy.util.ConfiguredMiniMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Path
import java.time.Duration
import java.util.UUID

public data class MoveCommandDefinition(
    val name: String,
    val aliases: List<String>,
    val servers: List<String>,
    val permission: String,
    val cooldown: Duration,
)

public data class MessageCommandDefinition(
    val name: String,
    val aliases: List<String>,
    val permission: String,
    val messages: List<Component>,
    val cooldown: Duration,
)

public data class ConfiguredCommandDefinitions(
    val move: List<MoveCommandDefinition>,
    val message: List<MessageCommandDefinition>,
)

public object ConfiguredCommandLoader {
    public fun load(path: Path): ConfiguredCommandDefinitions {
        val root = YamlConfigurationLoader.builder().path(path).build().load()
        val move = root.node("move-commands").childrenMap().map { (key, node) ->
            val name = validateCommandName(key.toString())
            val servers = node.node("servers").getList(String::class.java, emptyList()).map(String::lowercase).distinct()
            require(servers.isNotEmpty()) { "commands.yml: move command $name requires at least one server" }
            MoveCommandDefinition(
                name,
                node.node("aliases").getList(String::class.java, emptyList()).map(::validateCommandName),
                servers,
                requireNotNull(node.node("permission").getString()?.takeIf(String::isNotBlank)) {
                    "commands.yml: move command $name requires a permission"
                },
                DurationParser.parse(node.node("cooldown").getString("3s")),
            )
        }
        val message = root.node("message-commands").childrenMap().map { (key, node) ->
            val name = validateCommandName(key.toString())
            val lines = node.node("messages").getList(String::class.java, emptyList())
            require(lines.isNotEmpty()) { "commands.yml: message command $name requires at least one message" }
            MessageCommandDefinition(
                name,
                node.node("aliases").getList(String::class.java, emptyList()).map(::validateCommandName),
                requireNotNull(node.node("permission").getString()?.takeIf(String::isNotBlank)) {
                    "commands.yml: message command $name requires a permission"
                },
                lines.mapIndexed { index, line ->
                    runCatching { ConfiguredMiniMessage.deserialize(line) }.getOrElse {
                        throw IllegalArgumentException(
                            "commands.yml: message-commands.$name.messages[$index]: invalid MiniMessage: ${it.message}",
                            it,
                        )
                    }
                },
                DurationParser.parse(node.node("cooldown").getString("5s")),
            )
        }
        val labels = (move.flatMap { listOf(it.name) + it.aliases } + message.flatMap { listOf(it.name) + it.aliases })
        require(labels.size == labels.map(String::lowercase).distinct().size) { "commands.yml contains duplicate command labels" }
        return ConfiguredCommandDefinitions(move, message)
    }

    private fun validateCommandName(value: String): String = value.lowercase().also {
        require(Regex("[a-z0-9_-]{1,32}").matches(it)) { "Invalid configured command name: $value" }
    }
}

public class ConfiguredMoveCommand(
    private val definition: MoveCommandDefinition,
    private val network: VelocityNetworkService,
    private val messages: ConfiguredMessages,
    private val scope: CoroutineScope,
) : SimpleCommand {
    private val cooldowns = BoundedExpiringMap<UUID, Unit>(100_000, definition.cooldown)

    override fun execute(invocation: SimpleCommand.Invocation) {
        val player = invocation.source() as? Player
        if (player == null) {
            invocation.source().sendMessage(messages.render("players-only"))
            return
        }
        if (!player.hasPermission(definition.permission)) {
            player.sendMessage(messages.render("no-permission"))
            return
        }
        if (cooldowns[player.uniqueId] != null) {
            player.sendMessage(ChatUi.warning("Please wait before connecting again."))
            return
        }
        cooldowns.put(player.uniqueId, Unit)
        scope.launch {
            when (val outcome = network.connect(player.uniqueId, definition.servers)) {
                is ConnectionOutcome.Connected -> player.sendMessage(messages.render("connecting", mapOf("server" to Component.text(outcome.server))))
                else -> {
                    cooldowns.remove(player.uniqueId)
                    player.sendMessage(messages.render("server-unavailable"))
                }
            }
        }
    }
}

public class ConfiguredMessageCommand(
    private val definition: MessageCommandDefinition,
    private val messages: ConfiguredMessages,
) : SimpleCommand {
    private val cooldowns = BoundedExpiringMap<UUID, Unit>(100_000, definition.cooldown)

    override fun execute(invocation: SimpleCommand.Invocation) {
        if (!invocation.source().hasPermission(definition.permission)) {
            invocation.source().sendMessage(messages.render("no-permission"))
            return
        }
        val player = invocation.source() as? Player
        if (player != null && cooldowns[player.uniqueId] != null) {
            player.sendMessage(ChatUi.warning("Please wait before using this command again."))
            return
        }
        player?.let { cooldowns.put(it.uniqueId, Unit) }
        definition.messages.forEach(invocation.source()::sendMessage)
    }
}
