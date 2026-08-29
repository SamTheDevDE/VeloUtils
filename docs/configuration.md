# Configuration

[Documentation](README.md) · [Modules](modules.md) · [Commands and permissions](commands-and-permissions.md)

Start with `plugins/VeloUtils/config.yml`. This file turns features on and off and contains the settings shared by the whole plugin. Enabled Paper/Folia features may create extra files under `plugins/VeloUtils/modules/`.

VeloUtils checks configuration when it starts. If something is wrong, it prints the setting and reason in the console instead of guessing what you meant.

## Safe editing routine

1. Make a backup of `plugins/VeloUtils`.
2. Stop the proxy or server when changing modules, storage, or security settings.
3. Use spaces in YAML files, not tab characters.
4. Keep `config-version` unchanged unless an official migration tells you otherwise.
5. Start the server and read any VeloUtils warnings in the console.
6. On Velocity, run `/veloutils config validate`.

## Velocity layout

```text
plugins/VeloUtils/
├── config.yml
├── messages.yml
├── commands.yml
├── moderation.yml
├── integrations.yml
├── alerts.yml
└── storage.yml
```

What each file is for:

| File | Change this file when you want to |
|---|---|
| `config.yml` | Enable modules, secure the bridge, customize MOTD, or set server access/metadata |
| `messages.yml` | Change messages, colors, prefixes, and network-global chat format |
| `commands.yml` | Add or change custom transfer and information commands |
| `moderation.yml` | Configure punishments and the private IP-hash key |
| `integrations.yml` | Configure TAB, Discord webhooks, and other optional integrations |
| `alerts.yml` | Configure rotating proxy announcements |
| `storage.yml` | Choose SQLite, MySQL/MariaDB, or PostgreSQL |

Validate changes with:

```text
/veloutils config validate
/veloutils config diff
```

`/veloutils reload` applies message text only. Restart for module, database, protocol, command, or scheduler changes.

Existing YAML files are not rewritten just because a release adds a setting. At runtime VeloUtils reads the administrator file first and uses the bundled value for supported fixed settings when that path is absent. Keyed administrator collections such as custom commands, server-access rules, virtual hosts, and webhooks are not populated from bundled examples on an upgrade. `/veloutils config diff` still reports every missing disk path, so you can copy and customize new settings deliberately. A message key missing from both sources produces a safe red component and one concise console warning instead of a MiniMessage parsing failure. Explicit versioned migrations remain separate; a migration that edits a file creates a `.pre-migration.bak` copy first.

| What you changed | How to apply it |
|---|---|
| `messages.yml` text | Run `/veloutils reload` |
| A module setting | Restart that proxy/server |
| Database or bridge security | Restart every affected proxy/server |
| A Paper/Folia module file | Restart that backend |

## Paper/Folia layout

```text
plugins/VeloUtils/
├── config.yml
└── modules/
    ├── afk.yml                 # created only when AFK is first enabled
    ├── announcements.yml       # created only when local announcements are first enabled
    ├── chat.yml                # local formatting and chat policy
    └── messaging.yml           # private-message presentation
```

Do not edit `messaging-state.yml`. VeloUtils uses it to remember ignored players on standalone Paper/Folia servers.

Select backend modules in `config.yml`:

```yaml
modules:
  afk: false
  announcements: false
  chat: false
  messaging: false
  moderation: false
  placeholders: true
  staff-chat: true
  network-alerts: true
```

AFK is optional. In `modules/afk.yml`, leave `kick-after: ""` to disable AFK kicks. A value such as `30m` kicks a player after they have been AFK for that additional amount of time.

Local announcements can play in order or in a non-repeating random order. Their interval must be at least 30 seconds.

When using Velocity, set `server-id` on every backend to its exact name from Velocity's server list. For example, a backend registered as `survival` must use `server-id: survival`. Standalone servers can leave it blank.

`modules/chat.yml` contains server-wide, nearby/radius, and network-wide channels. Players select one with `/channel`. To use network-wide chat, enable `chat` on both Velocity and the backend. Change the network chat appearance with `chat.global-format` in the proxy's `messages.yml`.

Cross-server private messaging requires `messaging: true` on the proxy and every participating backend. Local `/msg` still works without Velocity.

VeloUtils does not configure or render TAB, scoreboards, nametags, or layouts. Install TAB on Velocity and enable only the optional data hook in `integrations.yml`:

```yaml
tab:
  enabled: true
  placeholders:
    enabled: true
```

Optional server display names and maximums are configured in the proxy `config.yml`:

```yaml
servers:
  lobby:
    display-name: "<gradient:#7c3aed:#a855f7>Lobby</gradient>"
    max-players: 200
```

