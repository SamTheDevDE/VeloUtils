// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge.presentation

import de.samthedev.veloutils.bridge.server.PlatformSchedulers
import de.samthedev.veloutils.common.DurationParser
import de.samthedev.veloutils.core.condition.BasicSelectors
import de.samthedev.veloutils.core.condition.Condition
import de.samthedev.veloutils.core.placeholder.TemplateRenderer
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Team
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Files
import java.time.Duration

internal data class AnimatedTemplate(private val frames: List<TemplateRenderer>) {
    fun render(tick: Long, values: Map<String, String>): Component =
        frames[Math.floorMod(tick, frames.size.toLong()).toInt()].render(values)

    fun requiredKeys(): Set<String> = frames.flatMapTo(linkedSetOf(), TemplateRenderer::requiredKeys)

    companion object {
        fun compile(value: String): AnimatedTemplate = AnimatedTemplate(
            value.split("||").map(String::trim).filter(String::isNotEmpty).ifEmpty { listOf("") }.map(::TemplateRenderer),
        )
    }
}

internal data class Selection(val condition: Condition, val permissionKeys: Set<String>)

internal data class Design(
    val id: String,
    val priority: Int,
    val selection: Selection,
    val header: AnimatedTemplate,
    val footer: AnimatedTemplate,
    val playerListName: AnimatedTemplate,
    val sortOrder: Int,
) {
    fun requiredKeys(): Set<String> = buildSet {
        addAll(header.requiredKeys())
        addAll(footer.requiredKeys())
        addAll(playerListName.requiredKeys())
    }
}

internal data class ScoreboardDefinition(
    val id: String,
    val priority: Int,
    val selection: Selection,
    val title: AnimatedTemplate,
    val lines: List<AnimatedTemplate>,
) {
    fun requiredKeys(): Set<String> = buildSet {
        addAll(title.requiredKeys())
        lines.forEach { addAll(it.requiredKeys()) }
    }
}

internal data class NametagDefinition(
    val id: String,
    val priority: Int,
    val selection: Selection,
    val prefix: AnimatedTemplate,
    val suffix: AnimatedTemplate,
    val color: NamedTextColor?,
    val visibility: Team.OptionStatus,
    val collision: Team.OptionStatus,
    val sortOrder: Int,
) {
    fun requiredKeys(): Set<String> = prefix.requiredKeys() + suffix.requiredKeys()
}

internal data class BossBarConfig(
    val enabled: Boolean,
    val selection: Selection,
    val text: AnimatedTemplate,
    val progress: Float,
    val color: BossBar.Color,
    val overlay: BossBar.Overlay,
    val priority: Int,
)

