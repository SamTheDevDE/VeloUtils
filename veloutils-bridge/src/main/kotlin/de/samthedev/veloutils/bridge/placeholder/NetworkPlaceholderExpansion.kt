// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge.placeholder

import de.samthedev.veloutils.api.AfkService
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap

public class NetworkPlaceholderCache {
    private val values = ConcurrentHashMap<String, String>()
    public fun update(snapshot: Map<String, String>) { values.putAll(snapshot) }
    public fun get(key: String): String? = values[key]
    public fun snapshot(): Map<String, String> = values.toMap()
}

public class NetworkPlaceholderExpansion(
    private val plugin: Plugin,
    private val serverId: String,
    private val cache: NetworkPlaceholderCache,
    private val afk: () -> AfkService?,
) : PlaceholderExpansion() {
    override fun getIdentifier(): String = "veloutils"
    override fun getAuthor(): String = "SamTheDevDE"
    override fun getVersion(): String = plugin.pluginMeta.version
    override fun persist(): Boolean = true

    override fun onPlaceholderRequest(player: Player?, identifier: String): String = when {
        identifier == "server" || identifier == "player_server" -> serverId
        identifier == "network_online" || identifier == "network_players" -> cache.get("network_players") ?: plugin.server.onlinePlayers.size.toString()
        identifier == "afk" -> player?.let { afk()?.snapshot(it.uniqueId)?.afk?.toString() } ?: "false"
        identifier == "staff_online" -> cache.get("staff_online") ?: "0"
        identifier == "maintenance" -> cache.get("maintenance") ?: "false"
        identifier.startsWith("server_") && identifier.endsWith("_players") -> cache.get(identifier) ?: "0"
        identifier.startsWith("server_") && identifier.endsWith("_online") -> cache.get(identifier) ?: "false"
        identifier.startsWith("maintenance_") -> cache.get(identifier) ?: "false"
        else -> ""
    }
}
