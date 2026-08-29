// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.proxy.config

import de.samthedev.veloutils.proxy.command.ConfiguredCommandLoader
import de.samthedev.veloutils.proxy.util.ConfiguredMessages
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
        assertTrue(snapshot.playerFormatting.enabled)
        assertFalse(snapshot.playerFormatting.defaults.colors)
        assertTrue(snapshot.tab.enabled)
        assertTrue(snapshot.tab.placeholdersEnabled)
        assertTrue(snapshot.serverMetadata.isEmpty())
        assertTrue(snapshot.serverAccessRules.isEmpty())
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
                  - "<gray>Discord: <click:open_url:'https://example.com'><aqua>Click here</aqua></click></gray>"
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

    @Test
    fun `server metadata and TAB integration settings are loaded`() {
        val directory = Files.createTempDirectory("veloutils-tab-config")
        directory.resolve("config.yml").writeText(
            """
            config-version: 1
            servers:
              lobby:
                display-name: "<aqua>Lobby</aqua>"
                max-players: 200
            """.trimIndent(),
        )
        directory.resolve("integrations.yml").writeText(
            """
            config-version: 1
            tab:
              enabled: false
              placeholders:
                enabled: false
            """.trimIndent(),
        )

        val snapshot = ConfigRepository(directory).load()

        assertFalse(snapshot.tab.enabled)
        assertFalse(snapshot.tab.placeholdersEnabled)
        assertEquals("<aqua>Lobby</aqua>", snapshot.serverMetadata.getValue("lobby").displayName)
        assertEquals(200, snapshot.serverMetadata.getValue("lobby").maximumPlayers)
    }

    @Test
    fun `invalid sample player MiniMessage identifies its exact index`() {
        val directory = Files.createTempDirectory("veloutils-invalid-sample")
        directory.resolve("config.yml").writeText(
            "config-version: 1\nmotd:\n  sample-players:\n    - \"<red>valid</red>\"\n    - \"<red>missing close\"\n",
        )

        val failure = assertFailsWith<ConfigValidationException> { ConfigRepository(directory).load() }

        assertTrue(failure.message.orEmpty().contains("config.yml: motd.sample-players[1]: invalid MiniMessage"))
    }

    @Test
    fun `all bundled proxy MiniMessage templates validate`() {
        val directory = Files.createTempDirectory("veloutils-bundled-templates")
        val repository = ConfigRepository(directory)

        repository.load()
        ConfiguredMessages(directory.resolve("messages.yml")).validate()
        ConfiguredCommandLoader.load(directory.resolve("commands.yml"))
    }
}
