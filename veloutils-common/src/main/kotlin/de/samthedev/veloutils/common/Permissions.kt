// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.common

public data class PermissionDefinition(
    val node: String,
    val description: String,
    val legacyAliases: Set<String> = emptySet(),
)

public object Permissions {
    public val ADMIN_STATUS: PermissionDefinition = permission("veloutils.admin.status", "View plugin and network status", "veloutils.command.admin")
    public val ADMIN_RELOAD: PermissionDefinition = permission("veloutils.admin.reload", "Reload safe configuration", "veloutils.command.admin")
    public val ADMIN_DEBUG: PermissionDefinition = permission("veloutils.admin.debug", "View redacted diagnostics", "veloutils.command.admin")
    public val ADMIN_VERSION: PermissionDefinition = permission("veloutils.admin.version", "View the installed version", "veloutils.command.admin")
    public val ADMIN_CONFIG: PermissionDefinition = permission("veloutils.admin.config", "Validate configuration", "veloutils.command.admin")

    public val NETWORK_FIND: PermissionDefinition = permission("veloutils.network.find", "Locate an online player", "veloutils.command.find")
    public val NETWORK_GOTO: PermissionDefinition = permission("veloutils.network.goto", "Join an online player's server", "veloutils.command.goto")
    public val NETWORK_LIST: PermissionDefinition = permission("veloutils.network.list", "View players grouped by server", "veloutils.command.list")
    public val NETWORK_STATUS: PermissionDefinition = permission("veloutils.network.status", "View the network overview", "veloutils.command.network")
    public val NETWORK_SERVER_INFO: PermissionDefinition = permission("veloutils.network.serverinfo", "View detailed server status", "veloutils.command.serverinfo")
    public val NETWORK_SEND: PermissionDefinition = permission("veloutils.network.send", "Move one player to a server", "veloutils.command.send")
    public val NETWORK_SEND_ALL: PermissionDefinition = permission("veloutils.network.sendall", "Move all online players", "veloutils.command.sendall")
    public val NETWORK_EXECUTE: PermissionDefinition = permission("veloutils.network.execute", "Run an allowlisted backend command", "veloutils.command.serverexecute")

    public val MAINTENANCE_MANAGE: PermissionDefinition = permission("veloutils.maintenance.manage", "Manage maintenance state and its allowlist", "veloutils.maintenance.command")
    public val MAINTENANCE_BYPASS: PermissionDefinition = permission("veloutils.maintenance.bypass", "Bypass maintenance access restrictions")
    public val MAINTENANCE_NOTIFY: PermissionDefinition = permission("veloutils.maintenance.notify", "Receive maintenance activity notifications")

    public val STAFF_MEMBER: PermissionDefinition = permission("veloutils.staff.member", "Identify a player as staff")
    public val STAFF_LIST_VIEW: PermissionDefinition = permission("veloutils.staff.list.view", "View the online staff list", "veloutils.staff.list")
    public val STAFF_TIME_SELF: PermissionDefinition = permission("veloutils.staff.time.view.self", "View personal tracked staff time", "veloutils.staff.time")
    public val STAFF_TIME_OTHERS: PermissionDefinition = permission("veloutils.staff.time.view.others", "View another staff member's tracked time", "veloutils.staff.time")
    public val STAFF_ACTIVITY_NOTIFY: PermissionDefinition = permission("veloutils.staff.activity.notify", "Receive staff activity notifications", "veloutils.staff.notify")
    public val STAFF_TIME_EXCLUDE: PermissionDefinition = permission("veloutils.staff.time.exclude", "Exclude a staff member from time tracking")

    public val CHAT_STAFF_USE: PermissionDefinition = permission("veloutils.chat.staff.use", "Send staff-chat messages", "veloutils.chat.staff")
    public val CHAT_STAFF_RECEIVE: PermissionDefinition = permission("veloutils.chat.staff.receive", "Receive staff-chat messages", "veloutils.chat.staff")
    public val CHAT_ADMIN_USE: PermissionDefinition = permission("veloutils.chat.admin.use", "Send admin-chat messages", "veloutils.chat.admin")
    public val CHAT_ADMIN_RECEIVE: PermissionDefinition = permission("veloutils.chat.admin.receive", "Receive admin-chat messages", "veloutils.chat.admin")

    public val REPORTS_CREATE: PermissionDefinition = permission("veloutils.reports.create", "Create player reports", "veloutils.report.create")
    public val HELPOP_CREATE: PermissionDefinition = permission("veloutils.helpop.create", "Create help requests")
    public val REPORTS_VIEW: PermissionDefinition = permission("veloutils.reports.view", "View reports", "veloutils.report.manage")
    public val REPORTS_CLAIM: PermissionDefinition = permission("veloutils.reports.claim", "Claim open reports", "veloutils.report.manage")
    public val REPORTS_CLOSE: PermissionDefinition = permission("veloutils.reports.close", "Close reports", "veloutils.report.manage")
    public val REPORTS_NOTIFY: PermissionDefinition = permission("veloutils.reports.notify", "Receive new-report notifications", "veloutils.report.manage")

