# Feature decisions

[Documentation](README.md) · [Implementation status](implementation-status.md) · [Migration map](migration-map.md)

> This project-history page is optional reading. Server owners should use [What currently works?](implementation-status.md) instead.

This matrix records what happened to features from the project before the rewrite and from projects used only as references. It explains product choices, not source-code history.

## Decision key

| Decision | Meaning |
|---|---|
| **Keep** | Preserve the useful behavior with a fresh implementation |
| **Redesign** | Preserve the goal with a safer architecture |
| **Replace** | Solve the same need in a different way |
| **Optional** | Make it a module that starts disabled |
| **Deprecate** | Support only for migration |
| **Remove** | Exclude for a documented technical reason |

## Network and integrations

| Reference behavior | Decision | VeloUtils direction |
|---|---|---|
| `/find`, `/goto`, `/vlist` | Redesign | Shared UI, explicit console behavior, actions, pagination, tab completion |
| Alerts | Redesign | Validated broadcasts and authenticated backend alerts |
| Built-in tablist/presentation | Replace | TAB owns visuals; VeloUtils registers optional network placeholders |
| Rotating alerts | Keep | Sequential or non-repeating random schedules with clear limits |
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
| Staff/admin chat | Replace | Explicit configured channels without unsafe chat replay |
| Reports and helpop | Redesign | Pages, filters, separate staff actions, cooldowns, and notices |
| Name/IP bans and kick | Replace | Offline UUID identity, expiry, keyed IP hashes, history/details/revocation UI |
| Mutes | Redesign | Authenticated state packets with Paper/Folia bridge chat enforcement |

## Bridge and integrations

| Reference behavior | Decision | VeloUtils direction |
|---|---|---|
| PlaceholderAPI | Redesign | Local snapshots pushed over the protocol |
| Backend `/vualert` | Keep | Authorized, size-limited alerts with matched delivery confirmation |
| Backend console execution | Redesign | Disabled by default, authenticated, dual allowlists |
| Discord webhooks | Optional | Asynchronous event delivery, limited retries, and hidden URLs |
| LimboAPI | Optional | Registered-server kick fallback compatible with a Limbo server |
| Update checking | Replace | Scheduled Modrinth provider and semantic version comparison |

## Platform and compatibility

| Reference behavior | Decision | VeloUtils direction |
|---|---|---|
| Static API provider | Replace | Plugin instance implements `VeloUtilsApi` |
| SQLite/MySQL storage | Redesign | Connection pooling, migrations, and prepared SQL, with PostgreSQL added |
| Old permissions | Deprecate | Old aliases remain enabled by default and warn once. New permissions are more specific |

Inactive items remain explicitly listed in [Implementation status](implementation-status.md).
