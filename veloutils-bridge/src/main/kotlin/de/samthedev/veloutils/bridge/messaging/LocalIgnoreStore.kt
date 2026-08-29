// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.bridge.messaging

import de.samthedev.veloutils.bridge.server.PlatformSchedulers
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

internal class LocalIgnoreStore(
    private val path: Path,
    private val schedulers: PlatformSchedulers,
) {
    private val requestedRevision = AtomicLong()
    private var writtenRevision: Long = 0

    fun load(): Map<UUID, Map<UUID, String>> {
        if (Files.notExists(path)) return emptyMap()
        val root = YamlConfigurationLoader.builder().path(path).build().load()
        require(root.node("config-version").getInt(1) == 1) { "Unsupported messaging-state.yml config-version" }
        return root.node("players").childrenMap().mapNotNull { (ownerKey, ownerNode) ->
            val owner = runCatching { UUID.fromString(ownerKey.toString()) }.getOrNull() ?: return@mapNotNull null
            val entries = ownerNode.node("ignored").childrenMap().mapNotNull entries@{ (targetKey, targetNode) ->
                val target = runCatching { UUID.fromString(targetKey.toString()) }.getOrNull() ?: return@entries null
                val name = targetNode.getString()?.takeIf(String::isNotBlank) ?: return@entries null
                target to name.take(16)
            }.toMap()
            owner to entries
        }.toMap()
    }

    fun save(snapshot: Map<UUID, Map<UUID, String>>) {
        val revision = requestedRevision.incrementAndGet()
        schedulers.async { persist(snapshot, revision) }
    }

    private fun persist(snapshot: Map<UUID, Map<UUID, String>>, revision: Long) = synchronized(this) {
        if (revision < writtenRevision) return@synchronized
        Files.createDirectories(path.parent)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        val loader = YamlConfigurationLoader.builder().path(temporary).build()
        val root = loader.createNode().also { it.node("config-version").set(1) }
        snapshot.forEach { (owner, entries) ->
            entries.forEach { (target, name) -> root.node("players", owner.toString(), "ignored", target.toString()).set(name) }
        }
        loader.save(root)
        runCatching {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.getOrElse { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING) }
        writtenRevision = revision
    }
}
