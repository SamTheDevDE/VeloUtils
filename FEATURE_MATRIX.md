# Feature decisions

This matrix records how behavior from the reference projects was handled.

## Decision key

| Decision | Meaning |
|---|---|
| **Keep** | Preserve the useful behavior with a fresh implementation |
| **Redesign** | Preserve the goal with a safer architecture |
| **Replace** | Use a different model or workflow |
| **Optional** | Isolate behind a disabled-by-default module boundary |
| **Deprecate** | Support only for migration |
| **Remove** | Exclude for a documented technical reason |

## Network and presentation

| Reference behavior | Decision | VeloUtils direction |
|---|---|---|
| `/find`, `/goto`, `/vlist` | Redesign | Shared UI, explicit console behavior, actions, pagination, tab completion |
| Alerts and rich presentation | Redesign | Validated broadcasts and authenticated backend alerts |
| Rotating alerts | Keep | Bounded sequential or non-repeating random schedules |
| MOTD and favicon | Redesign | Cached rotation, virtual hosts, maintenance branding |
| Move commands | Redesign | Aliases, permissions, fallbacks, availability, cooldowns |
| Informational commands | Keep | Multi-line MiniMessage, click/hover, aliases, cooldowns |
| Client brand rewriting | Remove | Required Velocity internals for cosmetic-only behavior |

## Access, staff, and moderation

| Reference behavior | Decision | VeloUtils direction |
|---|---|---|
| Global maintenance | Redesign | Persistent global/server state and UUID allowlist |
| Server whitelist | Replace | Permission and UUID access rules with checked fallback |
| Staff list/rank | Redesign | `StaffService` and platform permissions |
| Staff time | Redesign | UUID sessions and asynchronous range queries |
| Staff activity notices | Keep | Optional notification channel |
| Staff/admin chat | Replace | Explicit configured channels; no unsafe chat replay |
| Reports and helpop | Redesign | Paginated/filterable lifecycle UI, granular actions, cooldowns, notices |
| Name/IP bans and kick | Replace | Offline UUID identity, expiry, keyed IP hashes, history/details/revocation UI |
| Mutes | Redesign | Authenticated state packets with Paper/Folia bridge chat enforcement |

## Bridge and integrations

| Reference behavior | Decision | VeloUtils direction |
|---|---|---|
| PlaceholderAPI | Redesign | Local snapshots pushed over the protocol |
| Backend `/vualert` | Keep | Authorized, bounded alert packets with correlated delivery acknowledgement |
| Backend console execution | Redesign | Disabled by default, authenticated, dual allowlists |
| Discord webhooks | Optional | Typed asynchronous event sinks, bounded retries, and URL redaction |
| LimboAPI | Optional | Registered-server kick fallback compatible with a Limbo server |
| Update checking | Replace | Scheduled Modrinth provider and semantic version comparison |

## Platform and compatibility

| Reference behavior | Decision | VeloUtils direction |
|---|---|---|
| Static API provider | Replace | Plugin instance implements `VeloUtilsApi` |
| SQLite/MySQL storage | Redesign | Pooling, migrations, prepared SQL; PostgreSQL added |
| Old permissions | Deprecate | Enabled-by-default, warning-once aliases mapped to canonical capabilities |

Inactive items remain explicitly listed in [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md).
