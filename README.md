# VeloUtils

VeloUtils is a Kotlin-first utility platform for modern Minecraft networks. It combines a Velocity proxy plugin, an optional Paper/Folia bridge, a stable API, and a secure cross-server protocol.

> [!IMPORTANT]
> `1.0.0-SNAPSHOT` is a development build. Read [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) before deploying it.

## Highlights

- Network discovery, transfer, status, and configurable commands
- Consistent Adventure chat UI with safe actions and configurable pagination
- Persistent maintenance, reports, moderation, and staff sessions
- Offline-player moderation, player-oriented revocation, and complete punishment details
- Authenticated backend mute enforcement
- Permission and UUID-based server access rules
- Cached rotating and virtual-host MOTDs
- Scheduled network alerts and asynchronous Discord event webhooks
- Registered-server Limbo fallback and Modrinth update checks
- Versioned bridge protocol with optional HMAC authentication
- Paper and genuine Folia scheduler support
- SQLite, MySQL/MariaDB, and PostgreSQL storage
- Standard Velocity permissions, compatible with providers such as LuckPerms

## Requirements

| Component | Requirement |
|---|---|
| Runtime | Java 25 |
| Proxy | Velocity 4.1 or newer in the 4.x line |
| Backend bridge | Paper or Folia 26.2 |
| PlaceholderAPI | Optional |

## Installation

### Proxy

1. Copy `veloutils-proxy-<version>.jar` into Velocity's `plugins` directory.
2. Start the proxy once.
3. Review the generated files in `plugins/VeloUtils`.

### Backend bridge

1. Copy `veloutils-bridge-<version>.jar` into each Paper/Folia `plugins` directory.
2. Start each backend once.
3. Configure `plugins/VeloUtilsBridge/config.yml`.
4. Restart the backend.

The bridge is optional. Proxy-only features continue to work without it, and the bridge starts normally without PlaceholderAPI.

### Secure the connection

Before production use:

1. Generate a unique random secret of at least 32 bytes.
2. Put the same secret in the proxy and every bridge configuration.
3. Set protocol authentication to `required: true` on both sides.
4. Firewall backend servers from direct public access.
5. Enable Velocity secure player forwarding.

See [SECURITY.md](SECURITY.md) for the full deployment checklist.

## Configuration

VeloUtils separates configuration by responsibility:

| File | Purpose |
|---|---|
| `config.yml` | Modules, UI, protocol, compatibility, updates, MOTD, and server access |
| `messages.yml` | MiniMessage text |
| `commands.yml` | Move and informational commands |
| `moderation.yml` | Punishment and IP-hashing settings |
| `integrations.yml` | Optional external integrations |
| `alerts.yml` | Rotating network announcements |
| `storage.yml` | Database connection and pool settings |

Every file has a `config-version`. Existing files are read without being rewritten, so comments and formatting survive normal startup. New defaults are used in memory and can be inspected with `/veloutils config diff`. A real migration creates a `.pre-migration.bak` backup before changing a file. Invalid settings fail early with an actionable error.

## Project layout

| Module | Responsibility |
|---|---|
| `veloutils-api` | Public interfaces and immutable models |
| `veloutils-common` | Platform-neutral validation and policies |
| `veloutils-protocol` | Wire packets, authentication, and request tracking |
| `veloutils-proxy` | Velocity commands, policy, storage, and network state |
| `veloutils-bridge` | Backend-only Paper/Folia features |

Velocity 4.1 has no general service registry. The loaded `veloutils` plugin instance therefore implements `VeloUtilsApi` directly. Third-party plugins can obtain the plugin instance and cast it using only the API module.

## Documentation

- [Implementation status](IMPLEMENTATION_STATUS.md)
- [Commands and permissions](COMMANDS_AND_PERMISSIONS.md)
- [Architecture](ARCHITECTURE.md)
- [Migration guide](MIGRATION.md)
- [Feature decisions](FEATURE_MATRIX.md)
- [Security audit](SECURITY_AUDIT.md)
- [Clean-room reference audit](REFERENCE_AUDIT.md)
- [Contributing](CONTRIBUTING.md)

## Build from source

```bash
./gradlew clean qualityGate
./gradlew printVersion
```

The build uses Gradle 9.7.1, Kotlin 2.4.10, Java 25, and JUnit 5. Production source directories reject Java files. Plugin artifacts are written to each plugin module's `build/libs` directory.

## License

Copyright © 2026 SamTheDevDE. VeloUtils is licensed under GPL-3.0-only. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for dependency notices.
