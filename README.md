# VeloUtils

Modern utilities for Minecraft servers and networks.

**Modular by design. Enable what you need.**

VeloUtils is a modular utility plugin for Velocity, Paper, and Folia servers. It provides network tools, maintenance, moderation, chat, private messages, AFK, announcements, and optional data integration with TAB.

You choose which features are enabled. Disabled features do not register listeners, start tasks, or create their own runtime services.

> [!IMPORTANT]
> `1.0.0-SNAPSHOT` is a development build. Review the [implementation status](docs/implementation-status.md) before deploying it.

## What can it do?

- Find and move players, view server status, and create custom network commands
- Schedule global or per-server maintenance with countdowns and allowlists
- Run reports, help requests, staff tracking, bans, mutes, warnings, and punishment history
- Customize rotating MOTDs, server-list icons, player samples, and maintenance messages
- Use server, nearby, or network chat with mentions, links, cooldowns, and spam controls
- Send local or cross-server private messages with reply, ignore, and social spy
- Configure AFK detection and local announcements, and expose network data to TAB
- Send selected events to Discord and use an optional Limbo fallback
- Store data in SQLite, MySQL/MariaDB, or PostgreSQL
- Use the same backend JAR on Paper and Folia

## Requirements

| Component | Requirement |
|---|---|
| Runtime | Java 25 |
| Proxy | Velocity 4.1 or newer in the 4.x line |
| Backend server | Paper or Folia 26.2 |
| PlaceholderAPI | Optional |
| TAB | Optional; install on Velocity to use VeloUtils TAB placeholders |

## Which JAR do I install?

| Your server | Install |
|---|---|
| Velocity only | `VeloUtils-Velocity-<version>.jar` |
| Paper or Folia only | `VeloUtils-Paper-<version>.jar` |
| Velocity plus backend servers | Velocity JAR on the proxy and Paper JAR on every backend |

The Paper JAR supports both Paper and Folia. Start with the plain-language [installation guide](docs/getting-started.md).

## Quick installation

### Proxy

1. Copy `VeloUtils-Velocity-<version>.jar` into Velocity's `plugins` directory.
2. Start the proxy once.
3. Review the generated files in `plugins/VeloUtils`.

### Backend bridge

1. Copy `VeloUtils-Paper-<version>.jar` into each Paper/Folia `plugins` directory.
2. Start each backend once.
3. Configure `plugins/VeloUtils/config.yml`.
4. Restart the backend.

The backend plugin is optional. Proxy-only features continue to work without it, and the Paper/Folia plugin also works without Velocity or PlaceholderAPI.

## TAB integration

VeloUtils no longer renders a tablist itself. TAB by NEZNAMY owns headers, footers, player-list formatting, sorting, nametags, scoreboards, and layouts; VeloUtils optionally supplies cheap network, backend, maintenance, player-count, ping, and uptime placeholders through TAB's public API.

Enable the hook in the Velocity `integrations.yml`:

```yaml
tab:
  enabled: true
  placeholders:
    enabled: true
```

TAB remains optional. If it is missing, VeloUtils logs one informational message and continues normally. See the [TAB integration guide](docs/tab-integration.md) for the complete placeholder table, server display metadata, status semantics, and a working TAB configuration example.

### If you use Velocity with backends

Before production use:

1. Generate a unique random secret of at least 32 bytes.
2. Put the same secret in the proxy and every Paper/Folia configuration.
3. Set protocol authentication to `required: true` on both sides.
4. Firewall backend servers from direct public access.
5. Enable Velocity secure player forwarding.

The [getting-started guide](docs/getting-started.md) explains where these settings go. See [SECURITY.md](SECURITY.md) for the full production checklist.

## Configuration at a glance

VeloUtils separates configuration by responsibility:

| File | Purpose |
|---|---|
| `config.yml` | Modules, UI, protocol, compatibility, updates, MOTD, and server access |
| `messages.yml` | Plugin messages, colors, and formatting |
| `commands.yml` | Move and informational commands |
| `moderation.yml` | Punishment options and the private protection key for IP bans |
| `integrations.yml` | Optional external integrations |
| `alerts.yml` | Rotating network announcements |
| `storage.yml` | Database type, address, and login details |

You normally start with `config.yml`, choose the modules you want, restart, and then edit the files for those modules. VeloUtils reports invalid settings in the console instead of silently ignoring them.

Read [Configuration](docs/configuration.md) for examples or [Modules](docs/modules.md) to decide what to enable.

## For developers

Server owners can skip this section. Addon authors should read the [public API](docs/api.md) and [addon guide](docs/addons.md).

| Module | Responsibility |
|---|---|
| `veloutils-api` | Public interfaces and immutable models |
| `veloutils-common` | Platform-neutral validation and policies |
| `veloutils-core` | Module lifecycle, selectors, and placeholder/rendering infrastructure |
| `veloutils-protocol` | Wire packets, authentication, and request tracking |
| `veloutils-proxy` | Velocity commands, policy, storage, and network state |
| `veloutils-bridge` | Backend-only Paper/Folia features |

## Documentation

The [documentation index](docs/README.md) separates server-owner guides from developer details. Common starting points:

- Server owners: [getting started](docs/getting-started.md), [configuration](docs/configuration.md), and [commands and permissions](docs/commands-and-permissions.md)
- Addon authors: [public API](docs/api.md) and [addon development](docs/addons.md)
- Contributors: [architecture](docs/architecture.md), [modules](docs/modules.md), and [contributing](CONTRIBUTING.md)
- Upgrades and audits: [migration](docs/migration.md), [security audit](docs/security-audit.md), and [clean-room audit](docs/clean-room-audit.md)

## Build from source

```bash
./gradlew clean qualityGate
./gradlew printVersion
```

The build uses Gradle 9.7.1, Kotlin 2.4.10, Java 25, and JUnit 5. Production source directories reject Java files. Plugin artifacts are written to each plugin module's `build/libs` directory.

## License

Copyright © 2026 SamTheDevDE. VeloUtils is licensed under GPL-3.0-only. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for dependency notices.