See the [TAB integration guide](tab-integration.md) for all placeholders and the TAB-side configuration example. VeloUtils never edits TAB's files.

Optional pre-maintenance transfer is configured on Velocity:

```yaml
maintenance:
  pre-activation-transfer:
    enabled: true
    before: 30s
    destinations: [lobby, limbo]
```

Players with `veloutils.maintenance.bypass` are not moved. Normal server-access listeners still authorize every connection request.

## Text, colors, and placeholders

Messages use MiniMessage formatting. Common examples:

```text
<red>Red text</red>
<bold>Bold text</bold>
<gradient:#7c3aed:#a855f7>Gradient text</gradient>
```

Administrator-controlled templates use strict MiniMessage, so opened tags must be closed. Their output capabilities differ by destination:

| Location | MiniMessage behavior |
|---|---|
| MOTD | Full supported Adventure formatting |
| Sample-player hover | MiniMessage converted to legacy client formatting |
| Configured global chat format | Full supported Adventure formatting |
| Player chat input | Restricted and permission controlled |
| Staff chat format | Full supported Adventure formatting |
| Announcements | Full supported Adventure formatting |

The sample-player list is a protocol string rather than an Adventure component. VeloUtils precompiles each MiniMessage entry, inserts dynamic values as literal text, and serializes it with section-sign legacy formatting. Colors, RGB/gradients on modern clients, bold, italic, underline, and strikethrough are preserved as far as the client supports them. Hover and click events cannot work in the vanilla sample-player tooltip. Long or RGB-heavy entries can also be constrained by the connecting client's status-tooltip behavior.

Example Velocity MOTD samples:

```yaml
motd:
  sample-players:
    - "<gradient:#7c3aed:#a855f7><bold>✦ Example Network ✦</bold></gradient>"
    - "<gray>Players Online: <white>{players}</white>/<white>{max_players}</white></gray>"
    - "<light_purple>➜</light_purple> <white>play.example.com</white>"
```

`{players}` and `{max_players}` are updated for every ping without reparsing the template. Registered VeloUtils plain-text placeholders, such as `{veloutils_network_online}`, are also available and are inserted safely.

The trusted global chat template lives in `messages.yml` and may use normal MiniMessage formatting plus component placeholders:

```yaml
chat:
  global-format: "<gradient:#7c3aed:#a855f7>[Global]</gradient> <player> <dark_gray>»</dark_gray> <white><message></white>"
```

`<player>`, `<server>`, and `<message>` are component insertion points. Player content is never concatenated into this template and cannot close its tags or restyle its player name, server label, or surrounding text.

Player-entered formatting is configured separately in Velocity `config.yml`:

```yaml
chat:
  player-formatting:
    enabled: true
    default:
      colors: false
      decorations: false
      gradients: false
    permissions:
      colors: veloutils.chat.format.colors
      decorations: veloutils.chat.format.decorations
      gradients: veloutils.chat.format.gradients
      full: veloutils.chat.format.full
```

With the default settings, `<red>Hello</red>` remains literal unless the sender has the corresponding permission. Colors include named and hex colors; decorations include bold, italic, underline, and strikethrough; gradients are separate. `full` means all of these safe presentation groups. It deliberately does not allow click, hover, insertion, font, selector, score, NBT, keybind, translatable, URL/action, newline, or reset tags. Unsupported tags stay literal, and malformed player input falls back to literal text. These permissions affect only player-entered global/staff chat, never the administrator's templates.

Words inside braces are VeloUtils template placeholders, such as `{player}`, `{server}`, or `{veloutils_network_online}`. Keep a placeholder exactly as written. PlaceholderAPI values on the Paper bridge start with `papi_`, for example `{papi_luckperms_prefix}`. TAB's API placeholders instead use percent syntax such as `%veloutils_server%`.

If a message fails validation, restore the original line from the bundled resource and make smaller changes until the error is clear.

## Connecting safely to Velocity

For a real network, use the same private secret of at least 32 characters on the proxy and backends, then set `protocol.authentication.required: true` everywhere. Keep remote commands disabled unless you need them. If enabled, allow only the exact command names you trust.

Do not commit populated configuration directories. They can contain database credentials, protocol secrets, IP-hash keys, or Discord webhook URLs.

## Need more detail?

- [Modules](modules.md) explains what every module does.
- [Commands and permissions](commands-and-permissions.md) lists every permission.
- [What currently works?](implementation-status.md) lists known limitations.
- The bundled [Velocity](../veloutils-proxy/src/main/resources/) and [Paper/Folia](../veloutils-bridge/src/main/resources/) files show every available setting.
