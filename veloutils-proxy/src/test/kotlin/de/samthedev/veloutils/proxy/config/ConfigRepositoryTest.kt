// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.config

import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ConfigRepositoryTest {
    @Test
    fun `load installs defaults and preserves unknown administrator fields`() {
        val directory = Files.createTempDirectory("veloutils-config")
        directory.resolve("config.yml").writeText(
            """
            config-version: 1
            debug: true
            custom-administrator-field: keep-me
            protocol:
              authentication:
                required: false
            """.trimIndent(),
        )

        val snapshot = ConfigRepository(directory).load()
        val reloaded = YamlConfigurationLoader.builder().path(directory.resolve("config.yml")).build().load()

        assertFalse(snapshot.protocol.requireAuthentication)
        assertEquals("keep-me", reloaded.node("custom-administrator-field").getString())
        assertEquals(1, reloaded.node("config-version").getInt())
        assertTrue(Files.exists(directory.resolve("messages.yml")))
    }

    @Test
    fun `version zero is migrated without replacing custom values`() {
        val directory = Files.createTempDirectory("veloutils-migration")
        directory.resolve("config.yml").writeText("config-version: 0\ndebug: true\n")

        ConfigRepository(directory).load()

        val migrated = YamlConfigurationLoader.builder().path(directory.resolve("config.yml")).build().load()
        assertEquals(1, migrated.node("config-version").getInt())
        assertTrue(migrated.node("debug").getBoolean())
        assertEquals("config-version: 0\ndebug: true\n", directory.resolve("config.yml.pre-migration.bak").readText())
    }

    @Test
    fun `normal startup does not rewrite readable commands yaml`() {
        val directory = Files.createTempDirectory("veloutils-format-regression")
        val readable = """
            config-version: 1

            move-commands:
              lobby:
                aliases:
                  - hub
                servers:
                  - lobby
                  - limbo
                permission: veloutils.command.lobby
                cooldown: 3s

            message-commands:
              discord:
                aliases:
                  - dc
                permission: veloutils.message.discord
                cooldown: 5s
                messages:
                  - "<gray>Discord: <click:open_url:'https://example.com'><aqua>Click here</aqua></click>"
        """.trimIndent()
        directory.resolve("commands.yml").writeText(readable)

        val repository = ConfigRepository(directory)
        repository.load()

        assertEquals(readable, directory.resolve("commands.yml").readText())
        assertTrue(repository.missingDefaults()["commands.yml"].orEmpty().any { it.startsWith("move-commands.survival.") })
    }

    @Test
    fun `rejects non Discord webhook destinations`() {
        val directory = Files.createTempDirectory("veloutils-webhook-config")
        directory.resolve("integrations.yml").writeText(
            """
            config-version: 1
            discord:
              webhooks:
                reports: "https://example.com/api/webhooks/secret"
            """.trimIndent(),
        )

        val failure = assertFailsWith<ConfigValidationException> { ConfigRepository(directory).load() }
        assertTrue(failure.message.orEmpty().contains("official HTTPS Discord webhook"))
    }
}
