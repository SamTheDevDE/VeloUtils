// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import de.samthedev.veloutils.common.Page
import de.samthedev.veloutils.common.Permissions
import de.samthedev.veloutils.proxy.permission.PermissionService
import de.samthedev.veloutils.proxy.player.PlayerIdentityService
import de.samthedev.veloutils.proxy.staff.VelocityStaffService
import de.samthedev.veloutils.proxy.ui.ChatUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

public enum class StaffCommandKind { LIST, TIME }

public class StaffCommand(
    private val kind: StaffCommandKind,
    private val staff: VelocityStaffService,
    private val identities: PlayerIdentityService,
    private val permissions: PermissionService,
    private val pageSize: Int,
    private val scope: CoroutineScope,
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        if (kind == StaffCommandKind.LIST) {
            if (!permissions.has(source, Permissions.STAFF_LIST_VIEW)) {
                source.sendMessage(ChatUi.error("You do not have permission to view the staff list."))
                return
            }
            val pageInput = invocation.arguments().firstOrNull()
            val pageNumber = pageInput?.toIntOrNull() ?: 1
            if (pageInput != null && (pageInput.toIntOrNull() == null || pageNumber < 1)) {
                source.sendMessage(ChatUi.error("Invalid page '$pageInput'. Pages are positive whole numbers."))
                ChatUi.usage(source, "/stafflist [page]", "Shows a paginated list of online staff.").forEach(source::sendMessage)
                return
            }
            val online = staff.onlineStaff()
            val totalPages = maxOf(1, (online.size + pageSize - 1) / pageSize)
            if (pageNumber !in 1..totalPages) {
                source.sendMessage(ChatUi.error("Page $pageNumber does not exist; the last page is $totalPages."))
                return
            }
            val items = online.drop((pageNumber - 1) * pageSize).take(pageSize)
            val page = Page(items, pageNumber, pageSize, online.size.toLong())
            source.sendMessage(ChatUi.header("Online staff"))
            source.sendMessage(ChatUi.field("Online", online.size.toString()))
            if (items.isEmpty()) source.sendMessage(ChatUi.info("No tracked staff members are currently online."))
            items.forEach { member -> source.sendMessage(ChatUi.join(ChatUi.player(member.name), ChatUi.server(member.server ?: "Connecting"))) }
            if (page.totalPages > 1) source.sendMessage(ChatUi.pagination(source, page, "/stafflist"))
            return
        }

        val input = invocation.arguments().firstOrNull()
        val sender = source as? Player
        val viewingSelf = input == null || sender?.username?.equals(input, true) == true
        val required = if (viewingSelf) Permissions.STAFF_TIME_SELF else Permissions.STAFF_TIME_OTHERS
        if (!permissions.has(source, required)) {
            source.sendMessage(ChatUi.error(if (viewingSelf) "You cannot view your tracked staff time." else "You cannot view another staff member's tracked time."))
            return
        }
        if (input == null && sender == null) {
            ChatUi.usage(source, "/stafftime <known-player>", "Shows tracked staff time from the last seven days.").forEach(source::sendMessage)
            return
        }
        scope.launch {
            val identity = if (input == null && sender != null) identities.resolve(sender.uniqueId.toString()) else identities.resolve(requireNotNull(input))
            if (identity == null) {
                source.sendMessage(ChatUi.error("Unknown player '${input ?: sender?.username}'."))
                return@launch
            }
            val until = Instant.now()
            val duration = staff.trackedTime(identity.playerId, until.minus(7, ChronoUnit.DAYS), until)
            source.sendMessage(ChatUi.header("Staff time • ${identity.name}"))
            source.sendMessage(ChatUi.field("Last 7 days", ChatUi.humanDuration(duration)))
            source.sendMessage(ChatUi.field("Last seen", ChatUi.format(identity.lastSeen)))
        }
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        if (kind != StaffCommandKind.TIME || !permissions.has(invocation.source(), Permissions.STAFF_TIME_OTHERS)) return emptyList()
        return identities.suggestions(invocation.arguments().firstOrNull().orEmpty())
    }
}
