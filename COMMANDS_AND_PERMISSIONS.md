# Commands and permissions

Permissions default to the platform's configured policy. VeloUtils never grants permissions itself.

## Administration and network

| Command | Permission | Notes |
|---|---|---|
| `/veloutils status` | `veloutils.command.admin` | Network and bridge health |
| `/veloutils reload` | `veloutils.command.admin` | Reloads safe resources only |
| `/veloutils version` | `veloutils.command.admin` | Build version |
| `/veloutils debug` | `veloutils.command.admin` | Redacted diagnostics |
| `/find <player>` | `veloutils.command.find` | Shows current server |
| `/goto <player>` | `veloutils.command.goto` | Joins the player's server |
| `/vlist` | `veloutils.command.list` | Lists players by server |
| `/network` | `veloutils.command.network` | Network summary |
| `/serverinfo <server>` | `veloutils.command.serverinfo` | Server and bridge state |
| `/send <player> <server>` | `veloutils.command.send` | Moves one player |
| `/sendall <server>` | `veloutils.command.sendall` | Moves all players |
| `/serverexecute <server> <command>` | `veloutils.command.serverexecute` | Disabled by default; requires dual allowlists |

## Feature commands

| Command | Permission |
|---|---|
| `/maintenance ...` | `veloutils.maintenance.command` |
| `/report <player> <reason>` | `veloutils.report.create` |
| `/helpop <message>` | `veloutils.helpop.create` |
| `/reports <view\|claim\|close> ...` | `veloutils.report.manage` |
| `/stafflist` | `veloutils.staff.list` |
| `/stafftime [player]` | `veloutils.staff.time` |
| `/ban`, `/tempban`, `/ipban`, `/tempipban` | `veloutils.moderation.<command>` |
| `/mute`, `/tempmute`, `/unmute` | `veloutils.moderation.<command>` |
| `/kick`, `/warn`, `/history`, `/checkban` | `veloutils.moderation.<command>` |
| `/unban <punishment-id>` | `veloutils.moderation.unban` |
| Bridge `/sc`, `/ac` | `veloutils.chat.<channel>` |
| Bridge `/vualert` | `veloutils.bridge.alert` |

Mute commands are registered only when protocol authentication is required. Install the bridge on every backend where chat must be enforced and configure the same shared secret on both sides.

## Policy permissions

- `veloutils.maintenance.bypass`
- `veloutils.maintenance.notify`
- `veloutils.server-access.bypass`
- Per-server permissions configured in `config.yml`
- `veloutils.staff.member`
- `veloutils.staff.notify`
- `veloutils.staff.time.exclude`
- `veloutils.moderation.ip.view`

Move and informational command permissions come directly from `commands.yml`. Legacy `velocityutils.*` aliases are disabled by default.
