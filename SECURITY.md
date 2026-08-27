# Security policy

## Supported versions

| Version | Support |
|---|---|
| Latest release line | Security fixes |
| Development snapshots | Best effort; not production-stable |
| Older release lines | Unsupported unless announced otherwise |

## Report a vulnerability

Do not open a public issue containing exploit details.

1. Use GitHub's private vulnerability reporting feature.
2. If it is unavailable, contact the maintainer privately through the address on their GitHub profile.
3. Include affected versions, prerequisites, impact, and a minimal reproduction.
4. Remove production secrets, databases, and raw player addresses from the report.

An acknowledgement should arrive within seven days. Coordinated disclosure is preferred; no bounty is promised.

## Production checklist

- Require protocol authentication on proxy and bridge.
- Use the same unique 32-byte-or-longer secret on both sides.
- Use a different key for moderation IP hashing.
- Firewall backend servers and enable secure player forwarding.
- Use least-privilege database credentials and TLS where available.
- Keep remote commands disabled unless required.
- Allowlist only the exact remote command roots needed.
- Never publish generated configuration containing credentials.
- Treat Discord webhook URLs as secrets and rotate any URL that appears in logs or issue reports.
- Keep Velocity, Paper/Folia, Java, and VeloUtils updated.

For implementation details and residual risks, read [SECURITY_AUDIT.md](SECURITY_AUDIT.md).
