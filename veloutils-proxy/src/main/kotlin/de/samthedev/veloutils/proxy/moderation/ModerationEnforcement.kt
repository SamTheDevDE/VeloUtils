// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.moderation

import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import de.samthedev.veloutils.api.PunishmentType
import de.samthedev.veloutils.proxy.messaging.ProxyProtocolGateway
import de.samthedev.veloutils.proxy.util.ConfiguredMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component

public class ModerationEnforcement(
    private val moderation: PersistentModerationService,
    private val messages: ConfiguredMessages,
    private val scope: CoroutineScope,
    private val gateway: ProxyProtocolGateway,
) {

    @Subscribe
    public fun onLogin(event: LoginEvent): EventTask = EventTask.withContinuation { continuation ->
        scope.launch {
            runCatching { moderation.activeFor(event.player.uniqueId, event.player.remoteAddress.address) }
                .onSuccess { active ->
                    val denied = active.firstOrNull(ModerationPolicy::isLoginDenied)
                    if (denied != null) {
                        event.result = ResultedEvent.ComponentResult.denied(
                            messages.render("moderation.banned", mapOf("reason" to Component.text(denied.reason))),
                        )
                    }
                    continuation.resume()
                }
                .onFailure(continuation::resumeWithException)
        }
    }

    @Subscribe
    public fun onServerConnected(event: ServerPostConnectEvent) {
        scope.launch {
            val mute = runCatching { moderation.activeFor(event.player.uniqueId, event.player.remoteAddress.address) }
                .getOrNull()
                ?.firstOrNull { it.type == PunishmentType.MUTE }
            gateway.sendMuteState(event.player, mute)
        }
    }

}
