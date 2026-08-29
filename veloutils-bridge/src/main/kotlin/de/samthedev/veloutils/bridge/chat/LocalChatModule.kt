// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge.chat

import de.samthedev.veloutils.api.ChatChannelDefinition
import de.samthedev.veloutils.api.ChatChannelScope
import de.samthedev.veloutils.api.ChatService
import de.samthedev.veloutils.api.PlaceholderContext
import de.samthedev.veloutils.api.PlaceholderService
import de.samthedev.veloutils.bridge.server.PlatformSchedulers
import de.samthedev.veloutils.common.DurationParser
import de.samthedev.veloutils.common.Permissions
import de.samthedev.veloutils.core.chat.ChatDecision
import de.samthedev.veloutils.core.chat.ChatPolicy
import de.samthedev.veloutils.core.chat.ChatPolicyConfig
import de.samthedev.veloutils.core.module.ManagedModule
import io.papermc.paper.event.player.AsyncChatEvent
import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.net.URI
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

public data class LocalChatConfig(
    val defaultChannel: String,
    val channels: Map<String, ChatChannelDefinition>,
    val mentionFormat: String,
    val mentionSound: Sound?,
    val urlsEnabled: Boolean,
    val styledPlaceholders: Set<String>,
    val policy: ChatPolicyConfig,
) {
    public companion object {
        public fun load(plugin: JavaPlugin): LocalChatConfig {
            val path = plugin.dataPath.resolve("modules/chat.yml")

            if (Files.notExists(path)) {
                plugin.saveResource("modules/chat.yml", false)
            }

            val root = YamlConfigurationLoader.builder()
                .path(path)
                .build()
                .load()

            require(root.node("config-version").getInt(1) == 1) {
                "Unsupported modules/chat.yml config-version"
            }

            val regex = root.node("filters", "regex")
                .getList(String::class.java, emptyList())
                .map { expression ->
                    runCatching {
                        Regex(
                            expression,
                            RegexOption.IGNORE_CASE,
                        )
                    }.getOrElse {
                        throw IllegalArgumentException(
                            "Invalid chat filter regex '$expression': ${it.message}",
                        )
                    }
                }

            val channels = root.node("channels")
                .childrenMap()
                .map { (rawId, node) ->
                    val id = rawId.toString().lowercase()
                    id to parseChannel(id, node)
                }
                .toMap()
                .ifEmpty {
                    mapOf(
                        "server" to ChatChannelDefinition(
                            "server",
                            root.node("format").getString(
                                "<gray><player></gray> " +
                                    "<dark_gray>»</dark_gray> " +
                                    "<white><message></white>",
                            ),
                        ),
                    )
                }

            val defaultChannel = root.node("default-channel")
                .getString(channels.keys.first())
                .lowercase()

            require(defaultChannel in channels) {
                "Chat default-channel '$defaultChannel' does not exist"
            }

            val mentionSound = root.node("mentions", "sound")
                .getString("block.note_block.pling")
                .trim()
                .takeIf(String::isNotEmpty)
                ?.let { key ->
                    NamespacedKey.fromString(key)
                        ?.let(Registry.SOUND_EVENT::get)
                }

            val styledPlaceholders = root
                .node("formatting", "styled-placeholders")
                .getList(
                    String::class.java,
                    DEFAULT_STYLED_PLACEHOLDERS,
                )
                .asSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map(String::lowercase)
                .onEach { key ->
                    require(
                        ChatFormatPlaceholderRenderer
                            .isValidPlaceholderKey(key),
                    ) {
                        "Invalid styled chat placeholder '$key'"
                    }
                }
                .toSet()

            return LocalChatConfig(
                defaultChannel = defaultChannel,
                channels = channels,
                mentionFormat = root.node(
                    "mentions",
                    "format",
                ).getString(
                    "<yellow><bold>@<player></bold></yellow>",
                ),
                mentionSound = mentionSound,
                urlsEnabled = root.node(
                    "urls",
                    "enabled",
                ).getBoolean(true),
                styledPlaceholders = styledPlaceholders,
                policy = ChatPolicyConfig(
                    DurationParser.parse(
                        root.node("cooldown").getString("1s"),
                    ),
                    DurationParser.parse(
                        root.node(
                            "duplicate-window",
                        ).getString("10s"),
                    ),
                    root.node(
                        "maximum-length",
                    ).getInt(256),
                    root.node(
                        "caps",
                        "minimum-letters",
                    ).getInt(8),
                    root.node(
                        "caps",
                        "maximum-ratio",
                    ).getDouble(0.75),
                    root.node(
                        "filters",
                        "words",
                    ).getList(
                        String::class.java,
                        emptyList(),
                    ),
                    regex,
                ),
            )
        }

        private fun parseChannel(
            id: String,
            node: ConfigurationNode,
        ): ChatChannelDefinition {
            require(
                id.matches(
                    Regex("[a-z][a-z0-9_-]{0,31}"),
                ),
            ) {
                "Invalid chat channel id '$id'"
            }

            val scope = runCatching {
                ChatChannelScope.valueOf(
                    node.node("scope")
                        .getString("server")
                        .uppercase(),
                )
            }.getOrElse {
                throw IllegalArgumentException(
                    "Channel '$id' scope must be server, radius, or network",
                )
            }

            val radius = node.node("radius")
                .getDouble(100.0)
                .takeIf {
                    scope == ChatChannelScope.RADIUS
                }

            require(
                radius == null ||
                    radius in 1.0..10_000.0,
            ) {
                "Channel '$id' radius must be between 1 and 10000"
            }

            return ChatChannelDefinition(
                id,
                node.node("format").getString(
                    "<gray>[<channel>] <player>:</gray> " +
                        "<white><message></white>",
                ),
                scope,
                radius,
                node.node("permission")
                    .getString()
                    ?.trim()
                    ?.takeIf(String::isNotEmpty),
                node.node("mentions")
                    .getBoolean(true),
            )
        }

        private val DEFAULT_STYLED_PLACEHOLDERS =
            listOf(
                "papi_luckperms_prefix",
                "papi_luckperms_suffix",
            )
    }
}

