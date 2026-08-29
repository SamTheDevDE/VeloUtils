# Addon development

[Documentation](README.md) · [Public API](api.md) · [Architecture](architecture.md)

> This page is for plugin developers creating an addon. It is not required for normal server setup.

VeloUtils addons are ordinary Minecraft plugins managed by Velocity or Paper/Folia. There is no addon directory, custom classloader, dependency downloader, marketplace runtime, or VeloUtils-specific lifecycle.

An addon should depend only on `de.samthedev.veloutils:veloutils-api:<version>` at compile time and use the normal Velocity or Paper plugin dependency declaration. Do not depend on `veloutils-proxy`, `veloutils-bridge`, storage, or protocol implementation modules.

## Gradle

```kotlin
dependencies {
    compileOnly("de.samthedev.veloutils:veloutils-api:1.0.0-SNAPSHOT")
}
```

The snapshot API is currently built locally rather than published to a public Maven repository. Use a composite build or publish it to your local development repository until release publishing is configured.

Do not shade `veloutils-api` into an addon. The platform plugin dependency supplies the shared API classes at runtime.

## Platform dependencies

Paper/Folia `plugin.yml`:

```yaml
depend: [VeloUtils]
```

Velocity `velocity-plugin.json`:

```json
{
  "dependencies": [
    { "id": "veloutils", "optional": false }
  ]
}
```

Use an optional dependency only when every integration path is guarded against VeloUtils being absent.

## Module-aware integration

```kotlin
val reports = api.reports
if (api.modules.state("reports") == ModuleAvailability.ENABLED && reports != null) {
    // Launch from your own structured asynchronous scope.
    val recent = reports.history(playerId, limit = 20)
}
```

Paper addons obtain the API from `ServicesManager`. Velocity addons obtain the `veloutils` plugin instance. Complete snippets are in the [public API guide](api.md).

## Extension points

The current supported API includes service reads and updates, placeholder providers, and local chat-channel registration. Moderation events and arbitrary network chat channels are not published yet. Addons should never use internal implementation packages as a substitute. Network channels need matching proxy permissions and formatting rules.

Always close registrations during addon shutdown, return immutable/plain placeholder values, use UUID identities, and make database or network work asynchronous.

```kotlin
private var registration: AutoCloseable? = null

fun enable(api: VeloUtilsApi) {
    registration = api.placeholders?.register("myaddon") { context ->
        mapOf("profile" to loadCachedProfileName(context.playerId))
    }
}

fun disable() {
    registration?.close()
    registration = null
}
```

Provider callbacks should return cached or immediately available values. They may be called frequently by consumers, so they should never query a database or remote service for each request.

## Separate official plugins

- VeloEconomy should own currencies, balances, atomic transactions, history, auditing, and an economy API.
- VeloParties should own party membership, invitations, leadership, following, and party chat.
- VeloGuilds should own guild membership, ranks, roles, and guild chat.
- VeloFriends should own relationships, requests, privacy, and presence notifications.

These projects should be separate repositories and normal plugins. They may use VeloUtils placeholders and future chat extension points, but VeloUtils core will not include fake economy, party, guild, or friend features.
