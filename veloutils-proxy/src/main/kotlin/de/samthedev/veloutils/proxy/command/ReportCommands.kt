// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.command

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import de.samthedev.veloutils.api.CreateReport
import de.samthedev.veloutils.api.Report
import de.samthedev.veloutils.api.ReportId
import de.samthedev.veloutils.api.ReportStatus
import de.samthedev.veloutils.api.ReportType
import de.samthedev.veloutils.common.BoundedExpiringMap
import de.samthedev.veloutils.common.PageRequest
import de.samthedev.veloutils.common.Permissions
import de.samthedev.veloutils.proxy.integration.NetworkEventKind
import de.samthedev.veloutils.proxy.integration.NetworkEventSink
import de.samthedev.veloutils.proxy.permission.PermissionService
import de.samthedev.veloutils.proxy.report.PersistentReportService
import de.samthedev.veloutils.proxy.report.ReportFilter
import de.samthedev.veloutils.proxy.ui.ChatUi
import de.samthedev.veloutils.proxy.util.ConfiguredMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.time.Duration
import java.util.UUID

public class ReportCreateCommand(
    private val type: ReportType,
    private val proxy: ProxyServer,
    private val reports: PersistentReportService,
    private val messages: ConfiguredMessages,
    private val permissions: PermissionService,
    private val scope: CoroutineScope,
    private val cooldowns: BoundedExpiringMap<UUID, Unit>,
    private val eventSink: NetworkEventSink,
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source() as? Player
        if (source == null) {
            invocation.source().sendMessage(ChatUi.error("This command requires a player because reports record their sender and server."))
            return
        }
        val permission = if (type == ReportType.PLAYER) Permissions.REPORTS_CREATE else Permissions.HELPOP_CREATE
        if (!permissions.has(source, permission)) {
            source.sendMessage(ChatUi.error("You do not have permission to create this request."))
            return
        }
        if (cooldowns[source.uniqueId] != null) {
            source.sendMessage(ChatUi.warning("Please wait before submitting another request."))
            return
        }
        val arguments = invocation.arguments()
        val target = if (type == ReportType.PLAYER) arguments.firstOrNull()?.let { proxy.getPlayer(it).orElse(null) } else null
        val reason = arguments.drop(if (type == ReportType.PLAYER) 1 else 0).joinToString(" ")
        if ((type == ReportType.PLAYER && target == null) || reason.isBlank()) {
            val syntax = if (type == ReportType.PLAYER) "/report <online-player> <reason>" else "/helpop <message>"
            ChatUi.usage(source, syntax, "Submits a request to online network staff.").forEach(source::sendMessage)
            return
        }
        if (target?.uniqueId == source.uniqueId) {
            source.sendMessage(ChatUi.error("You cannot report yourself. Contact staff with /helpop instead."))
            return
        }
        val request = CreateReport(
            type, source.uniqueId, source.username, target?.uniqueId, target?.username, reason,
            source.currentServer.map { it.serverInfo.name }.orElse(null),
        )
        cooldowns.put(source.uniqueId, Unit)
        scope.launch {
            runCatching { reports.create(request) }.onSuccess { report ->
                source.sendMessage(messages.render("report.created", mapOf("id" to Component.text(report.id.value))))
                if (permissions.has(source, Permissions.REPORTS_VIEW)) {
                    source.sendMessage(ChatUi.button(source, "View report", "/reports view ${report.id.value}", "View report #${report.id.value}"))
                }
                val notice = Component.text()
                    .append(Component.text("New ${report.type.name.lowercase()} #${report.id.value} from ${report.reporterName}: ", NamedTextColor.YELLOW))
                    .append(Component.text(report.reason, NamedTextColor.GRAY))
                    .append(Component.space())
                    .append(ChatUi.button(source, "View", "/reports view ${report.id.value}", "View and manage report #${report.id.value}"))
                    .build()
                proxy.allPlayers.filter { permissions.has(it, Permissions.REPORTS_NOTIFY) }.forEach { staff ->
                    val personalized = Component.text()
                        .append(Component.text("New ${report.type.name.lowercase()} #${report.id.value} from ${report.reporterName}: ", NamedTextColor.YELLOW))
                        .append(Component.text(report.reason, NamedTextColor.GRAY)).append(Component.space())
                        .append(ChatUi.button(staff, "View", "/reports view ${report.id.value}", "View and manage report #${report.id.value}"))
                        .build()
                    staff.sendMessage(personalized)
                }
                proxy.consoleCommandSource.sendMessage(notice)
                eventSink.emit(
                    if (report.type == ReportType.HELPOP) NetworkEventKind.HELPOP else NetworkEventKind.REPORT,
                    "New ${report.type.name.lowercase()} #${report.id.value}",
                    "Reporter: ${report.reporterName}\nServer: ${report.server ?: "unknown"}\nReason: ${report.reason}",
                )
            }.onFailure {
                cooldowns.remove(source.uniqueId)
                source.sendMessage(ChatUi.error("The request could not be saved. Please try again or contact an administrator."))
            }
        }
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        if (type != ReportType.PLAYER || !permissions.has(invocation.source(), Permissions.REPORTS_CREATE)) return emptyList()
        if (invocation.arguments().size > 1) return emptyList()
        val input = invocation.arguments().firstOrNull().orEmpty()
        return proxy.allPlayers.map(Player::getUsername).filter { it.startsWith(input, true) }.take(20)
    }
}

