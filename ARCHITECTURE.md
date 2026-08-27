# Architecture

VeloUtils keeps public contracts, domain rules, transport, and platform code separate.

## Module boundaries

| Module | Owns | Must not own |
|---|---|---|
| `veloutils-api` | Public services and immutable models | Database or platform internals |
| `veloutils-common` | Validation, access rules, bounded utilities | Velocity or Bukkit APIs |
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

## State and configuration

- Configuration loads into immutable snapshots.
- Reload validates a replacement before publishing it.
- Restart-only settings are not hot-swapped.
- Hot-path state uses bounded caches or immutable views.
- Persistent state is written asynchronously.
- Disabled modules do not register their commands or listeners.

## Threading

| Work | Execution context |
|---|---|
| JDBC and file I/O | Dedicated coroutine/IO dispatcher |
| Folia entity work | Entity scheduler |
| Folia global state | Global region scheduler |
| Backend blocking work | Async scheduler |
| Velocity connection futures | Suspended without blocking `join()` |

## Public API

Storage connections and implementation objects never cross the API boundary. Services expose immutable records and suspend functions for I/O. Because Velocity 4.1 has no general service registry, the loaded plugin instance implements `VeloUtilsApi` directly.
