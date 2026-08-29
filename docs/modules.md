# Modules

[Documentation](README.md) · [Getting started](getting-started.md) · [Configuration](configuration.md)

Modules are features you can turn on or off in `plugins/VeloUtils/config.yml`. You only need to enable the features your server uses.

Changing a module setting requires a restart. When a module is disabled, VeloUtils does not start its tasks, listeners, storage, or feature-specific services.

## Where modules run

- Proxy features are enabled in the Velocity `config.yml`.
- Local server features are enabled in the Paper/Folia `config.yml`.
- Network chat and cross-server private messages need the matching module enabled on both sides.

Paper/Folia features such as AFK, local chat, and announcements still work without Velocity.

## Common choices

| What you want | What to enable |
|---|---|
| Basic Velocity utilities | Keep the default Velocity modules |
| Network-wide chat | `chat` on Velocity and every participating backend |
| Cross-server private messages | `messaging` on Velocity and every participating backend |
| Network mutes | `moderation` on Velocity and every backend, plus bridge authentication |
| TAB, scoreboards, nametags, or layouts | Install TAB on Velocity; optionally enable VeloUtils's TAB integration |
| Standalone server chat and AFK | `chat` and `afk` on Paper/Folia. Velocity is not required |

## Velocity modules

| Setting | What it adds | Default |
|---|---|---|
| `network-commands` | `/find`, `/goto`, player/server lists, transfers, and custom network commands | On |
| `motd` | Rotating MOTDs, server-list icons, player samples, and maintenance MOTDs | On |
| `maintenance` | Global or per-server maintenance, schedules, allowlists, and countdowns | On |
| `server-access` | Permission- and UUID-based access rules for individual servers | On |
| `reports` | `/report`, `/helpop`, and the staff report queue | On |
| `moderation` | Bans, mutes, warnings, kicks, history, and enforcement | Off |
| `staff` | Online staff list and tracked play/server time | On |
| `staff-chat` | Network staff and admin chat through `/sc` and `/ac` | On |
| `chat` | Network-global chat sent from backend chat channels | Off |
| `messaging` | Cross-server `/msg`, `/reply`, and network-wide ignores | Off |
| `alerts` | Timed announcements across the proxy | On |
| `discord` | Sends selected VeloUtils events to Discord webhooks | Off |

If you only want basic proxy utilities, the default Velocity modules are a sensible starting point. Moderation, global chat, messaging, and Discord stay off until you choose to configure them.

## Paper/Folia modules

| Setting | What it adds | Default |
|---|---|---|
| `afk` | `/afk`, automatic AFK detection, AFK display, and optional AFK kicks | Off |
| `announcements` | Timed local announcements in order or random order | Off |
| `chat` | Server, radius, and network channels, mentions, links, and spam controls | Off |
| `messaging` | Local/cross-server private messages, reply, ignore, and social spy | Off |
| `moderation` | Enforces Velocity mute state on that backend | Off |
| `placeholders` | VeloUtils placeholders and optional PlaceholderAPI support | On |
| `staff-chat` | Lets backend players use `/sc` and `/ac` | On |
| `network-alerts` | Lets authorized users send `/vualert` messages | On |

An enabled feature creates its own file under `plugins/VeloUtils/modules/` when needed. To enforce network mutes, enable `moderation` on Velocity and every backend, then enable protocol authentication.

TAB integration is an optional Velocity integration rather than a module. It is configured in `integrations.yml`; see the [TAB integration guide](tab-integration.md). VeloUtils does not start a tablist rendering task on any backend.

## What "disabled" means

- No feature listeners or commands are activated.
- No repeating feature tasks are started.
- No large feature cache or feature-specific storage component is created.
- Module files are not loaded unless they are needed.
- Stopping the plugin also stops every enabled module cleanly.

For technical lifecycle and Folia scheduler details, see [Architecture](architecture.md). For features that are limited or unavailable, see [What currently works?](implementation-status.md).
