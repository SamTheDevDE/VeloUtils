# Changelog

This project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and Semantic Versioning.

## [Unreleased]

### Added

- Six-module Kotlin monorepo targeting Java 25.
- Stable service API and platform-neutral domain policies.
- Versioned JSON bridge protocol with HMAC, replay defense, negotiation, payload limits, and request correlation.
- Velocity network commands, health reporting, configurable commands, and cached MOTDs.
- Persistent maintenance, reports, moderation enforcement, server access, and staff sessions.
- Folia-safe Paper bridge with heartbeats, alerts, staff channels, remote commands, and optional PlaceholderAPI.
- Discord event webhooks with a limited number of asynchronous retries and hidden endpoint details.
- Registered-server Limbo fallback, scheduled Modrinth update checks, and rotating network alerts.
- Authenticated mute-state synchronization with Paper/Folia chat enforcement.
- SQLite, MySQL/MariaDB, and PostgreSQL migrations on a dedicated dispatcher.
- Automated tests, artifact inspection, CI, release automation, CodeQL, and dependency review.
- Shared Adventure UI, permission-aware action buttons, and reusable configurable pagination.
- Persistent offline-player identity lookup with a limited number of name suggestions.
- Player-or-ID unban/unmute, full punishment details, and expiring self-punishment confirmation.
- Paginated report filters/details and clickable staff actions.
- Correlated staff-chat and network-alert delivery acknowledgements.
- Specific feature permissions with documented old aliases that warn once.
- Dependency-aware module startup, rollback, selectors, and safe size-limited placeholder rendering in `veloutils-core`.
- Disabled-by-default standalone Paper/Folia AFK and local announcement modules.
- Bukkit service publication for the API and explicit nullable optional services.
- Predictable `VeloUtils-Velocity-<version>.jar` and `VeloUtils-Paper-<version>.jar` artifacts.
- Module, configuration, API, and addon development guides.
- Persistent maintenance scheduling, countdown announcements, automatic expiry, and restart restoration.
- Standalone Paper/Folia chat policy with safe Adventure rendering, mute/clear, cooldown, duplicate, caps, and optional filters.
- Local and protocol-v4 acknowledged cross-server private messaging with reply, persistent UUID ignores, ignore synchronization, and social spy commands.
- Selected animated TAB designs, scoreboards, nametags, and configured or temporary bossbars using the shared placeholder renderer.
- PlaceholderAPI-backed presentation values and LuckPerms primary-group selection through the installed PlaceholderAPI expansion.
- Configured backend `server-id` for correct network placeholders and per-server presentation selectors.
- Target-backend delivery acknowledgements and SQL-backed, backend-synchronized UUID ignore relationships.
- Server, radius, and network chat channels with selection, local addon channels, mentions/sounds, and safe clickable URLs.
- Incremental duplicate-safe scoreboards and per-viewer nametag teams with selector, group, permission, visibility, collision, and sorting support.
- Multiline TAB layouts, supported player-list ordering, and scheduled/targeted temporary bossbars through the public API.
- Optional one-shot pre-maintenance transfer with bypass exclusion and normal server-access authorization.
- A centralized `docs/` site with a plain-language installation guide, server-owner module/configuration references, and clearly separated developer and audit documentation.
- Strict, cached MiniMessage rendering for MOTDs and legacy-formatted server-list sample players with dynamic placeholders.
- Permission-controlled player chat colors, decorations, and gradients using a presentation-only tag allowlist.

### Fixed

- Explicitly load packaged JDBC drivers so SQLite, MySQL, and PostgreSQL work with Velocity's isolated plugin classloader.
- Commit the Gradle wrapper JAR and update GitHub Actions to Node 24-compatible releases.
- Accept legacy `<reset>` message templates and ignore removed message keys during active-template validation.
- Preserve YAML comments and formatting by avoiding normal-startup rewrites. Migrations now create backups.
- Remove ignored configuration fields and the obsolete `maintenance.yml` resource.
- Remove an undocumented extra per-destination permission check from configured move commands.
- Fall back from missing disk configuration/message keys to bundled defaults without rewriting administrator YAML.
- Return direct safe components for unknown message keys and log each missing key only once.
- Validate MOTD and sample-player MiniMessage paths during startup/reload instead of failing on a proxy ping.

### Known limitations

- See the [implementation status](docs/implementation-status.md) for features that are limited or not included.

[Unreleased]: https://github.com/SamTheDevDE/VeloUtils/commits/main
