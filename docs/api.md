# Public API

[Documentation](README.md) · [Addon development](addons.md) · [Architecture](architecture.md)

> This page is for plugin developers. Server owners can skip it.

Addon plugins compile against `veloutils-api` only. The API provides stable data models and focused services. It does not expose database connections, internal storage code, platform adapters, or protocol classes.

All module services are nullable. Check `api.modules` or the service property before use:

```kotlin
if (api.modules.state("moderation") == ModuleAvailability.ENABLED) {
    val moderation = requireNotNull(api.moderation)
    // Call suspend I/O methods from your plugin's asynchronous scope.
}
```

Unknown IDs return `UNAVAILABLE`. Known but unselected IDs return `DISABLED`. `active()` returns a read-only snapshot.

## Available services

| Property | Purpose | Typical platform |
|---|---|---|
| `modules` | Availability of known feature modules | Both |
| `network` | Network/server snapshots and transfers | Velocity |
| `maintenance` | Persistent maintenance state and updates | Velocity |
| `staff` | Staff presence and time snapshots | Velocity |
| `reports` | Report/helpop lifecycle | Velocity |
| `moderation` | Punishment creation, lookup, and revocation | Velocity |
| `placeholders` | Namespaced plain-text value providers | Both |
| `afk` | Local AFK state | Paper/Folia |
| `chat` | Local channel selection and registration | Paper/Folia |
| `presentation` | Refresh and temporary bossbar registration | Paper/Folia |
| `messaging` | Synchronous local UUID delivery | Paper/Folia |

Optional properties are `null` when their owning module or platform capability is unavailable. Do not retain implementation casts or assume a service exists because its configuration file exists.

## Velocity

Velocity 4.1 has no general-purpose service registry, so obtain the plugin instance:

```kotlin
val api = proxy.pluginManager
    .getPlugin("veloutils")
    .flatMap { it.instance }
    .filterIsInstance<VeloUtilsApi>()
    .orElseThrow { IllegalStateException("VeloUtils is not initialized") }
```

Declare an optional or required `veloutils` dependency in `velocity-plugin.json` so class loading and startup order are deterministic.

## Paper and Folia

The Paper artifact registers the API with Bukkit's standard services manager after module startup:

```kotlin
val api = server.servicesManager.load(VeloUtilsApi::class.java)
    ?: error("VeloUtils is not initialized")

val afk = api.afk
if (afk != null) {
    val status = afk.snapshot(player.uniqueId)
}

api.presentation?.refresh(player.uniqueId)
val delivery = api.messaging?.send(sender.uniqueId, target.uniqueId, "Hello")
```

Temporary/scheduled bossbars use immutable API models and return an owned registration:

```kotlin
val registration = api.presentation?.showBossBar(
    TemporaryBossBarRequest(
        id = "event-countdown",
        text = "<yellow>Event starts soon, {player}</yellow>",
        startsAt = Instant.now().plusSeconds(30),
        endsAt = Instant.now().plusSeconds(90),
        priority = 100,
    ),
)
// Close early when the owning addon disables.
registration?.close()
```

`ChatService.register` publishes local addon channels without exposing bridge internals. Close registrations when the addon shuts down. Network channels are not accepted here because they also need matching proxy permissions and formatting rules.

Declare `depend: [VeloUtils]` or `softdepend: [VeloUtils]` in `plugin.yml`. The same Paper artifact runs on Folia. Addon code must still follow Folia's scheduler rules.

## Placeholder extension

`PlaceholderService` is the stable provider contract. Providers return plain text under a namespace. VeloUtils escapes dynamic values before MiniMessage renders them. Registrations return `AutoCloseable` and should be closed when the addon disables.

The shared placeholder service and its size-limited, expiring cache live in `veloutils-core` and work on both platforms. Registered providers resolve under their namespace. PlaceholderAPI exposes `%veloutils_server%`, `%veloutils_network_online%`, `%veloutils_afk%`, `%veloutils_maintenance%`, and documented old aliases on Paper/Folia. Presentation templates can use installed PlaceholderAPI expansions as escaped `{papi_<expansion>_<identifier>}` values.

`MessagingService.send` handles immediate local UUID delivery. Command-driven cross-server delivery uses protocol v4 acknowledgements and saved proxy preferences. `PresentationService.refresh` uses the platform scheduler, so callers do not need to own the player's region.

## Compatibility

The API is still `1.0.0-SNAPSHOT`, so binary compatibility is not promised until `1.0.0`. Releases will follow semantic versioning. Addons should depend on the API artifact, check module availability, avoid implementation casts, and never block platform event threads while waiting for suspend services.

Public collections are snapshots. Registration methods return handles that the addon owns. Operations backed by storage are suspend functions. These rules form the supported API contract.
