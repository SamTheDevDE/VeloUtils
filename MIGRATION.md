# Migrating from VelocityUtils

Migration is manual and non-destructive. VeloUtils does not modify the old plugin folder or database.

## Before migrating

1. Back up both legacy plugin folders.
2. Back up every legacy database.
3. Install VeloUtils into a new folder.
4. Start it once to generate clean configuration files.
5. Stop the network before copying settings.

## Map legacy settings

| Legacy concept | VeloUtils destination | Important change |
|---|---|---|
| `movecommands` | `commands.yml` | Add explicit aliases, permissions, fallbacks, and cooldowns |
| `messagescommands` | `commands.yml` | Convert text to MiniMessage and review URLs/click actions |
| Maintenance allowlist | Maintenance storage/config | Resolve names to UUIDs; never guess unresolved players |
| Discord webhooks | `integrations.yml` | Never paste secrets into logs or issues |
| Database settings | `storage.yml` | Use a new VeloUtils schema |
| Staff/report/helpop permissions | Canonical `veloutils.*` permissions | Legacy aliases are disabled by default |
| Plugin-message setup | Proxy and bridge `config.yml` | Create a new shared secret; old channels are incompatible |

## Validate the migration

- Start a test proxy and one backend first.
- Confirm the bridge handshake in `/veloutils status`.
- Test maintenance bypass and server fallback with non-admin accounts.
- Test report persistence across restart.
- Confirm no credentials or raw addresses appear in diagnostics.
- Add remaining backends only after the first pair works.

Do not point VeloUtils at an old SQLite file without a verified backup. Unknown legacy fields should be reviewed manually rather than copied wholesale.
