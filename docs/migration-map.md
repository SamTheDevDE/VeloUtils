# Architecture migration map

[Documentation](README.md) · [Migration guide](migration.md) · [Feature decisions](feature-decisions.md)

> This page records the rewrite for contributors. Server owners updating the plugin should use the [Migration guide](migration.md).

This map records what happened to features from before the modular rewrite. "Retained" means the feature is still available, but it may have a completely new implementation.

| Feature before the rewrite | New owner | What changed |
|---|---|---|
| `/find`, `/goto`, player/server lists, send commands | Velocity network module | Rewritten and retained with UUID-aware handling, pagination, and permission-aware transfers |
| Configurable lobby/server and information commands | Velocity network module | Rewritten and retained with explicit aliases, cooldowns, and MiniMessage |
| Rotating/host-specific MOTD, sample, favicon | Velocity MOTD module | Rewritten and retained with cached parsed components and ping-path selection |
| Global maintenance and allowlist | Maintenance module | Rewritten and retained as persistent global/per-server UUID state |
| Per-server whitelist/access | Server-access module | Rewritten as permission/UUID policy with authorization-checked fallbacks |
| Staff list, join/leave notices, play/server time | Staff module | Rewritten and retained with UUID sessions and database queries that return a limited number of records |
| Staff/admin chat | Communication module | Retained as authenticated `/sc` and `/ac` channels. Transparent chat interception was removed |
| Alerts and rotating broadcasts | Announcements module | Rewritten and retained on Velocity. Optional local Paper/Folia rotation was added |
| Reports and helpop | Moderation reports/helpop domain | Rewritten and retained with open/claimed/closed lifecycle, assignment, filters, and pagination |
| Ban, IP ban, kick, warn, history | Moderation punishments/history domain | Rewritten and retained with UUID identity, keyed IP hashes, audit detail, expiry, and revocation |
| Mutes | Moderation plus backend enforcement | Rewritten. Authenticated state packets replace unsafe chat replay |
| Maintenance/report/staff SQL | Shared storage plus domain repositories | Rewritten with pooling, prepared statements, migrations, indexes, transactions, and async dispatch |
| Proxy/backend plugin messages | `veloutils-protocol` | Replaced by size-limited versioned JSON, HMAC, timestamps, replay protection, and matched requests/responses |
| PlaceholderAPI integration | Placeholder service plus Paper expansion | Rewritten and retained. Cached values replace per-render network requests |
| Discord notifications | Discord integration | Rewritten, optional, asynchronous, limited to a few retries, and hidden from logs |
| Limbo fallback | Limbo integration | Retained as an optional adapter to an already registered server |
| Update checks | Modrinth integration | Replaced with an asynchronous semantic-version provider |
| Backend remote console | Protocol compatibility feature | Retained disabled by default with authentication and dual command-root allowlists |
| Permission aliases | Compatibility layer | Kept temporarily with one-time warnings and newer, more specific replacements |
| Static/global API provider | `veloutils-api` platform publication | Replaced by the Velocity plugin instance and Bukkit service registry |
| Backend Bukkit scheduler assumptions | Platform scheduler abstraction | Replaced with global, entity, and async Paper/Folia-safe dispatch |
| AFK | Optional Paper/Folia AFK module | Added and disabled by default. It has no economy, rewards, or gameplay mechanics |
| Client brand rewriting and Velocity internals | None | Removed because the cosmetic feature required fragile internal code |
| Plaintext IP persistence | None | Removed. IP targets use protected hashes |
| Unversioned channels and Java serialization | None | Removed for security and compatibility |
| Tebex/economy-like integration placeholders | Separate addons if ever justified | Removed from core |

Chat, private messaging, and presentation did not have working features to migrate. They now have new opt-in implementations. The [implementation status](implementation-status.md) clearly lists what they support and what they do not.
