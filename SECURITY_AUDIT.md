# Security audit

## Trust boundaries

| Boundary | Protection |
|---|---|
| Backend → proxy | Size, JSON, version, timestamp, nonce, signature, request ID, packet type, and source validation |
| Proxy → backend mute state | Accepted only when protocol authentication is required on the bridge |
| Remote commands | Disabled by default; authenticated transport and allowlists on both ends |
| Proxy → external services | Discord destinations are restricted to official HTTPS webhook paths; update checks use a fixed Modrinth endpoint |
| Player text → MiniMessage | Length validation and literal `Component` insertion |
| Application → database | Prepared statements and dedicated asynchronous dispatcher |
| Player address → storage | Keyed HMAC; raw addresses are not stored in punishment records |
| Diagnostics → administrator | Passwords, webhooks, API keys, shared secrets, and addresses are redacted |

Plugin-message authentication does not replace host security. Backend servers still require firewalling and Velocity secure forwarding.

## Abuse resistance

- Protocol payloads and input fields have explicit maximum lengths.
- Replay and request-tracking maps are bounded and expire entries.
- Requests use correlation IDs and timeouts.
- PlaceholderAPI reads a local snapshot instead of issuing live requests.
- MiniMessage templates are parsed from trusted configuration; user input stays literal.
- Remote command roots are checked independently by proxy and backend.
- Discord requests disable redirects, suppress mentions, bound content, and use limited retries.
- Update responses have a size limit and are parsed only for release versions.

## Threading and lifecycle

- Velocity file and JDBC work runs off critical event threads.
- Folia entity actions use entity schedulers.
- Folia global state uses the global region scheduler.
- Backend blocking work uses the async scheduler.
- Executors, coroutine scopes, protocol trackers, and storage pools close during shutdown.

## Residual risks

| Risk | Required mitigation |
|---|---|
| Authentication is disabled in first-run defaults | Configure a strong secret and require authentication before production |
| A compromised backend knows the network secret | Use separate secrets per network and strict command allowlists |
| Database or webhook credentials are stolen | Use least privilege, TLS, secret rotation, and host controls |
| Dependency vulnerability | Keep dependencies updated and review CI security findings |
| Optional feature appears configured but is inactive | Check [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) before deployment |

CI provides CodeQL and dependency review, but deployment security remains the operator's responsibility.
