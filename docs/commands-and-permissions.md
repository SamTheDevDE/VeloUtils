# Commands and permissions

[Documentation](README.md) · [Configuration](configuration.md) · [Modules](modules.md)

This page lists every VeloUtils command and the permission needed to use it. On Velocity, permissions normally come from LuckPerms or another permission plugin. VeloUtils does not automatically give players staff access.

How to read command examples:

- `<player>` means the value is required.
- `[page]` means the value is optional.
- "Player" means the command must be run in game.
- "Both" means a player or the console can run it.
- A "known player" can be online or someone VeloUtils has seen before.

All commands support console unless the table says "Player." Clickable buttons become normal command suggestions in console output.

Common aliases include `/vu` for `/veloutils`, `/tell`, `/w`, and `/whisper` for `/msg`, `/r` for `/reply`, and `/ch` for `/channel`. Configured network commands may define their own aliases in `commands.yml`.

## Useful first commands

| Command | When to use it |
|---|---|
| `/veloutils status` | Check whether VeloUtils and connected backends are healthy |
| `/veloutils config validate` | Check your configuration for errors |
| `/veloutils config diff` | See new settings that are missing from an older config |
| `/serverinfo <server>` | Check one Velocity server and its backend connection |
| `/maintenance status` | Check active or scheduled maintenance |

