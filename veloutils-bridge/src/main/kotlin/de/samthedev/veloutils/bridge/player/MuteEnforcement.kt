// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge.player

import de.samthedev.veloutils.bridge.server.PlatformSchedulers
import de.samthedev.veloutils.protocol.MuteStatePayload
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.time.Clock
import java.util.UUID

public class MuteEnforcement(
    private val schedulers: PlatformSchedulers,
    messageTemplate: String,
    clock: Clock = Clock.systemUTC(),
) : Listener {
    private val states = MuteStateCache(clock)
    private val miniMessage = MiniMessage.miniMessage()
    private val template = messageTemplate

    public fun update(payload: MuteStatePayload) {
        states.update(payload)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public fun onChat(event: AsyncChatEvent) {
        val state = states.active(event.player.uniqueId) ?: return
        event.isCancelled = true
        val message = miniMessage.deserialize(template, Placeholder.unparsed("reason", state.reason))
        schedulers.entity(event.player) { event.player.sendMessage(message) }
    }

    @EventHandler
    public fun onQuit(event: PlayerQuitEvent) {
        states.remove(event.player.uniqueId)
    }

    public fun isMuted(playerId: UUID): Boolean = states.active(playerId) != null
}