    public val MODERATION_BAN: PermissionDefinition = permission("veloutils.moderation.ban", "Create permanent bans")
    public val MODERATION_TEMPBAN: PermissionDefinition = permission("veloutils.moderation.tempban", "Create temporary bans")
    public val MODERATION_IPBAN: PermissionDefinition = permission("veloutils.moderation.ipban", "Create permanent IP bans")
    public val MODERATION_TEMPIPBAN: PermissionDefinition = permission("veloutils.moderation.tempipban", "Create temporary IP bans")
    public val MODERATION_UNBAN: PermissionDefinition = permission("veloutils.moderation.unban", "Revoke active bans")
    public val MODERATION_KICK: PermissionDefinition = permission("veloutils.moderation.kick", "Kick online players")
    public val MODERATION_WARN: PermissionDefinition = permission("veloutils.moderation.warn", "Create warnings")
    public val MODERATION_MUTE: PermissionDefinition = permission("veloutils.moderation.mute", "Create permanent mutes")
    public val MODERATION_TEMPMUTE: PermissionDefinition = permission("veloutils.moderation.tempmute", "Create temporary mutes")
    public val MODERATION_UNMUTE: PermissionDefinition = permission("veloutils.moderation.unmute", "Revoke active mutes")
    public val MODERATION_HISTORY: PermissionDefinition = permission("veloutils.moderation.history.view", "View punishment history", "veloutils.moderation.history")
    public val MODERATION_BAN_VIEW: PermissionDefinition = permission("veloutils.moderation.ban.view", "View active bans", "veloutils.moderation.checkban")
    public val MODERATION_DETAILS: PermissionDefinition = permission("veloutils.moderation.punishment.view", "View punishment details", "veloutils.moderation.history")
    public val MODERATION_IP_VIEW: PermissionDefinition = permission("veloutils.moderation.ip.view", "View IP-ban metadata")
    public val MODERATION_SELF_PUNISH: PermissionDefinition = permission("veloutils.moderation.self-punish", "Confirm self-directed punishments")

    public val ALERT_BROADCAST: PermissionDefinition = permission("veloutils.alert.broadcast", "Broadcast a network alert", "veloutils.bridge.alert")
    public val SERVER_ACCESS_BYPASS: PermissionDefinition = permission("veloutils.server-access.bypass", "Bypass configured server access rules")

    public val ALL: List<PermissionDefinition> = listOf(
        ADMIN_STATUS, ADMIN_RELOAD, ADMIN_DEBUG, ADMIN_VERSION, ADMIN_CONFIG,
        NETWORK_FIND, NETWORK_GOTO, NETWORK_LIST, NETWORK_STATUS, NETWORK_SERVER_INFO, NETWORK_SEND, NETWORK_SEND_ALL, NETWORK_EXECUTE,
        MAINTENANCE_MANAGE, MAINTENANCE_BYPASS, MAINTENANCE_NOTIFY,
        STAFF_MEMBER, STAFF_LIST_VIEW, STAFF_TIME_SELF, STAFF_TIME_OTHERS, STAFF_ACTIVITY_NOTIFY, STAFF_TIME_EXCLUDE,
        CHAT_STAFF_USE, CHAT_STAFF_RECEIVE, CHAT_ADMIN_USE, CHAT_ADMIN_RECEIVE,
        REPORTS_CREATE, HELPOP_CREATE, REPORTS_VIEW, REPORTS_CLAIM, REPORTS_CLOSE, REPORTS_NOTIFY,
        MODERATION_BAN, MODERATION_TEMPBAN, MODERATION_IPBAN, MODERATION_TEMPIPBAN, MODERATION_UNBAN,
        MODERATION_KICK, MODERATION_WARN, MODERATION_MUTE, MODERATION_TEMPMUTE, MODERATION_UNMUTE,
        MODERATION_HISTORY, MODERATION_BAN_VIEW, MODERATION_DETAILS, MODERATION_IP_VIEW, MODERATION_SELF_PUNISH,
        ALERT_BROADCAST, SERVER_ACCESS_BYPASS,
    )

    private fun permission(node: String, description: String, vararg aliases: String): PermissionDefinition =
        PermissionDefinition(node, description, aliases.toSet())
}

public fun PermissionDefinition.isGranted(
    legacyEnabled: Boolean = true,
    check: (String) -> Boolean,
): Boolean = check(node) || legacyEnabled && legacyAliases.any(check)
