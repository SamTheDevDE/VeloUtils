# Implementation status

[Documentation](README.md) · [Modules](modules.md) · [Configuration](configuration.md)

> [!WARNING]
> `1.0.0-SNAPSHOT` is a test build, not a stable production release. Back up your server and test it before wider use.

The first table shows features that are available now. The second explains limits that may matter when choosing or configuring those features.

## Available now

| Feature | What you can use |
|---|---|
| Network tools | Find players, view servers, transfer players, check backend health, and create custom commands |
| Maintenance | Global or per-server maintenance, allowlists, schedules, countdowns, and optional player transfers before maintenance |
| Server access | Restrict individual servers by permission or player UUID and send denied players to allowed fallbacks |
| MOTD | Rotating and hostname-specific MOTDs, maintenance text, player counts/samples, and icons |
| Reports and helpop | Player reports, help requests, staff claiming/closing, filters, pages, and clickable actions |
| Moderation | Bans, temporary bans, IP bans, kicks, warnings, mutes, history, details, and revocation |
| Staff tools | Online staff list, activity messages, and tracked total/server time |
| Proxy/backend connection | Health checks, network alerts, staff chat, placeholders, mute state, and optional remote commands |
| Storage | SQLite, MySQL/MariaDB, and PostgreSQL with automatic database upgrades |
| Integrations | Discord webhooks, Limbo fallback, and Modrinth update checks |
| Announcements | Timed announcements in order or non-repeating random order |
| AFK | Manual and automatic AFK, AFK duration, optional kick, and presentation indicators |
| Private messages | Local and cross-server `/msg`, delivery confirmation, reply, persistent ignores, ignore list, and social spy |
| Chat | Server, nearby/radius, and network channels, mentions, sounds, safe links, cooldowns, mute/clear, and spam controls |
| Presentation | Animated TAB headers/footers, ordering, scoreboards, nametags, AFK indicators, and bossbars |

## Important limits

| Feature | What to know |
|---|---|
| Maintenance transfers | Players with the bypass permission are not moved. Failed transfers use Velocity's normal connection handling |
| IP bans and kicks | The target must be online when the punishment is created |
| Mutes | Every enforcing backend needs the VeloUtils Paper/Folia JAR, backend moderation enabled, and bridge authentication |
| Staff chat | Use `/sc` and `/ac`. Normal chat is not automatically redirected into staff chat |
| Discord | An event is sent only when that event category has a webhook URL configured |
| Limbo | The fallback server must already be registered in Velocity |
| AFK | AFK state is local to each Paper/Folia backend and is not yet synchronized through Velocity |
| Custom chat channels | Addons can add local channels. New network-wide channel types need matching proxy support |
| Social spy | Each staff member must enable it again after reconnecting or restarting |
| TAB layouts | Normal multiline layouts and sorting work, but VeloUtils does not create fake player entries or packet-based fixed grids |
| Scoreboards | Use only one plugin to manage a player's scoreboard, otherwise the plugins can replace each other's display |

## Not provided

- Transparent chat mode (use `/sc` and `/ac`)
- Automatic import from the unrelated VelocityUtils plugin
- Synthetic/fake-player TAB slots
- Proxy-synchronized AFK state

These features are left out unless a safe, supported implementation becomes available. They are listed here so you do not spend time looking for settings that do not exist.
