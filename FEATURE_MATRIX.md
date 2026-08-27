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
| `/find`, `/goto`, `/vlist` | Redesign | Async results, explicit console behavior, tab completion |
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
| Reports and helpop | Redesign | Persistent IDs, claim/close lifecycle, history, cooldowns |
| Name/IP bans and kick | Replace | UUID records, expiry, scopes, keyed IP hashes, audit fields |
| Mutes | Redesign | Authenticated state packets with Paper/Folia bridge chat enforcement |

## Bridge and integrations

| Reference behavior | Decision | VeloUtils direction |
|---|---|---|
| PlaceholderAPI | Redesign | Local snapshots pushed over the protocol |
| Backend `/vualert` | Keep | Authorized, bounded alert packets |
| Backend console execution | Redesign | Disabled by default, authenticated, dual allowlists |
| Sound forwarding | Redesign | Typed packets and entity scheduling |
| Discord webhooks | Optional | Typed asynchronous event sinks, bounded retries, and URL redaction |
| Tebex | Optional | Cached, rate-limited service boundary |
| LimboAPI | Optional | Registered-server kick fallback compatible with a Limbo server |
| Update checking | Replace | Scheduled Modrinth provider and semantic version comparison |

## Platform and compatibility

| Reference behavior | Decision | VeloUtils direction |
|---|---|---|
| Static API provider | Replace | Plugin instance implements `VeloUtilsApi` |
| SQLite/MySQL storage | Redesign | Pooling, migrations, prepared SQL; PostgreSQL added |
| Old permissions | Deprecate | Disabled migration aliases using `veloutils.*` guidance |

Inactive items remain explicitly listed in [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md).
