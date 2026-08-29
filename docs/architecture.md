# Architecture

[Documentation](README.md) · [Modules](modules.md) · [Public API](api.md)

> This page is for contributors and addon developers. Server owners do not need it to install or configure VeloUtils.

VeloUtils keeps public contracts, domain rules, transport, and platform code separate while producing only two deployable plugin artifacts.

## Deployment topology

```text
Velocity only                 Standalone backend              Combined network
┌──────────────────┐          ┌──────────────────┐             ┌──────────────────┐
│ Velocity artifact│          │ Paper/Folia JAR  │             │ Velocity artifact│
│ network authority│          │ local features   │             │ network authority│
└──────────────────┘          └──────────────────┘             └────────┬─────────┘
                                                                       │ authenticated
                                                          veloutils:main protocol
                                                                       │
                                                              ┌────────┴─────────┐
                                                              │ Paper/Folia JAR  │
                                                              │ backend context  │
                                                              └──────────────────┘
```

The Paper artifact detects the running platform through supported Paper build information. Paper and Folia use the same code with a shared scheduler wrapper. The `folia-supported: true` setting reflects real entity, global-region, and async scheduling support.

## Module boundaries

| Module | Owns | Must not own |
|---|---|---|
| `veloutils-api` | Public services and immutable models | Database or platform internals |
| `veloutils-common` | Validation, access rules, and size-limited shared tools | Velocity or Bukkit APIs |
| `veloutils-core` | Module lifecycle, selectors, placeholder snapshots, rendering | Platform listeners or storage implementations |
| `veloutils-protocol` | Packets, encoding, authentication, correlation | Platform behavior |
| `veloutils-proxy` | Network authority, commands, policy, storage | Backend-only world logic |
| `veloutils-bridge` | Paper/Folia context and actions | Network moderation or access authority |

## Request flow

```text
Backend player
    ↓ player-carried plugin message
Bridge → protocol validation → Velocity proxy
                                  ↓
                         policy and storage
                                  ↓
Bridge ← correlated response ← protocol gateway
```

Plugin messages are transport, not identity. The proxy binds messages to the actual `ServerConnection`, validates size, syntax, version, timestamp, nonce, signature, packet type, and request ID, then checks packet-specific identity and authorization.

Protocol v4 adds target-backend private-message delivery acknowledgements and persistent ignore synchronization. Compatibility negotiation permits older peers to connect, but features requiring newer packet types are unavailable until both artifacts are upgraded.

## State and configuration

- Configuration loads into immutable snapshots.
- Normal startup never saves existing YAML merely to merge defaults.
- Migrations create a `.pre-migration.bak` before changing a file.
- Reload validates every file, then replaces only message templates.
- Restart-only settings are not hot-swapped.
- Frequently used state stays in size-limited caches or read-only views.
- Persistent state is written asynchronously.
- Disabled backend modules do not install command executors or listeners and do not start tasks. Bukkit command labels remain statically declared in `plugin.yml`, as required by the supported command API.
- Disabled modules are never constructed. If startup fails, already-started modules stop in reverse order.
- Player identities use UUIDs and keep only a limited number of recent names for fast suggestions.
- Report and punishment pages use indexed `COUNT`, `LIMIT`, and `OFFSET` queries.

## Threading

| Work | Execution context |
|---|---|
| JDBC and file I/O | Dedicated coroutine/IO dispatcher |
| Folia entity work | Entity scheduler |
| Folia global state | Global region scheduler |
| Scoreboard objectives and per-viewer teams | Global region scheduler |
| Scoreboard assignment, chat radius/location, mentions/sounds | Entity scheduler |
| Backend blocking work | Async scheduler |
| Velocity connection futures | Suspended without blocking `join()` |

## Public API

Storage connections and implementation objects never cross the API boundary. Services expose immutable records and suspend functions for I/O. Because Velocity 4.1 has no general service registry, the loaded plugin instance implements `VeloUtilsApi` directly.

On Paper/Folia, the API is published through Bukkit's `ServicesManager` only after enabled modules finish startup. An addon should depend on `veloutils-api`, check `ModuleAvailability`, and retain ownership of any registration handle it creates.
