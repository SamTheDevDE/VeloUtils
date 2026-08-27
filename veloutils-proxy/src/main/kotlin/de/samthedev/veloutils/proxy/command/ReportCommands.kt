// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import de.samthedev.veloutils.api.CreateReport
import de.samthedev.veloutils.api.ReportId
import de.samthedev.veloutils.api.ReportType
import de.samthedev.veloutils.common.BoundedExpiringMap
import de.samthedev.veloutils.proxy.report.PersistentReportService
import de.samthedev.veloutils.proxy.util.ConfiguredMessages
import de.samthedev.veloutils.proxy.integration.NetworkEventKind
import de.samthedev.veloutils.proxy.integration.NetworkEventSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import java.time.Duration
import java.util.UUID

public class ReportCreateCommand(
    private val type: ReportType,
    private val proxy: ProxyServer,
    private val reports: PersistentReportService,
    private val messages: ConfiguredMessages,
    private val scope: CoroutineScope,
    private val cooldowns: BoundedExpiringMap<UUID, Unit>,
    private val eventSink: NetworkEventSink,
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source() as? Player
        if (source == null) {
            invocation.source().sendMessage(messages.render("players-only"))
            return
        }
        val permission = if (type == ReportType.PLAYER) "veloutils.report.create" else "veloutils.helpop.create"
        if (!source.hasPermission(permission)) {
            source.sendMessage(messages.render("no-permission"))
            return
        }
        if (cooldowns[source.uniqueId] != null) {
            source.sendMessage(Component.text("Please wait before submitting another request."))
            return
        }
        val arguments = invocation.arguments()
        val target = if (type == ReportType.PLAYER) arguments.firstOrNull()?.let { proxy.getPlayer(it).orElse(null) } else null
        val reason = arguments.drop(if (type == ReportType.PLAYER) 1 else 0).joinToString(" ")
        if ((type == ReportType.PLAYER && target == null) || reason.isBlank()) {
            source.sendMessage(Component.text(if (type == ReportType.PLAYER) "/report <player> <reason>" else "/helpop <message>"))
            return
        }
        if (target?.uniqueId == source.uniqueId) {
            source.sendMessage(Component.text("You cannot report yourself."))
            return
        }
        val request = CreateReport(
            type,
            source.uniqueId,
            source.username,
            target?.uniqueId,
            target?.username,
            reason,
            source.currentServer.map { it.serverInfo.name }.orElse(null),
        )
        cooldowns.put(source.uniqueId, Unit)
        scope.launch {
            runCatching { reports.create(request) }
                .onSuccess { report ->
                    source.sendMessage(messages.render("report.created", mapOf("id" to Component.text(report.id.value))))
                    val notice = Component.text("New ${report.type.name.lowercase()} #${report.id.value} from ${report.reporterName}: ${report.reason}")
                    proxy.allPlayers.filter { it.hasPermission("veloutils.report.manage") }.forEach { it.sendMessage(notice) }
                    proxy.consoleCommandSource.sendMessage(notice)
                    eventSink.emit(
                        if (report.type == ReportType.HELPOP) NetworkEventKind.HELPOP else NetworkEventKind.REPORT,
                        "New ${report.type.name.lowercase()} #${report.id.value}",
                        "Reporter: ${report.reporterName}\nServer: ${report.server ?: "unknown"}\nReason: ${report.reason}",
                    )
                }
                .onFailure {
                    cooldowns.remove(source.uniqueId)
                    source.sendMessage(Component.text("The report could not be saved."))
                }
        }
    }
}

public class ReportManageCommand(
    private val reports: PersistentReportService,
    private val messages: ConfiguredMessages,
    private val scope: CoroutineScope,
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        if (!source.hasPermission("veloutils.report.manage")) {
            source.sendMessage(messages.render("no-permission"))
            return
        }
        val arguments = invocation.arguments()
        val id = arguments.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0 }?.let(::ReportId)
        if (arguments.firstOrNull()?.lowercase() !in setOf("view", "claim", "close") || id == null) {
            source.sendMessage(Component.text("/reports <view|claim|close> <id> [resolution]"))
            return
        }
        scope.launch {
            runCatching {
                when (arguments[0].lowercase()) {
                    "view" -> reports.find(id)
                    "claim" -> {
                        val player = source as? Player ?: error("Only a player can claim a report")
                        reports.claim(id, player.uniqueId, player.username)
                    }
                    else -> reports.close(id, (source as? Player)?.uniqueId, arguments.drop(2).joinToString(" "))
                }
            }.onSuccess { report ->
                if (report == null) source.sendMessage(Component.text("Report not found."))
                else source.sendMessage(Component.text("#${report.id.value} ${report.status}: ${report.reporterName} — ${report.reason}"))
            }.onFailure { source.sendMessage(Component.text("Report operation failed: ${it.message ?: "invalid state"}")) }
        }
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        if (!invocation.source().hasPermission("veloutils.report.manage")) return emptyList()
        return if (invocation.arguments().size <= 1) listOf("view", "claim", "close").filter {
            it.startsWith(invocation.arguments().firstOrNull().orEmpty(), true)
        } else emptyList()
    }
}

public fun reportCooldowns(): BoundedExpiringMap<UUID, Unit> =
    BoundedExpiringMap(maximumSize = 100_000, ttl = Duration.ofSeconds(30))