internal data class PresentationConfig(
    val refreshInterval: Duration,
    val tabEnabled: Boolean,
    val designs: List<Design>,
    val scoreboardEnabled: Boolean,
    val scoreboards: List<ScoreboardDefinition>,
    val nametagEnabled: Boolean,
    val nametags: List<NametagDefinition>,
    val bossBar: BossBarConfig,
) {
    val permissionKeys: Set<String> = buildSet {
        designs.forEach { addAll(it.selection.permissionKeys) }
        scoreboards.forEach { addAll(it.selection.permissionKeys) }
        nametags.forEach { addAll(it.selection.permissionKeys) }
        addAll(bossBar.selection.permissionKeys)
    }
    val papiKeys: Set<String> = buildSet {
        designs.forEach { addAll(it.requiredKeys()) }
        scoreboards.forEach { addAll(it.requiredKeys()) }
        nametags.forEach { addAll(it.requiredKeys()) }
        addAll(bossBar.text.requiredKeys())
    }.filterTo(linkedSetOf()) { it.startsWith(PAPI_PREFIX) }

    companion object {
        fun load(plugin: JavaPlugin, schedulers: PlatformSchedulers): PresentationConfig {
            val path = plugin.dataPath.resolve("modules/presentation.yml")
            if (Files.notExists(path)) plugin.saveResource("modules/presentation.yml", false)
            val root = YamlConfigurationLoader.builder().path(path).build().load()
            require(root.node("config-version").getInt(1) == 1) { "Unsupported modules/presentation.yml config-version" }
            val interval = DurationParser.parse(root.node("refresh-interval").getString("1s"))
            require(interval in Duration.ofMillis(250)..Duration.ofMinutes(1)) {
                "Presentation refresh-interval must be between 250ms and 1m"
            }
            val designs = root.node("designs").childrenMap().map { (rawId, node) -> parseDesign(rawId.toString(), node) }
                .sortedByDescending(Design::priority)
            require(designs.isNotEmpty()) { "At least one presentation design is required" }
            val scoreboardRoot = root.node("scoreboard")
            val scoreboards = scoreboardRoot.node("definitions").childrenMap().map { (rawId, node) ->
                parseScoreboard(rawId.toString(), node)
            }.sortedByDescending(ScoreboardDefinition::priority)
            val scoreboardEnabled = scoreboardRoot.node("enabled").getBoolean(false)
            require(!scoreboardEnabled || scoreboards.isNotEmpty()) { "Enabled scoreboards require at least one definition" }
            val nametagRoot = root.node("nametags")
            val nametags = nametagRoot.node("definitions").childrenMap().map { (rawId, node) ->
                parseNametag(rawId.toString(), node)
            }.sortedByDescending(NametagDefinition::priority)
            val nametagEnabled = nametagRoot.node("enabled").getBoolean(false)
            require(!nametagEnabled || nametags.isNotEmpty()) { "Enabled nametags require at least one definition" }
            val boss = root.node("bossbar")
            if (schedulers.isFolia && (scoreboardEnabled || nametagEnabled)) {
                plugin.logger.info("Scoreboard and nametag state will be mutated on Folia's global-region scheduler and assigned on entity schedulers.")
            }
            return PresentationConfig(
                interval,
                root.node("tab", "enabled").getBoolean(true),
                designs,
                scoreboardEnabled,
                scoreboards,
                nametagEnabled,
                nametags,
                BossBarConfig(
                    boss.node("enabled").getBoolean(false),
                    parseSelection(boss),
                    animated(boss.node("text"), "<gradient:#7c3aed:#a855f7>VeloUtils</gradient>"),
                    boss.node("progress").getFloat(1.0f).coerceIn(0.0f, 1.0f),
                    enumValue(boss.node("color").getString("purple"), BossBar.Color.PURPLE),
                    enumValue(boss.node("overlay").getString("progress"), BossBar.Overlay.PROGRESS),
                    boss.node("priority").getInt(0),
                ),
            )
        }

        private fun parseDesign(id: String, node: ConfigurationNode): Design {
            validId("presentation design", id)
            return Design(
                id,
                node.node("priority").getInt(0),
                parseSelection(node),
                animated(node.node("header"), "<gradient:#7c3aed:#a855f7><bold>VeloUtils</bold></gradient>"),
                animated(node.node("footer"), "<gray>{veloutils_network_online} online</gray>"),
                animated(node.node("player-list-name"), "<white>{player}</white>"),
                node.node("sort-order").getInt(1_000).coerceIn(0, 32_767),
            )
        }

        private fun parseScoreboard(id: String, node: ConfigurationNode): ScoreboardDefinition {
            validId("scoreboard", id)
            val lines = node.node("lines").getList(String::class.java, emptyList()).map(AnimatedTemplate::compile)
            require(lines.size <= 15) { "Scoreboard '$id' may contain at most 15 lines" }
            return ScoreboardDefinition(
                id,
                node.node("priority").getInt(0),
                parseSelection(node),
                animated(node.node("title"), "<gradient:#7c3aed:#a855f7><bold>VeloUtils</bold></gradient>"),
                lines,
            )
        }

        private fun parseNametag(id: String, node: ConfigurationNode): NametagDefinition {
            validId("nametag", id)
            return NametagDefinition(
                id,
                node.node("priority").getInt(0),
                parseSelection(node),
                animated(node.node("prefix"), ""),
                animated(node.node("suffix"), ""),
                namedColor(node.node("color").getString("white")),
                enumValue(node.node("visibility").getString("always"), Team.OptionStatus.ALWAYS),
                enumValue(node.node("collision").getString("always"), Team.OptionStatus.ALWAYS),
                node.node("sort-order").getInt(1_000).coerceIn(0, 9_999),
            )
        }

        private fun parseSelection(node: ConfigurationNode): Selection {
            val permissions = node.node("permissions").getList(String::class.java, emptyList()).toSet()
            val whenExpression = node.node("when").getString()?.trim()?.takeIf(String::isNotEmpty)
            val advancedPermissions = whenExpression.orEmpty().let { source ->
                PERMISSION.findAll(source).map { it.groupValues[1] }.toSet()
            }
            val selectors = BasicSelectors(
                node.node("servers").getList(String::class.java, emptyList()).toSet(),
                node.node("worlds").getList(String::class.java, emptyList()).toSet(),
                node.node("groups").getList(String::class.java, emptyList()).toSet(),
                permissions,
            )
            return Selection(selectors.compile(whenExpression), permissions + advancedPermissions)
        }

        private fun animated(node: ConfigurationNode, fallback: String): AnimatedTemplate {
            val value = if (node.isList) node.getList(String::class.java, emptyList()).joinToString("\n") else node.getString(fallback)
            return AnimatedTemplate.compile(value)
        }

        private fun namedColor(value: String): NamedTextColor? = when (value.lowercase()) {
            "black" -> NamedTextColor.BLACK
            "dark_blue" -> NamedTextColor.DARK_BLUE
            "dark_green" -> NamedTextColor.DARK_GREEN
            "dark_aqua" -> NamedTextColor.DARK_AQUA
            "dark_red" -> NamedTextColor.DARK_RED
            "dark_purple" -> NamedTextColor.DARK_PURPLE
            "gold" -> NamedTextColor.GOLD
            "gray" -> NamedTextColor.GRAY
            "dark_gray" -> NamedTextColor.DARK_GRAY
            "blue" -> NamedTextColor.BLUE
            "green" -> NamedTextColor.GREEN
            "aqua" -> NamedTextColor.AQUA
            "red" -> NamedTextColor.RED
            "light_purple" -> NamedTextColor.LIGHT_PURPLE
            "yellow" -> NamedTextColor.YELLOW
            "white" -> NamedTextColor.WHITE
            "none" -> null
            else -> throw IllegalArgumentException("Unknown nametag color '$value'")
        }

        private fun validId(kind: String, id: String) {
            require(id.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "Invalid $kind id '$id'" }
        }

        private inline fun <reified T : Enum<T>> enumValue(value: String, fallback: T): T =
            runCatching { enumValueOf<T>(value.uppercase()) }.getOrDefault(fallback)

        private val PERMISSION: Regex = Regex("permission\\(['\"]([^'\"]+)['\"]\\)")
        private const val PAPI_PREFIX: String = "papi_"
    }
}
