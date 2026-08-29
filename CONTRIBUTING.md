# Contributing

Thanks for helping improve VeloUtils.

Contributions should preserve the project's central promise: modular features, predictable runtime cost, and genuine Paper/Folia safety. Read the [architecture](docs/architecture.md) and [module lifecycle](docs/modules.md) before changing a platform boundary.

## Before you start

- Open an issue before making a large behavioral or architectural change.
- Report vulnerabilities privately using [SECURITY.md](SECURITY.md).
- Use JDK 25 and the checked-in Gradle wrapper.

## Local workflow

1. Create a focused branch.
2. Make the smallest coherent change.
3. Add or update tests.
4. Run the complete quality gate:

   ```bash
   ./gradlew clean qualityGate
   ```

5. Describe behavior, compatibility, configuration, and migration impact in the pull request.

## Where code belongs

| Module | Put this here |
|---|---|
| `veloutils-api` | Public contracts and models |
| `veloutils-common` | Platform-neutral rules |
| `veloutils-core` | Lifecycle, conditions, placeholders, and rendering infrastructure |
| `veloutils-protocol` | Shared wire types and validation |
| `veloutils-proxy` | Velocity behavior |
| `veloutils-bridge` | Paper/Folia-only behavior |

## Review checklist

- Production code is Kotlin only.
- Blocking I/O runs on an owned asynchronous dispatcher.
- User input, protocol payloads, and collections have explicit bounds.
- Secrets and raw addresses never appear in logs or diagnostics.
- Folia entity and global operations use the correct scheduler.
- Bug fixes include a regression test where practical.
- New behavior is reflected in the README, changelog, or status page.
- Documentation links remain relative and resolve from their containing file.

By contributing, you confirm that you can submit the work under GPL-3.0-only and agree to the [Code of Conduct](CODE_OF_CONDUCT.md).
