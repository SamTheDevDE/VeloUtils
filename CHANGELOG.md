# Changelog

This project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and Semantic Versioning.

## [Unreleased]

### Added

- Five-module Kotlin monorepo targeting Java 25.
- Stable service API and platform-neutral domain policies.
- Versioned JSON bridge protocol with HMAC, replay defense, negotiation, payload limits, and request correlation.
- Velocity network commands, health reporting, configurable commands, and cached MOTDs.
- Persistent maintenance, reports, moderation enforcement, server access, and staff sessions.
- Folia-safe Paper bridge with heartbeats, alerts, staff channels, remote commands, and optional PlaceholderAPI.
- Discord event webhooks with bounded asynchronous retries and redacted endpoints.
- Registered-server Limbo fallback, scheduled Modrinth update checks, and rotating network alerts.
- Authenticated mute-state synchronization with Paper/Folia chat enforcement.
- SQLite, MySQL/MariaDB, and PostgreSQL migrations on a dedicated dispatcher.
- Automated tests, artifact inspection, CI, release automation, CodeQL, and dependency review.
- Shared Adventure UI, permission-aware action buttons, and reusable configurable pagination.
- Persistent offline-player identity resolution with bounded name suggestions.
- Player-or-ID unban/unmute, full punishment details, and expiring self-punishment confirmation.
- Paginated report filters/details and clickable staff actions.
- Correlated staff-chat and network-alert delivery acknowledgements.
- Canonical capability permissions with documented, warning-once legacy aliases.

### Fixed

- Explicitly load packaged JDBC drivers so SQLite, MySQL, and PostgreSQL work with Velocity's isolated plugin classloader.
- Commit the Gradle wrapper JAR and update GitHub Actions to Node 24-compatible releases.
- Accept legacy `<reset>` message templates and ignore removed message keys during active-template validation.
- Preserve YAML comments and formatting by avoiding normal-startup rewrites; migrations now create backups.
- Remove ignored configuration fields and the obsolete `maintenance.yml` resource.
- Remove an undocumented extra per-destination permission check from configured move commands.

### Known limitations

- See [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) for features intentionally withheld or still represented only by extension boundaries.

[Unreleased]: https://github.com/SamTheDevDE/VeloUtils/commits/main
