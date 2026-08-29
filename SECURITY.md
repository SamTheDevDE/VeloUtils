# Security policy

This page has two purposes: it explains how to report a security problem and lists settings server owners should check before opening a network to players. Normal bugs can use the public issue tracker.

## Supported versions

| Version | Support |
|---|---|
| Latest release line | Security fixes |
| Development snapshots | Best effort and not production-stable |
| Older release lines | Unsupported unless announced otherwise |

## Report a vulnerability

Do not open a public issue containing exploit details.

1. Use GitHub's private vulnerability reporting feature.
2. If it is unavailable, contact the maintainer privately through the address on their GitHub profile.
3. Include affected versions, prerequisites, impact, and a minimal reproduction.
4. Remove production secrets, databases, and raw player addresses from the report.

An acknowledgement should arrive within seven days. The maintainer will confirm scope, coordinate a fix and release where necessary, and agree on disclosure timing when practical. No bounty or fixed resolution deadline is promised.

## Checklist for server owners

| Do this | Why it matters |
|---|---|
| Require bridge authentication and use the same private secret of at least 32 characters everywhere | Stops unauthenticated plugin messages from being trusted |
| Use a different private key for moderation IP hashing | Prevents stored IP-ban identifiers from being reused as ordinary addresses |
| Firewall backend ports and enable Velocity secure forwarding | Stops players from bypassing the proxy |
| Give the database user only the permissions VeloUtils needs | Reduces damage if those credentials are exposed |
| Keep remote commands disabled unless you genuinely need them | Remote console access is powerful and increases risk |
| If remote commands are enabled, allow only exact trusted command names | Limits what a compromised backend can request |
| Never upload populated config files | They may contain database passwords, bridge secrets, hash keys, or Discord webhooks |
| Treat Discord webhook URLs like passwords | Anyone with the URL can post through that webhook |
| Keep Java, Velocity, Paper/Folia, and VeloUtils updated | Security fixes arrive through updates |
| Back up config and databases before updating | Gives you a safe recovery point |

For a combined network, bridge authentication being disabled should be treated as an unsafe temporary setup, not a production configuration.

For implementation details and residual risks, read the [security audit](docs/security-audit.md).
