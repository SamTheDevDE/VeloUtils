# Implementation status

> [!WARNING]
> `1.0.0-SNAPSHOT` is deployable for testing, but it is not a stable release.

## Active

| Area | Available behavior |
|---|---|
| Network | Interactive discovery/transfer, paginated lists, honest bridge health, configurable commands |
| Maintenance | Persistent global/server enable, disable, and UUID allowlist enforcement |
| Server access | Permission and UUID rules with authorization-checked fallback |
| MOTD | Cached rotation, virtual hosts, maintenance text, counts, samples, favicon |
| Reports | Paginated filters, details/actions, report/helpop creation, claim/close lifecycle, cooldowns, clickable notices |
| Moderation | Offline identity/history, player-or-ID revocation, details/actions, self-confirmation, keyed IP bans, mute enforcement |
| Staff | Session/server-time persistence, paginated list, online/offline tracked-time lookup, activity notices |
| Bridge | Handshake, health, acknowledged alerts/staff channels, cached placeholders, remote commands |
| Storage | SQLite, MySQL/MariaDB, and PostgreSQL migrations |
| Integrations | Discord event webhooks, registered-server Limbo fallback, Modrinth update checks |
| Alerts | Sequential or randomized scheduled MiniMessage broadcasts |

## Limited

| Area | Current limitation |
|---|---|
| Maintenance | Scheduling, countdowns, and pre-activation transfer are not active |
| Moderation | IP bans and kicks require a live connection; Mojang HTTP name lookup is intentionally not used |
| Mutes | `/mute`, `/tempmute`, and `/unmute` require authenticated proxy-to-bridge messaging and the bridge on every enforcing backend |
| Staff chat | Direct bridge commands work; transparent proxy chat mode does not |
| Discord | Only event categories with a configured webhook URL are delivered |
| Limbo | The configured fallback must already be registered with Velocity; a LimboAPI server can provide it |
| Updates | The built-in provider currently checks Modrinth release versions only |
| Public API | Available after plugin initialization; disabled modules reject mutations |

## Not active

- Maintenance scheduling/countdowns
- Transparent chat mode (use `/sc` and `/ac`)
- Automatic import from the unrelated VelocityUtils plugin

These limitations must be resolved or deliberately reclassified before `1.0.0` is declared stable.
