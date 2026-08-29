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
| `config.yml` | Enable modules, secure the bridge, customize MOTD, or set server access rules |
| `messages.yml` | Change messages, colors, prefixes, and network-global chat format |
| `commands.yml` | Add or change custom transfer and information commands |
| `moderation.yml` | Configure punishments and the private IP-hash key |
| `integrations.yml` | Configure Discord webhooks and other optional integrations |
| `alerts.yml` | Configure rotating proxy announcements |
| `storage.yml` | Choose SQLite, MySQL/MariaDB, or PostgreSQL |

Validate changes with:

```text
/veloutils config validate
/veloutils config diff
```

`/veloutils reload` applies message text only. Restart for module, database, protocol, command, or scheduler changes.

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
    ├── messaging.yml           # private-message presentation
    └── presentation.yml        # TAB, scoreboard, nametag, and bossbar presentation
```

Do not edit `messaging-state.yml`. VeloUtils uses it to remember ignored players on standalone Paper/Folia servers.

Select backend modules in `config.yml`:

```yaml
modules:
  afk: false
  announcements: false
  chat: false
  messaging: false
  presentation: false
  moderation: false
  placeholders: true
  staff-chat: true
  network-alerts: true
```

AFK is optional. In `modules/afk.yml`, leave `kick-after: ""` to disable AFK kicks. A value such as `30m` kicks a player after they have been AFK for that additional amount of time.

Local announcements can play in order or in a non-repeating random order. Their interval must be at least 30 seconds.

When using Velocity, set `server-id` on every backend to its exact name from Velocity's server list. For example, a backend registered as `survival` must use `server-id: survival`. Standalone servers can leave it blank.

Presentation designs can be limited by server, world, group, or permission. Separate animation frames with `||`. PlaceholderAPI placeholders use the form `{papi_<expansion>_<identifier>}`, for example `{papi_luckperms_prefix}`.

`modules/chat.yml` contains server-wide, nearby/radius, and network-wide channels. Players select one with `/channel`. To use network-wide chat, enable `chat` on both Velocity and the backend. Change the network chat appearance with `chat.global-format` in the proxy's `messages.yml`.

Cross-server private messaging requires `messaging: true` on the proxy and every participating backend. Local `/msg` still works without Velocity.

`modules/presentation.yml` lets you switch TAB, scoreboards, nametags, and bossbars on or off separately. Headers and footers can use YAML lists for multiple lines. Lower `sort-order` numbers appear first. A scoreboard can contain up to 15 lines.

For example, after enabling the main `presentation` module, turn on its scoreboard with:

```yaml
scoreboard:
  enabled: true
```

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

Words inside braces are placeholders, such as `{player}`, `{server}`, or `{veloutils_network_online}`. Keep a placeholder exactly as written. PlaceholderAPI values start with `papi_`, for example `{papi_luckperms_prefix}`.

If a message fails validation, restore the original line from the bundled resource and make smaller changes until the error is clear.

## Connecting safely to Velocity

For a real network, use the same private secret of at least 32 characters on the proxy and backends, then set `protocol.authentication.required: true` everywhere. Keep remote commands disabled unless you need them. If enabled, allow only the exact command names you trust.

Do not commit populated configuration directories. They can contain database credentials, protocol secrets, IP-hash keys, or Discord webhook URLs.

## Need more detail?

- [Modules](modules.md) explains what every module does.
- [Commands and permissions](commands-and-permissions.md) lists every permission.
- [What currently works?](implementation-status.md) lists known limitations.
- The bundled [Velocity](../veloutils-proxy/src/main/resources/) and [Paper/Folia](../veloutils-bridge/src/main/resources/) files show every available setting.
