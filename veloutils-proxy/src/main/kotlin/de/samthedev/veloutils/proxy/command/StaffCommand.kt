// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import de.samthedev.veloutils.common.DurationParser
import de.samthedev.veloutils.proxy.staff.VelocityStaffService
import de.samthedev.veloutils.proxy.util.ConfiguredMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

public enum class StaffCommandKind { LIST, TIME }

public class StaffCommand(
    private val kind: StaffCommandKind,
    private val proxy: ProxyServer,
    private val staff: VelocityStaffService,
    private val messages: ConfiguredMessages,
    private val scope: CoroutineScope,
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val permission = if (kind == StaffCommandKind.LIST) "veloutils.staff.list" else "veloutils.staff.time"
        if (!invocation.source().hasPermission(permission)) {
            invocation.source().sendMessage(messages.render("no-permission"))
            return
        }
        if (kind == StaffCommandKind.LIST) {
            val online = staff.onlineStaff()
            invocation.source().sendMessage(Component.text("Online staff (${online.size}):"))
            online.forEach { invocation.source().sendMessage(Component.text("${it.name} — ${it.server ?: "connecting"}")) }
            return
        }
        val target = invocation.arguments().firstOrNull()?.let { proxy.getPlayer(it).orElse(null) } ?: invocation.source() as? Player
        if (target == null) {
            invocation.source().sendMessage(Component.text("/stafftime <online player>"))
            return
        }
        scope.launch {
            val until = Instant.now()
            val duration = staff.trackedTime(target.uniqueId, until.minus(7, ChronoUnit.DAYS), until)
            invocation.source().sendMessage(Component.text("${target.username}: ${DurationParser.format(duration)} tracked in the last 7 days"))
        }
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        if (kind != StaffCommandKind.TIME || !invocation.source().hasPermission("veloutils.staff.time")) return emptyList()
        val input = invocation.arguments().firstOrNull().orEmpty()
        return proxy.allPlayers.map { it.username }.filter { it.startsWith(input, true) }
    }
}
