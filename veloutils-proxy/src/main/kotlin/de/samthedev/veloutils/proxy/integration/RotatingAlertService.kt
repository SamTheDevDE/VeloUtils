// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.integration

import com.velocitypowered.api.proxy.ProxyServer
import de.samthedev.veloutils.proxy.config.AlertConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import kotlin.random.Random

public class RotatingAlertService(
    private val proxy: ProxyServer,
    config: AlertConfig,
    private val sink: NetworkEventSink,
    scope: CoroutineScope,
    random: Random = Random.Default,
) : AutoCloseable {
    private val messages = config.messages.map(MiniMessage.miniMessage()::deserialize)
    private val plainMessages = config.messages
    private val rotation = messages.takeIf(List<Component>::isNotEmpty)?.let {
        AlertRotation(it.size, config.randomOrder, random)
    }
    private val job: Job? = if (config.enabled && messages.isNotEmpty()) scope.launch {
        delay(config.initialDelay.toMillis())
        while (isActive) {
            val selected = requireNotNull(rotation).next()
            broadcast(messages[selected])
            sink.emit(NetworkEventKind.ALERT, "Scheduled network alert", plainMessages[selected])
            delay(config.interval.toMillis())
        }
    } else null

    private fun broadcast(message: Component) {
        proxy.allPlayers.forEach { it.sendMessage(message) }
        proxy.consoleCommandSource.sendMessage(message)
    }

    override fun close() {
        job?.cancel()
    }
}
