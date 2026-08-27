# Implementation status

> [!WARNING]
> `1.0.0-SNAPSHOT` is deployable for testing, but it is not a stable release.

## Active

| Area | Available behavior |
|---|---|
| Network | Discovery, transfer, list, health, configurable move/message commands |
| Maintenance | Persistent global/server enable, disable, and UUID allowlist enforcement |
| Server access | Permission and UUID rules with authorization-checked fallback |
| MOTD | Cached rotation, virtual hosts, maintenance text, counts, samples, favicon |
| Reports | Report/helpop creation, IDs, claim, close, history, cooldowns, notices |
| Moderation | Ban, temporary ban, keyed IP bans, kick, warn, mute, temporary mute, history, revocation, login and backend chat checks |
| Staff | Session and server-time persistence, list, tracked-time command |
| Bridge | Handshake, health, alerts, staff channels, cached placeholders, remote commands |
| Storage | SQLite, MySQL/MariaDB, and PostgreSQL migrations |
| Integrations | Discord event webhooks, registered-server Limbo fallback, Modrinth update checks |
| Alerts | Sequential or randomized scheduled MiniMessage broadcasts |

## Limited

| Area | Current limitation |
|---|---|
| Maintenance | Scheduling, countdowns, and pre-activation transfer are not active |
| Moderation | Built-in commands require an online target or punishment ID |
| Mutes | `/mute`, `/tempmute`, and `/unmute` require authenticated proxy-to-bridge messaging and the bridge on every enforcing backend |
| Staff chat | Direct bridge commands work; transparent proxy chat mode does not |
| Discord | Only event categories with a configured webhook URL are delivered |
| Limbo | The configured fallback must already be registered with Velocity; a LimboAPI server can provide it |
| Updates | The built-in provider currently checks Modrinth release versions only |
| Public API | Available after plugin initialization; disabled modules reject mutations |

## Not active yet

- Tebex client
- Legacy configuration importer

These limitations must be resolved or deliberately reclassified before `1.0.0` is declared stable.
