# TAB integration

[Documentation](README.md) · [Configuration](configuration.md) · [Modules](modules.md)

VeloUtils does not render tablists, headers, footers, player-list names, sorting, nametags, scoreboards, or layouts. Install [TAB by NEZNAMY](https://github.com/NEZNAMY/TAB) on Velocity for those presentation features. VeloUtils optionally registers network-specific placeholders through TAB's public API and never edits TAB's files.

TAB is not required. VeloUtils starts normally when TAB is absent, when the integration is disabled, or when only placeholder registration is disabled. Integration failures disable only the TAB hook.

## VeloUtils configuration

The proxy's `integrations.yml` contains:

```yaml
tab:
  enabled: true
  placeholders:
    enabled: true
```

Both settings require a proxy restart. `enabled: false` avoids all TAB detection and API calls. `placeholders.enabled: false` keeps the integration dormant and registers nothing.

Optional backend display metadata belongs in the proxy's `config.yml`, because it describes the network rather than a TAB layout:

```yaml
servers:
  lobby:
    display-name: "<gradient:#7c3aed:#a855f7>Lobby</gradient>"
    max-players: 200
  hardcore:
    display-name: "<red>Hardcore</red>"
    max-players: 100
```

Keys must match registered Velocity server names. `display-name` is the one intentionally formatted placeholder; all other placeholders return raw data. An omitted `display-name` falls back to the registered server name. An omitted or zero `max-players` means unknown and makes the maximum-player placeholder return `0`.

## Placeholders

Use TAB's percent-delimited syntax exactly as shown.

| Placeholder | Scope | Output | Example | TAB refresh |
|---|---|---|---|---|
| `%veloutils_server%` | Player | Current registered Velocity server name, or empty | `lobby` | 500 ms |
| `%veloutils_server_display_name%` | Player | Configured MiniMessage display name, registered name fallback, or empty | `<gradient:#7c3aed:#a855f7>Lobby</gradient>` | 500 ms |
| `%veloutils_network_players%` | Network | Connected proxy player count | `128` | 1 s |
| `%veloutils_server_players%` | Player/server | Players on the current backend, or `0` | `42` | 1 s |
| `%veloutils_server_max_players%` | Player/server | Configured backend maximum, or `0` when unknown | `200` | 5 s |
| `%veloutils_ping%` | Player | Velocity-measured latency in milliseconds, or `0` if unavailable | `37` | 500 ms |
| `%veloutils_maintenance%` | Player/network | Effective global or current-server maintenance state | `true` | 1 s |
| `%veloutils_maintenance_reason%` | Player/network | Effective maintenance reason, or empty | `Network upgrade` | 1 s |
| `%veloutils_uptime%` | Network | Compact VeloUtils proxy uptime | `2d 4h 3m 9s` | 1 s |
| `%veloutils_backend_count%` | Network | Registered Velocity backend count | `6` | 5 s |
| `%veloutils_online_backend_count%` | Network | Backends currently confirmed online | `5` | 1 s |
| `%veloutils_network_status%` | Network | `online` or `maintenance` | `online` | 1 s |
| `%veloutils_server_status_<server>%` | Named backend | `online`, `unknown`, or `missing` | `%veloutils_server_status_lobby%` → `online` | 1 s |
| `%veloutils_server_players_<server>%` | Named backend | Player count, or `0` if missing | `%veloutils_server_players_lobby%` → `42` | 1 s |

Named-backend placeholders are registered once for every Velocity backend present at proxy startup. A backend is confirmed `online` when it has a recent VeloUtils Bridge heartbeat or at least one connected player. An empty backend without a fresh bridge heartbeat is `unknown`, not falsely reported as offline. `missing` means the server is no longer registered. Placeholder resolution reads current in-memory Velocity/VeloUtils state and never performs a server ping, database query, or network request.

Global maintenance takes precedence over current-server maintenance. Boolean values are lowercase `true` or `false`; presentation such as colors or friendly labels remains TAB's responsibility.

## TAB configuration example

This is a valid fragment for TAB 6.1.2 with MiniMessage components enabled:

```yaml
header-footer:
  enabled: true
  designs:
    default:
      header:
        - "<gradient:#7c3aed:#a855f7><bold>Example Network</bold></gradient>"
        - "<gray>Playing on <white>%veloutils_server_display_name%</white></gray>"
      footer:
        - "<gray>Players: <white>%veloutils_network_players%</white></gray>"
        - "<gray>Ping: <white>%veloutils_ping%</white>ms</gray>"

components:
  minimessage-support: true
```

Place this in TAB's own `config.yml`; VeloUtils will not create or modify it. TAB remains responsible for LuckPerms groups/prefixes, visual conditions, sorting, refresh presentation, and all other layout choices.

TAB clears API registrations during its own reload. VeloUtils listens for TAB's public `TabLoadEvent` and re-registers its placeholders. VeloUtils unregisters the event listener and every fixed placeholder during proxy shutdown.