public class ReportManageCommand(
    private val proxy: ProxyServer,
    private val reports: PersistentReportService,
    private val permissions: PermissionService,
    private val pageSize: Int,
    private val scope: CoroutineScope,
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        if (!permissions.has(source, Permissions.REPORTS_VIEW)) {
            source.sendMessage(ChatUi.error("You do not have permission to view reports."))
            return
        }
        val arguments = invocation.arguments()
        when (arguments.firstOrNull()?.lowercase()) {
            "view" -> direct(source, "view", arguments)
            "claim" -> direct(source, "claim", arguments)
            "close" -> direct(source, "close", arguments)
            else -> list(source, arguments)
        }
    }

    private fun list(source: CommandSource, arguments: Array<String>) {
        val first = arguments.firstOrNull()?.lowercase()
        val filter = first?.let { value -> ReportFilter.entries.firstOrNull { it.name.equals(value, true) } } ?: ReportFilter.ALL
        val pageText = if (first?.toIntOrNull() != null) first else arguments.getOrNull(1)
        val pageNumber = pageText?.toIntOrNull() ?: 1
        val unknownFilter = first != null && first.toIntOrNull() == null && ReportFilter.entries.none { it.name.equals(first, true) }
        if (unknownFilter) source.sendMessage(ChatUi.error("Unknown report filter '$first'. Use open, claimed, closed, or mine."))
        if (pageText != null && (pageText.toIntOrNull() == null || pageNumber < 1)) {
            source.sendMessage(ChatUi.error("Invalid page '$pageText'. Pages are positive whole numbers."))
        }
        if (pageText != null && pageText.toIntOrNull() == null || pageNumber < 1 || unknownFilter) {
            ChatUi.usage(source, "/reports [open|claimed|closed|mine] [page]", "Lists reports with optional status filtering.")
                .forEach(source::sendMessage)
            return
        }
        if (filter == ReportFilter.MINE && source !is Player) {
            source.sendMessage(ChatUi.error("The 'mine' filter requires a player sender. Console can use open, claimed, or closed."))
            return
        }
        scope.launch {
            val page = reports.page(filter, (source as? Player)?.uniqueId, PageRequest(pageNumber, pageSize))
            if (pageNumber > page.totalPages) {
                source.sendMessage(ChatUi.error("Page $pageNumber does not exist; the last page is ${page.totalPages}."))
                return@launch
            }
            source.sendMessage(ChatUi.header("Reports • ${filter.name.lowercase().replaceFirstChar(Char::uppercase)}"))
            source.sendMessage(ChatUi.field("Total", page.totalItems.toString()))
            if (page.items.isEmpty()) source.sendMessage(ChatUi.info("No reports match this filter."))
            page.items.forEach { report -> renderSummary(source, report) }
            if (page.totalPages > 1) {
                val prefix = if (filter == ReportFilter.ALL) "/reports" else "/reports ${filter.name.lowercase()}"
                source.sendMessage(ChatUi.pagination(source, page, prefix))
            }
        }
    }

    private fun direct(source: CommandSource, action: String, arguments: Array<String>) {
        val id = arguments.getOrNull(1)?.removePrefix("#")?.toLongOrNull()?.takeIf { it > 0 }?.let(::ReportId)
        if (id == null) {
            val syntax = when (action) {
                "close" -> "/reports close <id> <resolution>"
                else -> "/reports $action <id>"
            }
            ChatUi.usage(source, syntax, "Manages one report.", "/reports").forEach(source::sendMessage)
            return
        }
        if (action == "claim" && !permissions.has(source, Permissions.REPORTS_CLAIM) ||
            action == "close" && !permissions.has(source, Permissions.REPORTS_CLOSE)
        ) {
            source.sendMessage(ChatUi.error("You do not have permission to $action reports."))
            return
        }
        val resolution = arguments.drop(2).joinToString(" ")
        if (action == "close" && resolution.isBlank()) {
            ChatUi.usage(source, "/reports close <id> <resolution>", "Records why the report was closed.", "/reports view ${id.value}")
                .forEach(source::sendMessage)
            return
        }
        scope.launch {
            runCatching {
                when (action) {
                    "view" -> reports.find(id)
                    "claim" -> {
                        val player = source as? Player ?: error("Console cannot claim a report because claims require a staff identity")
                        reports.claim(id, player.uniqueId, player.username)
                    }
                    else -> reports.close(id, (source as? Player)?.uniqueId, resolution)
                }
            }.onSuccess { report ->
                if (report == null) source.sendMessage(ChatUi.error("Report #${id.value} was not found."))
                else if (action == "view") renderDetails(source, report)
                else {
                    source.sendMessage(ChatUi.success("Report #${id.value} is now ${report.status.name.lowercase()}."))
                    renderDetails(source, report)
                }
            }.onFailure { source.sendMessage(ChatUi.error("Report operation failed: ${it.message ?: "invalid state"}.")) }
        }
    }

    private fun renderSummary(source: CommandSource, report: Report) {
        val status = when (report.status) {
            ReportStatus.OPEN -> "OPEN"
            ReportStatus.CLAIMED -> "CLAIMED by ${report.assignedStaffName ?: "staff"}"
            ReportStatus.CLOSED -> "CLOSED"
        }
        source.sendMessage(Component.text()
            .append(Component.text("#${report.id.value} ", NamedTextColor.DARK_GRAY))
            .append(ChatUi.status(status, report.status != ReportStatus.CLOSED))
            .append(Component.text(" • ${report.type.name.lowercase()}", NamedTextColor.GOLD))
            .append(Component.newline()).append(Component.text("${report.reporterName} → ${report.targetName ?: "Staff"}", NamedTextColor.AQUA))
            .append(Component.newline()).append(Component.text(report.reason, NamedTextColor.GRAY)).build())
        val actions = buildList {
            add(ChatUi.button(source, "View", "/reports view ${report.id.value}", "View report #${report.id.value}"))
            if (report.status == ReportStatus.OPEN && source is Player && permissions.has(source, Permissions.REPORTS_CLAIM)) {
                add(ChatUi.button(source, "Claim", "/reports claim ${report.id.value}", "Claim report #${report.id.value}"))
            }
        }
        source.sendMessage(ChatUi.join(*actions.toTypedArray()))
    }

    private fun renderDetails(source: CommandSource, report: Report) {
        source.sendMessage(ChatUi.header("Report #${report.id.value}"))
        listOf(
            ChatUi.field("Status", report.status.name), ChatUi.field("Type", report.type.name),
            ChatUi.field("Reporter", report.reporterName), ChatUi.field("Target", report.targetName ?: "Staff"),
            ChatUi.field("Reason", report.reason), ChatUi.field("Server", report.server ?: "Unknown"),
            ChatUi.field("Created", ChatUi.format(report.createdAt)),
            ChatUi.field("Assigned to", report.assignedStaffName ?: "Nobody"),
            ChatUi.field("Resolution", report.resolution ?: "Open"), ChatUi.field("Closed", ChatUi.format(report.closedAt)),
        ).forEach(source::sendMessage)
        val actions = buildList {
            if (report.status == ReportStatus.OPEN && source is Player && permissions.has(source, Permissions.REPORTS_CLAIM)) {
                add(ChatUi.button(source, "Claim", "/reports claim ${report.id.value}", "Claim this report"))
            }
            if (report.status != ReportStatus.CLOSED && permissions.has(source, Permissions.REPORTS_CLOSE)) {
                add(ChatUi.button(source, "Close", "/reports close ${report.id.value} ", "Enter a closing resolution", destructive = true))
            }
            if (report.targetName != null && permissions.has(source, Permissions.MODERATION_HISTORY)) {
                add(ChatUi.button(source, "Target history", "/history ${report.targetName}", "View target punishment history"))
            }
            val onlineTarget = report.targetId?.let { proxy.getPlayer(it).orElse(null) }
            if (onlineTarget != null && source is Player && permissions.has(source, Permissions.NETWORK_GOTO)) {
                add(ChatUi.button(source, "Go to target", "/goto ${onlineTarget.username}", "Join the target's server"))
            }
        }
        if (actions.isNotEmpty()) source.sendMessage(ChatUi.join(*actions.toTypedArray()))
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        if (!permissions.has(invocation.source(), Permissions.REPORTS_VIEW)) return emptyList()
        val input = invocation.arguments().lastOrNull().orEmpty()
        return if (invocation.arguments().size <= 1) {
            listOf("open", "claimed", "closed", "mine", "view", "claim", "close").filter { it.startsWith(input, true) }
        } else emptyList()
    }
}

public fun reportCooldowns(): BoundedExpiringMap<UUID, Unit> =
    BoundedExpiringMap(maximumSize = 100_000, ttl = Duration.ofSeconds(30))