Jump to [Administration](#administration), [Network](#network), [Maintenance and staff](#maintenance-and-staff), [Reports](#reports), [Moderation](#moderation), [Paper/Folia commands](#paperfolia-commands), or the [complete permission list](#complete-permission-reference).

## Administration

| Command | Purpose | Permission | Sender |
|---|---|---|---|
| `/veloutils` | Permission-aware administration dashboard | Any dashboard capability | Both |
| `/veloutils status` | Player, server, and bridge status | `veloutils.admin.status` | Both |
| `/veloutils reload` | Validate all files and reload `messages.yml` | `veloutils.admin.reload` | Both |
| `/veloutils version` | Installed build information | `veloutils.admin.version` | Both |
| `/veloutils debug` | Redacted diagnostics | `veloutils.admin.debug` | Both |
| `/veloutils config validate` | Parse YAML and MiniMessage without changing live state | `veloutils.admin.config` | Both |
| `/veloutils config diff` | List bundled settings missing locally | `veloutils.admin.config` | Both |

Example: `/vu config validate`. Most settings require a restart. `/veloutils reload` only applies changed messages.

## Network

| Command | Purpose | Permission | Sender |
|---|---|---|---|
| `/find <online-player>` | Show server and ping, with a Go To action | `veloutils.network.find` | Both |
| `/goto <online-player>` | Join the target's server | `veloutils.network.goto` | Player |
| `/vlist [page]` | Paginated players by server | `veloutils.network.list` | Both |
| `/network [page]` | Paginated network overview | `veloutils.network.status` | Both |
| `/serverinfo <server>` | Registration and honest bridge health | `veloutils.network.serverinfo` | Both |
| `/send <online-player> <server>` | Move one player | `veloutils.network.send` | Both |
| `/sendall <server>` | Move all players and summarize failures | `veloutils.network.sendall` | Both |
| `/serverexecute <server> <command>` | Run a command allowed by both protocol allowlists | `veloutils.network.execute` | Both |

Examples: `/find Alex`, `/network 2`, `/send Alex survival`. `/serverexecute` exists only when authenticated remote commands are enabled. Configured server-access rules still apply to transfers.

## Maintenance and staff

| Command | Purpose | Permission | Sender |
|---|---|---|---|
| `/maintenance status` | Show global/server state and allowlist size | `veloutils.maintenance.manage` | Both |
| `/maintenance enable [global\|server] [reason]` | Enable persistent maintenance | `veloutils.maintenance.manage` | Both |
| `/maintenance disable [global\|server]` | Disable maintenance | `veloutils.maintenance.manage` | Both |
| `/maintenance schedule <global\|server> <delay> <duration\|permanent> <reason>` | Persist a future maintenance window with countdowns | `veloutils.maintenance.manage` | Both |
| `/maintenance cancel <global\|server>` | Cancel a future maintenance window | `veloutils.maintenance.manage` | Both |
| `/maintenance allow <known-player>` | Add an online or known offline player | `veloutils.maintenance.manage` | Both |
| `/maintenance disallow <known-player>` | Remove a player from the allowlist | `veloutils.maintenance.manage` | Both |
| `/stafflist [page]` | List tracked online staff | `veloutils.staff.list.view` | Both |
| `/stafftime` | Show your last seven days of time | `veloutils.staff.time.view.self` | Player |
| `/stafftime <known-player>` | Show another member's tracked time | `veloutils.staff.time.view.others` | Both |

Examples: `/maintenance enable global Database work`, `/maintenance schedule survival 10m 30m Update`, `/stafftime Alex`.

## Reports

| Command | Purpose | Permission | Sender |
|---|---|---|---|
| `/report <online-player> <reason>` | Create a player report | `veloutils.reports.create` | Player |
| `/helpop <message>` | Create a help request | `veloutils.helpop.create` | Player |
| `/reports [page]` | List all reports | `veloutils.reports.view` | Both |
| `/reports <open\|claimed\|closed> [page]` | Filter reports | `veloutils.reports.view` | Both |
| `/reports mine [page]` | List reports assigned to you | `veloutils.reports.view` | Player |
| `/reports view <id>` | Open details and valid actions | `veloutils.reports.view` | Both |
| `/reports claim <id>` | Claim an open report | `veloutils.reports.claim` | Player |
| `/reports close <id> <resolution>` | Close with an audit resolution | `veloutils.reports.close` | Both |

Examples: `/reports open 2`, `/reports close 18 Reviewed evidence`. New-report notices require `veloutils.reports.notify` and include a View action.

## Moderation

A player name may refer to someone online or a name VeloUtils has seen before. Punishment IDs may be written as `41` or `#41`.

| Command | Purpose | Permission | Sender |
|---|---|---|---|
| `/ban <player> [reason]` | Permanent UUID ban | `veloutils.moderation.ban` | Both |
| `/tempban <player> <duration> [reason]` | Expiring UUID ban | `veloutils.moderation.tempban` | Both |
| `/ipban <online-player> [reason]` | Keyed-hash IP ban | `veloutils.moderation.ipban` | Both |
| `/tempipban <online-player> <duration> [reason]` | Expiring keyed-hash IP ban | `veloutils.moderation.tempipban` | Both |
| `/kick <online-player> [reason]` | Disconnect an online player | `veloutils.moderation.kick` | Both |
| `/warn <player> [reason]` | Record a warning | `veloutils.moderation.warn` | Both |
| `/mute <player> [reason]` | Permanent mute | `veloutils.moderation.mute` | Both |
| `/tempmute <player> <duration> [reason]` | Expiring mute | `veloutils.moderation.tempmute` | Both |
| `/unban <player\|#id> [reason]` | Revoke or list matching active bans | `veloutils.moderation.unban` | Both |
| `/unmute <player\|#id> [reason]` | Revoke or list matching active mutes | `veloutils.moderation.unmute` | Both |
| `/history <player> [page]` | Paginated online/offline history | `veloutils.moderation.history.view` | Both |
| `/checkban <player>` | List effective UUID/IP bans | `veloutils.moderation.ban.view` | Both |
| `/punishment <id>` | Full punishment and revocation audit | `veloutils.moderation.punishment.view` | Both |

Examples: `/tempban Alex 3d Abuse`, `/unban Alex Appeal accepted`, `/punishment 41`. Durations accept `10m`, `2h`, `3d`, or `1w`. Trying to punish yourself shows a temporary Confirm/Cancel prompt unless you have the self-punishment bypass permission. IP bans and kicks need the target to be online. Network mutes need the Paper/Folia bridge and bridge authentication.

## Paper/Folia commands

| Command | Purpose | Permission | Sender |
|---|---|---|---|
| `/sc <message>` | Staff chat with delivery result | `veloutils.chat.staff.use` | Player |
| `/ac <message>` | Admin chat with delivery result | `veloutils.chat.admin.use` | Player |
| `/vualert <message>` | Authenticated network alert | `veloutils.alert.broadcast` | Both (console needs an online player) |
| `/afk` | Toggle local AFK state when the module is enabled | `veloutils.afk.toggle` | Player |
| `/chat <mute\|unmute\|clear>` | Manage standalone local chat | `veloutils.chat.manage` | Both |
| `/channel [channel]` | List or select server/radius/network chat channels | Channel-specific permission | Player |
| `/msg <player> <message>` | Send a local or cross-server private message | `veloutils.messaging.use` | Player |
| `/reply <message>` | Reply to the most recent partner | `veloutils.messaging.use` | Player |
| `/ignore <known-player>` | Persistently ignore a UUID locally and across the network | `veloutils.messaging.use` | Player |
| `/unignore <known-player>` | Remove a persistent ignore | `veloutils.messaging.use` | Player |
| `/ignorelist` | List persistent ignored identities | `veloutils.messaging.use` | Player |
| `/socialspy` | Toggle local private-message observation | `veloutils.messaging.socialspy` | Player |

Recipients need `veloutils.chat.staff.receive` or `veloutils.chat.admin.receive`. The bridge reports permission, module, validation, recipient, and timeout failures instead of failing silently.

Cross-server messaging and network-global chat require their module to be enabled on both the proxy and participating backend. Local messaging and server/radius chat continue to work on a standalone Paper/Folia server.

## Complete permission reference

Velocity permissions are denied until your permission plugin grants them. Paper/Folia command permissions default to server operators unless the table or `plugin.yml` says otherwise.

| Permission | Capability | Legacy alias |
|---|---|---|
| `veloutils.admin.status` | Runtime/network status | `veloutils.command.admin` |
| `veloutils.admin.reload` | Validate and reload messages | `veloutils.command.admin` |
| `veloutils.admin.debug` | Redacted diagnostics | `veloutils.command.admin` |
| `veloutils.admin.version` | Installed version | `veloutils.command.admin` |
| `veloutils.admin.config` | Configuration validate/diff | `veloutils.command.admin` |
| `veloutils.network.find` | Locate players | `veloutils.command.find` |
| `veloutils.network.goto` | Follow players | `veloutils.command.goto` |
| `veloutils.network.list` | Players by server | `veloutils.command.list` |
| `veloutils.network.status` | Network overview | `veloutils.command.network` |
| `veloutils.network.serverinfo` | Server/bridge details | `veloutils.command.serverinfo` |
| `veloutils.network.send` | Move one player | `veloutils.command.send` |
| `veloutils.network.sendall` | Move all players | `veloutils.command.sendall` |
| `veloutils.network.execute` | Dual-allowlisted backend command | `veloutils.command.serverexecute` |
| `veloutils.maintenance.manage` | State and allowlist changes | `veloutils.maintenance.command` |
| `veloutils.maintenance.bypass` | Enter during maintenance | None |
| `veloutils.maintenance.notify` | Maintenance activity notices | None |
| `veloutils.staff.member` | Staff identity/tracking | None |
| `veloutils.staff.list.view` | Online staff list | `veloutils.staff.list` |
| `veloutils.staff.time.view.self` | Personal tracked time | `veloutils.staff.time` |
| `veloutils.staff.time.view.others` | Another member's time | `veloutils.staff.time` |
| `veloutils.staff.activity.notify` | Staff activity notices | `veloutils.staff.notify` |
| `veloutils.staff.time.exclude` | Exclude from tracking | None |
| `veloutils.chat.staff.use` | Send staff chat | `veloutils.chat.staff` |
| `veloutils.chat.staff.receive` | Receive staff chat | `veloutils.chat.staff` |
| `veloutils.chat.admin.use` | Send admin chat | `veloutils.chat.admin` |
| `veloutils.chat.admin.receive` | Receive admin chat | `veloutils.chat.admin` |
| `veloutils.reports.create` | Create reports | `veloutils.report.create` |
| `veloutils.helpop.create` | Create help requests | None |
| `veloutils.reports.view` | List/view reports | `veloutils.report.manage` |
| `veloutils.reports.claim` | Claim reports | `veloutils.report.manage` |
| `veloutils.reports.close` | Close reports | `veloutils.report.manage` |
| `veloutils.reports.notify` | New-report notices | `veloutils.report.manage` |
| `veloutils.moderation.ban` | Permanent bans | Unchanged |
| `veloutils.moderation.tempban` | Temporary bans | Unchanged |
| `veloutils.moderation.ipban` | Permanent IP bans | Unchanged |
| `veloutils.moderation.tempipban` | Temporary IP bans | Unchanged |
| `veloutils.moderation.unban` | Revoke bans | Unchanged |
| `veloutils.moderation.kick` | Kick players | Unchanged |
| `veloutils.moderation.warn` | Warnings | Unchanged |
| `veloutils.moderation.mute` | Permanent mutes | Unchanged |
| `veloutils.moderation.tempmute` | Temporary mutes | Unchanged |
| `veloutils.moderation.unmute` | Revoke mutes | Unchanged |
| `veloutils.moderation.history.view` | Punishment history | `veloutils.moderation.history` |
| `veloutils.moderation.ban.view` | Effective bans | `veloutils.moderation.checkban` |
| `veloutils.moderation.punishment.view` | Full punishment details | `veloutils.moderation.history` |
| `veloutils.moderation.ip.view` | Include IP bans in checks and open their details | None |
| `veloutils.moderation.self-punish` | Skip self-target confirmation | None |
| `veloutils.alert.broadcast` | Network alerts | `veloutils.bridge.alert` |
| `veloutils.afk.toggle` | Toggle personal AFK state | None |
| `veloutils.afk.bypass` | Ignore automatic AFK state and kicks | None |
| `veloutils.chat.manage` | Mute, unmute, or clear local chat | None |
| `veloutils.chat.mute.bypass` | Speak while local chat is muted | None |
| `veloutils.chat.cooldown.bypass` | Bypass local cooldown/duplicate checks | None |
| `veloutils.chat.local` | Use the bundled radius channel | None |
| `veloutils.chat.global.use` | Use network-global chat | None |
| `veloutils.messaging.use` | Use local/network private messaging | None |
| `veloutils.messaging.socialspy` | Observe local private-message deliveries | None |
| `veloutils.server-access.bypass` | Bypass all server rules | None |
| Configured per-server node | Enter that server | None |

Legacy aliases are enabled by default under `compatibility.legacy-permissions` in both proxy and bridge configs. Each used alias logs one concise migration warning, not one warning per check.

## Recommended LuckPerms groups

These examples are not a required hierarchy. VeloUtils relies on the permission provider for wildcard matching.

```text
lp group moderator permission set veloutils.staff.* true
lp group moderator permission set veloutils.chat.staff.* true
lp group moderator permission set veloutils.reports.* true
lp group moderator permission set veloutils.moderation.* true

lp group administrator parent add moderator
lp group administrator permission set veloutils.network.* true
lp group administrator permission set veloutils.maintenance.* true
lp group administrator permission set veloutils.chat.admin.* true

lp group developer permission set veloutils.admin.status true
lp group developer permission set veloutils.admin.debug true
lp group developer permission set veloutils.admin.version true

lp group owner permission set veloutils.* true
```

Review broad wildcards before production use, especially network execution, self-punishment bypass, and server-access bypass.
