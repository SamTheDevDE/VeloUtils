# Migration guide

[Documentation](README.md) · [Configuration](configuration.md) · [Migration map](migration-map.md)

Use this page only when updating an existing VeloUtils or VelocityUtils installation. New installations should follow [Getting started](getting-started.md) instead.

VeloUtils tries to preserve existing data and creates a `.pre-migration.bak` copy before it changes a configuration file. You should still make your own backup first.

## Safe update checklist

1. Back up the proxy/backend `plugins/VeloUtils` directories and the database.
2. If you use both Velocity and backend plugins, replace both JAR types together.
3. Test the update on a copy of your network when possible.
4. Run `/veloutils config validate` and `/veloutils config diff`.
5. Read the console and fix configuration warnings before players join.
6. Check the permission changes below.
7. Restart after configuration changes. `/veloutils reload` only applies changed messages.

## Important owner changes

- The backend file is now `VeloUtils-Paper-<version>.jar` and works on both Paper and Folia.
- Backend data now belongs in `plugins/VeloUtils`, not `plugins/VeloUtilsBridge`.
- Move an old bridge `config.yml` into the new directory before first startup.
- AFK, local announcements, chat, messaging, presentation, and backend moderation default to off when their module setting is missing.
- Each backend's `server-id` should match its exact registered Velocity name.
- Network mute enforcement needs moderation enabled on Velocity and every backend.
- Protocol v4 adds private-message delivery confirmation and network-wide persistent ignores. Older proxy/backend pairs cannot use those additions.

Database migration 3 creates the table used for network-wide ignores. Existing standalone ignores remain in `messaging-state.yml` and are copied to the proxy database when the player reconnects. Private-message text is not stored.

## Only for addon developers

The snapshot API now exposes `ModuleService`, placeholders, AFK, presentation, and messaging. Feature services are nullable so standalone Paper/Folia and disabled modules are represented without throwing placeholder implementations. Addon source must null-check optional services and should check `api.modules.state(id)` before mutation. `MaintenanceUpdate.Enable` also carries an optional scheduled end. These are pre-`1.0.0` source/binary changes and are not presented as stable compatibility.

`PresentationService` now includes temporary bossbar registration and `VeloUtilsApi.chat` exposes local channel registration. Recompile snapshot-era addons against the updated API.

Server owners who do not develop addons can skip this section.

## Removed or renamed settings

`maintenance.yml` was removed because it contained no settings that the plugin actually used. Maintenance state remains in storage. Presentation uses `messages.yml`, and server fallbacks use `config.yml`.

The following old settings were removed because they did not work or are no longer needed:

- The built-in `permissions:` map and move-command `fallback:` field in `commands.yml` were unused and are gone.
- Ignored SQLite path and timeout settings are gone. SQLite always uses `plugins/VeloUtils/data.db`.
- The unused `staff.week-start` and ineffective `store-ip-hashes` settings are gone. IP bans always use the required protected hash.
- Unused old message keys were removed. Active message templates remain in `messages.yml`.
- The unused top-level `debug` setting was removed. `/veloutils debug` remains available to users with permission and hides sensitive values.
- The old unused bridge `server-name` field became the working `server-id` field. `placeholder-cache-seconds` remains removed. `heartbeat` controls how often the backend reports its status.
- Unimplemented LuckPerms/Tebex integration settings are gone.
- The former `modules.tebex` flag is gone.

## Old permission mappings

Legacy checks are enabled by default:

```yaml
compatibility:
  legacy-permissions:
    enabled: true
    warn: true
```

Use the same compatibility block on the backend. Old permissions continue to work while compatibility is enabled and produce one migration warning. Disable compatibility only after updating every group.

| Old permission | New permission |
|---|---|
| `veloutils.command.admin` | The matching `veloutils.admin.status`, `.reload`, `.debug`, `.version`, and `.config` capabilities |
| `veloutils.command.find` | `veloutils.network.find` |
| `veloutils.command.goto` | `veloutils.network.goto` |
| `veloutils.command.list` | `veloutils.network.list` |
| `veloutils.command.network` | `veloutils.network.status` |
| `veloutils.command.serverinfo` | `veloutils.network.serverinfo` |
| `veloutils.command.send` | `veloutils.network.send` |
| `veloutils.command.sendall` | `veloutils.network.sendall` |
| `veloutils.command.serverexecute` | `veloutils.network.execute` |
| `veloutils.maintenance.command` | `veloutils.maintenance.manage` |
| `veloutils.staff.list` | `veloutils.staff.list.view` |
| `veloutils.staff.time` | `veloutils.staff.time.view.self` and/or `.others` |
| `veloutils.staff.notify` | `veloutils.staff.activity.notify` |
| `veloutils.chat.staff` | `veloutils.chat.staff.use` and/or `.receive` |
| `veloutils.chat.admin` | `veloutils.chat.admin.use` and/or `.receive` |
| `veloutils.report.create` | `veloutils.reports.create` |
| `veloutils.report.manage` | The needed `veloutils.reports.view`, `.claim`, `.close`, and `.notify` capabilities |
| `veloutils.moderation.history` | `veloutils.moderation.history.view` and `veloutils.moderation.punishment.view` |
| `veloutils.moderation.checkban` | `veloutils.moderation.ban.view` |
| `veloutils.bridge.alert` | `veloutils.alert.broadcast` |

Unchanged moderation action nodes need no migration. Custom command permissions remain whatever you define in `commands.yml`.

## Moving from the older VelocityUtils plugin

Do not connect VeloUtils directly to an old VelocityUtils database. The layouts are different and automatic import is not provided.

| Legacy concept | Destination | Important change |
|---|---|---|
| Move/message commands | `commands.yml` | Explicit aliases, destinations, permissions, cooldowns, and MiniMessage |
| Maintenance allowlist | VeloUtils storage | Permanent player IDs are used. Unknown names are never guessed |
| Discord webhooks | `integrations.yml` | Official HTTPS Discord webhook URLs only |
| Database | `storage.yml` | New versioned schema |
| Plugin messaging | Proxy and bridge `config.yml` | New shared secret and protocol channel |

Verify bridge status, maintenance bypass, reports, offline history, mute enforcement, and non-admin server access on staging before rolling out to every backend.
