# Clean-room reference audit

**Audit date:** 2026-08-27

## Legal conclusion

Neither `VelocityUtils` nor `VelocityUtilsLink` contains a project `LICENSE`, `COPYING`, or `NOTICE` file. Their source files also contain no copyright or SPDX headers.

The standard Apache-2.0 notice in generated Gradle wrapper scripts applies only to those wrapper files. It does not license the surrounding plugin source.

VeloUtils therefore uses the projects only as behavioral references. It does not copy or mechanically translate their source, configuration prose, documentation, images, icon, package names, or branding. VeloUtils was independently created under GPL-3.0-only.

## What was reviewed

| Project | Inventory |
|---|---|
| Proxy reference | 56 Java production files, two YAML resources, icon, build files, README, wrapper, wiki images |
| Bridge reference | Three Java production files, one YAML descriptor, build files, README, wrapper |

The review covered commands, listeners, lifecycle, API/provider classes, SQL, configuration, messages, webhooks, update checks, Tebex, Limbo, and every plugin-message path. Build output, IDE state, `.gradle`, and VCS internals were excluded.

## Communication findings

The old pair used six unversioned channels:

- `velocityutils:staffchat`
- `velocityutils:adminchat`
- `velocityutils:placeholders`
- `velocityutils:alerts`
- `velocityutils:serverexecute`
- `velocityutils:sounds`

Packets lacked consistent size limits, request IDs, timeouts, negotiation, replay protection, signatures, and authorization proof. Backend command execution trusted delivered command strings.

VeloUtils replaces them with one `veloutils:main` channel using versioned JSON, bounded fields, UUID request IDs, timeouts, negotiation, HMAC-SHA256, nonce replay rejection, source binding, and command allowlists on both ends. It never deserializes arbitrary Java objects.

## Data and architecture findings

The old schema stored name-keyed bans and plaintext IP addresses. Blocking JDBC and HTTP appeared on command/event paths. Other rejected patterns included Velocity internals, Netty brand rewriting, deprecated chat replay, repeated YAML reads, and automatic icon rewriting.

VeloUtils instead uses UUID identities, versioned migrations, prepared statements, asynchronous JDBC, keyed IP hashes, bounded caches, immutable public models, and separate tables for reports, punishments, staff sessions, maintenance, and preferences.

## Dependency review

Reference dependencies included Velocity internals, Configurate, Adventure, LuckPerms, SQLite JDBC, bStats, legacy MySQL Connector/J, `netty-all`, LimboAPI, Paper, and PlaceholderAPI. A dependency license does not license the reference project itself.

VeloUtils does not reuse reference binaries or source. Current dependency licenses are summarized in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
