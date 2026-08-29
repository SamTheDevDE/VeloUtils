// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge.server

import io.papermc.paper.ServerBuildInfo
import net.kyori.adventure.key.Key
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.concurrent.TimeUnit

public class PlatformSchedulers(private val plugin: Plugin) {
    private val server: Server = plugin.server

    public val isFolia: Boolean
        get() = ServerBuildInfo.buildInfo().isBrandCompatible(Key.key("papermc", "folia"))

    public fun global(task: () -> Unit) {
        server.globalRegionScheduler.execute(plugin, task)
    }

    public fun entity(player: Player, task: () -> Unit) {
        player.scheduler.execute(plugin, task, null, 1L)
    }

    public fun async(task: () -> Unit) {
        server.asyncScheduler.runNow(plugin) { task() }
    }

    public fun repeatGlobal(initialTicks: Long, periodTicks: Long, task: () -> Unit): AutoCloseable {
        val scheduled = server.globalRegionScheduler.runAtFixedRate(plugin, { task() }, initialTicks, periodTicks)
        return AutoCloseable(scheduled::cancel)
    }

    public fun repeatAsync(initialSeconds: Long, periodSeconds: Long, task: () -> Unit): AutoCloseable {
        val scheduled = server.asyncScheduler.runAtFixedRate(plugin, { task() }, initialSeconds, periodSeconds, TimeUnit.SECONDS)
        return AutoCloseable(scheduled::cancel)
    }

    public fun laterAsync(delaySeconds: Long, task: () -> Unit) {
        server.asyncScheduler.runDelayed(plugin, { task() }, delaySeconds, TimeUnit.SECONDS)
    }
}
