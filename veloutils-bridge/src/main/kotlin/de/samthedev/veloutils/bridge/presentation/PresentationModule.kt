// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge.presentation

import de.samthedev.veloutils.api.PlaceholderContext
import de.samthedev.veloutils.api.PlaceholderService
import de.samthedev.veloutils.api.PresentationBossBarColor
import de.samthedev.veloutils.api.PresentationBossBarOverlay
import de.samthedev.veloutils.api.PresentationService
import de.samthedev.veloutils.api.PresentationSnapshot
import de.samthedev.veloutils.api.TemporaryBossBarRequest
import de.samthedev.veloutils.bridge.afk.AfkStateService
import de.samthedev.veloutils.bridge.server.PlatformSchedulers
import de.samthedev.veloutils.core.condition.SelectionContext
import de.samthedev.veloutils.core.module.ManagedModule
import de.samthedev.veloutils.core.placeholder.TemplateRenderer
import io.papermc.paper.scoreboard.numbers.NumberFormat
import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class PresentationModule(
    private val plugin: JavaPlugin,
    private val schedulers: PlatformSchedulers,
    private val serverId: String,
    private val config: PresentationConfig,
    private val placeholders: PlaceholderService?,
    private val afk: () -> AfkStateService?,
) : ManagedModule, Listener, PresentationService {
    private data class RenderedTab(
        val design: String,
        val header: Component,
        val footer: Component,
        val listName: Component,
        val order: Int,
    )

    private data class PlayerState(
        val playerId: UUID,
        val name: String,
        val world: String,
        val group: String?,
        val permissions: Set<String>,
        val values: Map<String, String>,
        val frame: Long,
    ) {
        fun selectionContext(serverId: String): SelectionContext = SelectionContext(serverId, world, group, permissions)
    }

    private data class ScoreboardRender(val definition: String, val title: Component, val lines: List<Component>)

    private data class TeamRender(
        val teamName: String,
        val entry: String,
        val prefix: Component,
        val suffix: Component,
        val color: NamedTextColor?,
        val visibility: Team.OptionStatus,
        val collision: Team.OptionStatus,
    )

    private data class TeamView(val team: Team, val rendered: TeamRender)

    private class BoardView(val scoreboard: Scoreboard) {
        var objective: Objective? = null
        var definition: String? = null
        var title: Component? = null
        var lines: List<Component> = emptyList()
        val teams: MutableMap<UUID, TeamView> = mutableMapOf()
    }

    private data class TemporaryEntry(val request: TemporaryBossBarRequest, val renderer: TemplateRenderer)
    private data class ActiveBar(val bossBar: BossBar, var source: String)

    private val active = AtomicBoolean()
    private val renderedTabs = ConcurrentHashMap<UUID, RenderedTab>()
    private val playerStates = ConcurrentHashMap<UUID, PlayerState>()
    private val boards = ConcurrentHashMap<UUID, BoardView>()
    private val boardCreationPending = ConcurrentHashMap.newKeySet<UUID>()
    private val previousBoards = ConcurrentHashMap<UUID, Scoreboard>()
    private val bars = ConcurrentHashMap<UUID, ActiveBar>()
    private val temporaryBars = ConcurrentHashMap<String, TemporaryEntry>()
    private val tick = AtomicLong()
    private var task: AutoCloseable? = null

    override fun enable() {
        active.set(true)
        plugin.server.pluginManager.registerEvents(this, plugin)
        val ticks = (config.refreshInterval.toMillis() / 50L).coerceAtLeast(1L)
        task = schedulers.repeatGlobal(ticks, ticks, ::refreshAll)
        refreshAll()
    }

    override fun disable() {
        active.set(false)
        task?.close()
        task = null
        HandlerList.unregisterAll(this)
        val mainScoreboard = plugin.server.scoreboardManager.mainScoreboard
        plugin.server.onlinePlayers.forEach { player ->
            val view = boards[player.uniqueId]
            val previous = previousBoards[player.uniqueId] ?: mainScoreboard
            val activeBar = bars[player.uniqueId]?.bossBar
            schedulers.entity(player) {
                clearTabAndBars(player, activeBar)
                if (view != null && player.scoreboard === view.scoreboard) player.scoreboard = previous
            }
        }
        renderedTabs.clear()
        playerStates.clear()
        boards.clear()
        boardCreationPending.clear()
        previousBoards.clear()
        bars.clear()
        temporaryBars.clear()
    }

    override fun snapshot(): PresentationSnapshot = PresentationSnapshot(
        tab = config.tabEnabled,
        bossBars = config.bossBar.enabled || temporaryBars.isNotEmpty(),
        scoreboards = config.scoreboardEnabled,
        nametags = config.nametagEnabled,
    )

    override fun refresh(playerId: UUID) {
        plugin.server.getPlayer(playerId)?.let { player -> schedulers.entity(player) { render(player, tick.get()) } }
    }

    override fun showBossBar(request: TemporaryBossBarRequest): AutoCloseable {
        val entry = TemporaryEntry(request, TemplateRenderer(request.text))
        check(temporaryBars.putIfAbsent(request.id, entry) == null) { "Temporary bossbar '${request.id}' already exists" }
        schedulers.global(::refreshAll)
        return AutoCloseable {
            if (temporaryBars.remove(request.id, entry)) schedulers.global(::refreshAll)
        }
    }

    @EventHandler
    public fun onJoin(event: PlayerJoinEvent) {
        schedulers.entity(event.player) { render(event.player, tick.get()) }
    }

    @EventHandler
    public fun onQuit(event: PlayerQuitEvent) {
        val playerId = event.player.uniqueId
        renderedTabs.remove(playerId)
        playerStates.remove(playerId)
        previousBoards.remove(playerId)
        boardCreationPending.remove(playerId)
        bars.remove(playerId)
        boards.remove(playerId)?.let { view -> schedulers.global { clearBoard(view) } }
    }

    private fun refreshAll() {
        if (!active.get()) return
        val now = Instant.now()
        temporaryBars.entries.removeIf { (_, entry) -> !entry.request.endsAt.isAfter(now) }
        val frame = tick.getAndIncrement()
        val players = plugin.server.onlinePlayers.toList()
        val onlineIds = players.mapTo(mutableSetOf(), Player::getUniqueId)
        playerStates.keys.removeIf { it !in onlineIds }
        players.forEach { player -> schedulers.entity(player) { render(player, frame) } }
    }

    private fun render(player: Player, frame: Long) {
        if (!active.get() || !player.isOnline) return
        if (config.scoreboardEnabled || config.nametagEnabled) ensureBoard(player)
        val context = PlaceholderContext(player.uniqueId, serverId, player.world.name)
        val external = placeholders?.resolve(context).orEmpty()
        val papiValues = resolvePapi(player, config.papiKeys + PRIMARY_GROUP_KEY)
        val primaryGroup = papiValues[PRIMARY_GROUP_KEY]?.takeIf(String::isNotBlank)
        val permissions = config.permissionKeys.filterTo(mutableSetOf(), player::hasPermission)
        val isAfk = afk()?.snapshot(player.uniqueId)?.afk == true
        val values = buildMap {
            putAll(external)
            putAll(papiValues)
            put("player", player.name)
            put("server", serverId)
            put("world", player.world.name)
            put("group", primaryGroup.orEmpty())
            put("online", plugin.server.onlinePlayers.size.toString())
            put("afk", isAfk.toString())
            put("afk_indicator", if (isAfk) "[AFK]" else "")
            putIfAbsent("veloutils_network_online", plugin.server.onlinePlayers.size.toString())
        }
        val state = PlayerState(player.uniqueId, player.name, player.world.name, primaryGroup, permissions, values, frame)
        playerStates[player.uniqueId] = state
        renderTab(player, state)
        updateBossBar(player, state)
        if (config.scoreboardEnabled || config.nametagEnabled) {
            val scoreboard = select(config.scoreboards, state) { it.selection }?.let { definition ->
                ScoreboardRender(
                    definition.id,
                    definition.title.render(frame, values),
                    definition.lines.map { it.render(frame, values) },
                )
            }
            schedulers.global { updateBoard(player.uniqueId, scoreboard) }
        }
    }

    private fun renderTab(player: Player, state: PlayerState) {
        if (!config.tabEnabled) return
        val design = select(config.designs, state) { it.selection } ?: config.designs.last()
        val next = RenderedTab(
            design.id,
            design.header.render(state.frame, state.values),
            design.footer.render(state.frame, state.values),
            design.playerListName.render(state.frame, state.values),
            design.sortOrder,
        )
        if (renderedTabs.put(player.uniqueId, next) != next) {
            player.sendPlayerListHeaderAndFooter(next.header, next.footer)
            player.playerListName(next.listName)
            player.playerListOrder = next.order
        }
    }

    private fun ensureBoard(player: Player) {
        val playerId = player.uniqueId
        if (boards.containsKey(playerId) || !boardCreationPending.add(playerId)) return
        previousBoards.putIfAbsent(playerId, player.scoreboard)
        schedulers.global {
            if (!active.get()) {
                boardCreationPending.remove(playerId)
                return@global
            }
            val view = BoardView(plugin.server.scoreboardManager.newScoreboard)
            boards[playerId] = view
            boardCreationPending.remove(playerId)
            schedulers.entity(player) {
                if (active.get() && player.isOnline) player.scoreboard = view.scoreboard
            }
        }
    }

    private fun updateBoard(viewerId: UUID, rendered: ScoreboardRender?) {
        if (!active.get()) return
        val view = boards[viewerId] ?: return
        updateSidebar(view, rendered)
        updateNametags(view)
    }

    private fun updateSidebar(view: BoardView, rendered: ScoreboardRender?) {
        if (!config.scoreboardEnabled || rendered == null) {
            view.objective?.unregister()
            view.objective = null
            view.definition = null
            view.title = null
            view.lines = emptyList()
            return
        }
        val objective = view.objective ?: view.scoreboard.registerNewObjective(
            OBJECTIVE_NAME,
            Criteria.DUMMY,
            rendered.title,
        ).also {
            it.displaySlot = DisplaySlot.SIDEBAR
            it.numberFormat(NumberFormat.blank())
            view.objective = it
        }
        if (view.title != rendered.title) objective.displayName(rendered.title)
        rendered.lines.forEachIndexed { index, line ->
            val score = objective.getScore(SCORE_ENTRIES[index])
            if (!score.isScoreSet || score.score != rendered.lines.size - index) score.score = rendered.lines.size - index
            if (view.lines.getOrNull(index) != line) score.customName(line)
            score.numberFormat(NumberFormat.blank())
        }
        for (index in rendered.lines.size until view.lines.size) view.scoreboard.resetScores(SCORE_ENTRIES[index])
        view.definition = rendered.definition
        view.title = rendered.title
        view.lines = rendered.lines
    }

    private fun updateNametags(view: BoardView) {
        if (!config.nametagEnabled) {
            view.teams.values.forEach { it.team.unregister() }
            view.teams.clear()
            return
        }
        val desired = playerStates.values.mapNotNull { subject ->
            val definition = select(config.nametags, subject) { it.selection } ?: return@mapNotNull null
            val render = TeamRender(
                teamName(definition.sortOrder, subject.playerId),
                subject.name,
                definition.prefix.render(subject.frame, subject.values),
                definition.suffix.render(subject.frame, subject.values),
                definition.color,
                definition.visibility,
                definition.collision,
            )
            subject.playerId to render
        }.toMap()
        (view.teams.keys - desired.keys).forEach { playerId -> view.teams.remove(playerId)?.team?.unregister() }
        desired.forEach { (playerId, render) ->
            val existing = view.teams[playerId]
            val team = if (existing == null || existing.rendered.teamName != render.teamName) {
                existing?.team?.unregister()
                (view.scoreboard.getTeam(render.teamName) ?: view.scoreboard.registerNewTeam(render.teamName))
            } else existing.team
            if (existing?.rendered?.entry != render.entry) existing?.rendered?.entry?.let(team::removeEntry)
            if (!team.hasEntry(render.entry)) team.addEntry(render.entry)
            if (existing?.rendered?.prefix != render.prefix) team.prefix(render.prefix)
            if (existing?.rendered?.suffix != render.suffix) team.suffix(render.suffix)
            if (existing?.rendered?.color != render.color) team.color(render.color ?: NamedTextColor.WHITE)
            if (existing?.rendered?.visibility != render.visibility) team.setOption(Team.Option.NAME_TAG_VISIBILITY, render.visibility)
            if (existing?.rendered?.collision != render.collision) team.setOption(Team.Option.COLLISION_RULE, render.collision)
            view.teams[playerId] = TeamView(team, render)
        }
    }

    private fun updateBossBar(player: Player, state: PlayerState) {
        val now = Instant.now()
        val temporary = temporaryBars.values.asSequence()
            .filter { entry ->
                !entry.request.startsAt.isAfter(now) && entry.request.endsAt.isAfter(now) &&
                    (entry.request.playerIds.isEmpty() || state.playerId in entry.request.playerIds)
            }
            .maxWithOrNull(compareBy<TemporaryEntry> { it.request.priority }.thenBy { it.request.id })
        val persistentVisible = config.bossBar.enabled && config.bossBar.selection.condition.matches(state.selectionContext(serverId))
        val useTemporary = temporary != null && (!persistentVisible || temporary.request.priority >= config.bossBar.priority)
        if (!persistentVisible && !useTemporary) {
            bars.remove(player.uniqueId)?.let { player.hideBossBar(it.bossBar) }
            return
        }
        val source: String
        val name: Component
        val progress: Float
        val color: BossBar.Color
        val overlay: BossBar.Overlay
        if (useTemporary) {
            val entry = checkNotNull(temporary)
            source = "temporary:${entry.request.id}"
            name = entry.renderer.render(state.values)
            progress = entry.request.progress
            color = entry.request.color.toAdventure()
            overlay = entry.request.overlay.toAdventure()
        } else {
            source = "configured"
            name = config.bossBar.text.render(state.frame, state.values)
            progress = config.bossBar.progress
            color = config.bossBar.color
            overlay = config.bossBar.overlay
        }
        val existing = bars[player.uniqueId]
        if (existing == null) {
            val created = BossBar.bossBar(name, progress, color, overlay)
            bars[player.uniqueId] = ActiveBar(created, source)
            player.showBossBar(created)
        } else {
            existing.source = source
            if (existing.bossBar.name() != name) existing.bossBar.name(name)
            if (existing.bossBar.progress() != progress) existing.bossBar.progress(progress)
            if (existing.bossBar.color() != color) existing.bossBar.color(color)
            if (existing.bossBar.overlay() != overlay) existing.bossBar.overlay(overlay)
        }
    }

    private fun resolvePapi(player: Player, keys: Set<String>): Map<String, String> {
        if (!plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) return emptyMap()
        return keys.asSequence().filter { it.startsWith(PAPI_PREFIX) }.associateWith { key ->
            val identifier = key.removePrefix(PAPI_PREFIX)
            PlaceholderAPI.setPlaceholders(player, "%$identifier%").takeIf { it != "%$identifier%" }.orEmpty()
        }
    }

    private fun clearTabAndBars(player: Player, activeBar: BossBar?) {
        if (config.tabEnabled) {
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty())
            player.playerListName(Component.text(player.name))
            player.playerListOrder = 0
        }
        activeBar?.let(player::hideBossBar)
    }

    private fun clearBoard(view: BoardView) {
        runCatching { view.objective?.unregister() }
        view.teams.values.forEach { runCatching { it.team.unregister() } }
    }

    private fun <T> select(definitions: List<T>, state: PlayerState, selection: (T) -> Selection): T? =
        definitions.firstOrNull { selection(it).condition.matches(state.selectionContext(serverId)) }

    private fun teamName(order: Int, playerId: UUID): String = "vu%04d%s".format(order.coerceIn(0, 9_999), playerId.toString().take(8))

    private fun PresentationBossBarColor.toAdventure(): BossBar.Color = BossBar.Color.valueOf(name)
    private fun PresentationBossBarOverlay.toAdventure(): BossBar.Overlay = BossBar.Overlay.valueOf(name)

    private companion object {
        const val OBJECTIVE_NAME: String = "vu_sidebar"
        const val PAPI_PREFIX: String = "papi_"
        const val PRIMARY_GROUP_KEY: String = "papi_luckperms_primary_group"
        val SCORE_ENTRIES: List<String> = (0 until 15).map { index -> "§${index.toString(16)}" }
    }
}