public class LocalChatModule(
    private val plugin: JavaPlugin,
    private val schedulers: PlatformSchedulers,
    private val serverId: String,
    private val config: LocalChatConfig,
    private val placeholders: PlaceholderService?,
    private val networkChat: (
        Player,
        String,
        String,
    ) -> Unit,
) : ManagedModule,
    Listener,
    CommandExecutor,
    TabCompleter,
    ChatService {

    private val miniMessage =
        MiniMessage.miniMessage()

    private val plain =
        PlainTextComponentSerializer.plainText()

    private val policy =
        ChatPolicy(config.policy)

    private val formatPlaceholders =
        ChatFormatPlaceholderRenderer(
            config.styledPlaceholders,
        )

    private val muted =
        AtomicBoolean()

    private val selectedChannels =
        ConcurrentHashMap<UUID, String>()

    private val addonChannels =
        ConcurrentHashMap<
            String,
            ChatChannelDefinition
        >()

    override fun validate() {
        config.channels.values.forEach(
            ::validateChannel,
        )

        miniMessage.deserialize(
            config.mentionFormat,
            Placeholder.unparsed(
                "player",
                "Player",
            ),
        )
    }

    override fun enable() {
        plugin.server.pluginManager
            .registerEvents(this, plugin)

        plugin.getCommand("chat")
            ?.setExecutor(this)

        plugin.getCommand("channel")
            ?.setExecutor(this)

        plugin.getCommand("chat")
            ?.tabCompleter = this

        plugin.getCommand("channel")
            ?.tabCompleter = this
    }

    override fun disable() {
        plugin.getCommand("chat")
            ?.setExecutor(null)

        plugin.getCommand("channel")
            ?.setExecutor(null)

        plugin.getCommand("chat")
            ?.tabCompleter = null

        plugin.getCommand("channel")
            ?.tabCompleter = null

        HandlerList.unregisterAll(this)

        policy.clear()
        selectedChannels.clear()
        addonChannels.clear()
    }

    override fun channels(): Set<String> =
        (
            config.channels.keys +
                addonChannels.keys
            ).toSortedSet()

    override fun activeChannel(
        playerId: UUID,
    ): String =
        selectedChannels[playerId]
            ?.takeIf {
                channel(it) != null
            }
            ?: config.defaultChannel

    override fun select(
        playerId: UUID,
        channel: String,
    ): Boolean {
        val selected =
            channel(channel)
                ?: return false

        val player =
            plugin.server.getPlayer(playerId)
                ?: return false

        if (
            selected.permission
                ?.let(player::hasPermission) ==
            false
        ) {
            return false
        }

        selectedChannels[playerId] =
            selected.id

        return true
    }

    override fun register(
        channel: ChatChannelDefinition,
    ): AutoCloseable {
        validateChannel(channel)

        require(
            channel.scope !=
                ChatChannelScope.NETWORK,
        ) {
            "Addon-provided network channels require a proxy-side contract"
        }

        check(
            channel.id !in config.channels &&
                addonChannels.putIfAbsent(
                    channel.id,
                    channel,
                ) == null,
        ) {
            "Chat channel '${channel.id}' is already registered"
        }

        return AutoCloseable {
            addonChannels.remove(
                channel.id,
                channel,
            )

            selectedChannels.entries
                .removeIf {
                    it.value == channel.id
                }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true,
    )
    public fun onChat(
        event: AsyncChatEvent,
    ) {
        event.isCancelled = true

        val input =
            plain.serialize(event.message())

        schedulers.entity(event.player) {
            processChat(
                event.player,
                input,
            )
        }
    }

    @EventHandler
    public fun onQuit(
        event: PlayerQuitEvent,
    ) {
        policy.remove(event.player.uniqueId)

        selectedChannels.remove(
            event.player.uniqueId,
        )
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        if (
            command.name.equals(
                "channel",
                true,
            )
        ) {
            return channelCommand(
                sender,
                args,
            )
        }

        if (
            !sender.hasPermission(
                Permissions.CHAT_MANAGE.node,
            )
        ) {
            sender.sendMessage(
                "You do not have permission to manage chat.",
            )

            return true
        }

        when (
            args.firstOrNull()
                ?.lowercase()
        ) {
            "mute" ->
                muted.set(true)

            "unmute" ->
                muted.set(false)

            "clear" ->
                Unit

            else -> {
                sender.sendMessage(
                    "Usage: /chat <mute|unmute|clear>",
                )

                return true
            }
        }

        val action =
            args[0].lowercase()

        val actor =
            sender.name

        schedulers.global {
            if (action == "clear") {
                repeat(40) {
                    plugin.server.broadcast(
                        Component.empty(),
                    )
                }
            }

            plugin.server.broadcast(
                Component.text(
                    "Chat $action requested by $actor.",
                ),
            )
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        if (args.size != 1) {
            return emptyList()
        }

        val options =
            if (
                command.name.equals(
                    "chat",
                    true,
                )
            ) {
                if (
                    sender.hasPermission(
                        Permissions.CHAT_MANAGE.node,
                    )
                ) {
                    listOf(
                        "mute",
                        "unmute",
                        "clear",
                    )
                } else {
                    emptyList()
                }
            } else {
                val player =
                    sender as? Player
                        ?: return emptyList()

                channels().filter { id ->
                    channel(id)
                        ?.permission
                        ?.let(
                            player::hasPermission,
                        ) != false
                }
            }

        return options.filter {
            it.startsWith(
                args[0],
                true,
            )
        }
    }

    private fun channelCommand(
        sender: CommandSender,
        args: Array<out String>,
    ): Boolean {
        val player =
            sender as? Player
                ?: run {
                    sender.sendMessage(
                        "This command can only be used by a player.",
                    )

                    return true
                }

        if (args.isEmpty()) {
            player.sendMessage(
                Component.text(
                    "Channels: " +
                        "${channels().joinToString()} " +
                        "(current: " +
                        "${activeChannel(player.uniqueId)})",
                ),
            )

            return true
        }

        if (
            select(
                player.uniqueId,
                args[0].lowercase(),
            )
        ) {
            player.sendMessage(
                Component.text(
                    "Chat channel selected: " +
                        activeChannel(
                            player.uniqueId,
                        ),
                ),
            )
        } else {
            player.sendMessage(
                Component.text(
                    "That channel does not exist or you cannot use it.",
                ),
            )
        }

        return true
    }

    private fun processChat(
        player: Player,
        input: String,
    ) {
        if (
            muted.get() &&
            !player.hasPermission(
                Permissions
                    .CHAT_MUTE_BYPASS
                    .node,
            )
        ) {
            player.sendMessage(
                Component.text(
                    "Chat is currently muted.",
                ),
            )

            return
        }

        val accepted =
            when (
                val decision =
                    policy.evaluate(
                        player.uniqueId,
                        input,
                        player.hasPermission(
                            Permissions
                                .CHAT_COOLDOWN_BYPASS
                                .node,
                        ),
                    )
            ) {
                is ChatDecision.Accepted ->
                    decision.message

                is ChatDecision.Rejected -> {
                    player.sendMessage(
                        Component.text(
                            rejectionMessage(
                                decision.reason,
                            ),
                        ),
                    )

                    return
                }
            }

        val channel =
            channel(
                activeChannel(
                    player.uniqueId,
                ),
            )
                ?: config.channels
                    .getValue(
                        config.defaultChannel,
                    )

        if (
            channel.permission
                ?.let(player::hasPermission) ==
            false
        ) {
            selectedChannels.remove(
                player.uniqueId,
            )

            player.sendMessage(
                Component.text(
                    "You no longer have permission to use that chat channel.",
                ),
            )

            return
        }

        if (
            channel.scope ==
            ChatChannelScope.NETWORK
        ) {
            networkChat(
                player,
                channel.id,
                accepted,
            )

            return
        }

        val sourceWorld =
            player.world.uid

        val sourceLocation =
            player.location

        val radiusSquared =
            channel.radius?.let(::square)

        val format =
            resolveFormat(
                player,
                channel.format,
            )

        plugin.server.onlinePlayers
            .forEach { viewer ->
                schedulers.entity(viewer) {
                    if (
                        channel.scope ==
                        ChatChannelScope.RADIUS &&
                        (
                            viewer.world.uid !=
                                sourceWorld ||
                                viewer.location
                                    .distanceSquared(
                                        sourceLocation,
                                    ) >
                                checkNotNull(
                                    radiusSquared,
                                )
                            )
                    ) {
                        return@entity
                    }

                    val mentioned =
                        channel.mentions &&
                            MENTION
                                .findAll(accepted)
                                .any {
                                    it.groupValues[1]
                                        .equals(
                                            viewer.name,
                                            true,
                                        )
                                }

                    val message =
                        renderUserMessage(
                            accepted,
                            viewer,
                            channel.mentions,
                        )

                    /*
                     * Dynamic {placeholders} have already been converted into
                     * component resolvers by ChatFormatPlaceholderRenderer.
                     *
                     * Player and message are also inserted as Components,
                     * preventing chat text from modifying the trusted format.
                     */
                    val resolvers =
                        buildList<TagResolver> {
                            addAll(
                                format.resolvers,
                            )

                            add(
                                Placeholder.component(
                                    "player",
                                    player.displayName(),
                                ),
                            )

                            add(
                                Placeholder.component(
                                    "message",
                                    message,
                                ),
                            )

                            add(
                                Placeholder.unparsed(
                                    "channel",
                                    channel.id,
                                ),
                            )
                        }

                    viewer.sendMessage(
                        miniMessage.deserialize(
                            format.template,
                            *resolvers
                                .toTypedArray(),
                        ),
                    )

                    if (mentioned) {
                        config.mentionSound
                            ?.let { sound ->
                                viewer.playSound(
                                    viewer.location,
                                    sound,
                                    1.0f,
                                    1.2f,
                                )
                            }
                    }
                }
            }
    }

    private fun renderUserMessage(
        message: String,
        viewer: Player,
        mentions: Boolean,
    ): Component {
        var result =
            Component.empty()

        var cursor = 0

        TOKEN.findAll(message)
            .forEach { match ->
                if (
                    match.range.first >
                    cursor
                ) {
                    result = result.append(
                        Component.text(
                            message.substring(
                                cursor,
                                match.range.first,
                            ),
                        ),
                    )
                }

                val token =
                    match.value

                result = result.append(
                    when {
                        config.urlsEnabled &&
                            token.startsWith(
                                "http",
                                true,
                            ) &&
                            validUrl(token) ->
                            Component.text(
                                token,
                                NamedTextColor.AQUA,
                            )
                                .decorate(
                                    TextDecoration.UNDERLINED,
                                )
                                .clickEvent(
                                    ClickEvent.openUrl(
                                        token,
                                    ),
                                )
                                .hoverEvent(
                                    HoverEvent.showText(
                                        Component.text(
                                            "Open link",
                                        ),
                                    ),
                                )

                        mentions &&
                            token.drop(1)
                                .equals(
                                    viewer.name,
                                    true,
                                ) ->
                            miniMessage.deserialize(
                                config.mentionFormat,
                                Placeholder.unparsed(
                                    "player",
                                    viewer.name,
                                ),
                            )

                        else ->
                            Component.text(token)
                    },
                )

                cursor =
                    match.range.last + 1
            }

        if (cursor < message.length) {
            result = result.append(
                Component.text(
                    message.substring(cursor),
                ),
            )
        }

        return result
    }

    private fun resolveFormat(
        player: Player,
        template: String,
    ): ResolvedChatFormat {
        val keys =
            formatPlaceholders.keys(template)

        if (keys.isEmpty()) {
            return ResolvedChatFormat(
                template = template,
                resolvers = emptyList(),
            )
        }

        val values =
            placeholders
                ?.resolve(
                    PlaceholderContext(
                        player.uniqueId,
                        serverId,
                        player.world.name,
                    ),
                )
                .orEmpty()
                .toMutableMap()

        if (
            plugin.server.pluginManager
                .isPluginEnabled(
                    "PlaceholderAPI",
                )
        ) {
            keys
                .filter {
                    it.startsWith(
                        PAPI_PREFIX,
                    )
                }
                .forEach { key ->
                    val identifier =
                        key.removePrefix(
                            PAPI_PREFIX,
                        )

                    val placeholder =
                        "%$identifier%"

                    values[key] =
                        PlaceholderAPI
                            .setPlaceholders(
                                player,
                                placeholder,
                            )
                            .takeIf {
                                it != placeholder
                            }
                            .orEmpty()
                }
        }

        values.putIfAbsent(
            "server",
            serverId,
        )

        values.putIfAbsent(
            "world",
            player.world.name,
        )

        return formatPlaceholders.resolve(
            template,
            values,
        )
    }

    private fun channel(
        id: String,
    ): ChatChannelDefinition? =
        addonChannels[id.lowercase()]
            ?: config.channels[id.lowercase()]

    private fun validateChannel(
        channel: ChatChannelDefinition,
    ) {
        require(
            channel.id.matches(
                Regex(
                    "[a-z][a-z0-9_-]{0,31}",
                ),
            ),
        ) {
            "Invalid chat channel id '${channel.id}'"
        }

        val radius =
            channel.radius

        require(
            radius == null ||
                radius in 1.0..10_000.0,
        ) {
            "Invalid chat channel radius"
        }

        require(
            channel.scope !=
                ChatChannelScope.RADIUS ||
                radius != null,
        ) {
            "Radius channels require a radius"
        }

        miniMessage.deserialize(
            channel.format,
            Placeholder.component(
                "player",
                Component.text("Player"),
            ),
            Placeholder.component(
                "message",
                Component.text("Message"),
            ),
            Placeholder.unparsed(
                "channel",
                channel.id,
            ),
        )
    }

    private fun rejectionMessage(
        reason: ChatDecision.Reason,
    ): String =
        when (reason) {
            ChatDecision.Reason.BLANK ->
                "Chat messages cannot be blank."

            ChatDecision.Reason.TOO_LONG ->
                "That chat message is too long."

            ChatDecision.Reason.COOLDOWN ->
                "Please wait before chatting again."

            ChatDecision.Reason.DUPLICATE ->
                "Please do not repeat the same message."
        }

    private fun validUrl(
        value: String,
    ): Boolean =
        runCatching {
            URI(value)
        }
            .getOrNull()
            ?.let {
                it.scheme in
                    setOf(
                        "http",
                        "https",
                    ) &&
                    it.host != null
            } == true

    private companion object {
        val MENTION: Regex =
            Regex(
                "(?i)" +
                    "(?<![A-Za-z0-9_])" +
                    "@([A-Za-z0-9_]{1,16})",
            )

        val TOKEN: Regex =
            Regex(
                "(?i)" +
                    "https?://[^\\s<>{}]+" +
                    "|" +
                    "(?<![A-Za-z0-9_])" +
                    "@[A-Za-z0-9_]{1,16}",
            )

        const val PAPI_PREFIX: String =
            "papi_"

        fun square(
            value: Double,
        ): Double =
            value * value
    }
}