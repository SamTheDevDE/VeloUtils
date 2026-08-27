# Migration guide

VeloUtils migrations are non-destructive where possible. Normal startup reads existing YAML without saving it. A versioned file migration creates `<file>.pre-migration.bak` before changing the original.

## Upgrading a VeloUtils beta

1. Back up `plugins/VeloUtils`, `plugins/VeloUtilsBridge`, and the database.
2. Replace both plugin JARs together; protocol version 2 adds staff-chat and alert acknowledgements.
3. Start a test proxy/backend pair.
4. Run `/veloutils config validate` and `/veloutils config diff`.
5. Migrate permissions using the table below.
6. Restart after config changes. `/veloutils reload` hot-reloads only `messages.yml`.

`maintenance.yml` was removed because it contained no authoritative runtime settings. Maintenance state remains in storage; presentation uses `messages.yml`, and server fallbacks use `config.yml`.

Removed settings are not silently simulated:

- The built-in `permissions:` map and move-command `fallback:` field in `commands.yml` were unused and are gone.
- Ignored SQLite path/timeout settings are gone; SQLite is always `plugins/VeloUtils/data.db`.
- The unused `staff.week-start` and ineffective `store-ip-hashes` toggles are gone; IP bans always use the required keyed hash.
- Unused legacy message keys were removed; active configurable templates remain in `messages.yml`.
- The unused top-level `debug` toggle was removed; `/veloutils debug` remains a permission-controlled redacted diagnostics command.
- Unused bridge `server-name` and `placeholder-cache-seconds` fields were removed; `heartbeat` is now validated and controls the schedule.
- Unimplemented LuckPerms/Tebex integration settings are gone.
- The former `modules.tebex` flag is gone.

## Permission mappings

Legacy checks are enabled by default:

```yaml
compatibility:
  legacy-permissions:
    enabled: true
    warn: true
```

Apply the same compatibility block to the bridge config. Each old alias logs at most one warning per process. Disable compatibility only after migrating every group.

| Legacy node | Canonical replacement |
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

## Migrating from VelocityUtils

Do not point VeloUtils at an unverified legacy database.

| Legacy concept | Destination | Important change |
|---|---|---|
| Move/message commands | `commands.yml` | Explicit aliases, destinations, permissions, cooldowns, and MiniMessage |
| Maintenance allowlist | VeloUtils storage | UUID identities; unresolved names are never guessed |
| Discord webhooks | `integrations.yml` | Official HTTPS Discord webhook URLs only |
| Database | `storage.yml` | New versioned schema |
| Plugin messaging | Proxy and bridge `config.yml` | New shared secret and protocol channel |

Verify bridge status, maintenance bypass, reports, offline history, mute enforcement, and non-admin server access on staging before rolling out to every backend.
