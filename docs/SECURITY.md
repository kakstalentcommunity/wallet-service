# Security notes

## Threat model basics

The service protects user credentials, refresh sessions, wallet balances, payment requests, and administrative operations against common API threats: credential guessing, token theft or replay, account enumeration, request replay, concurrent overspending, forged or duplicated provider callbacks, unauthorized reversals, and accidental disclosure through logs.

Controls include BCrypt password hashing, opaque SHA-256-hashed rotating refresh tokens, short-lived issuer/audience-bound JWT access tokens with unique IDs, lockout after repeated failed logins, per-IP auth throttling, generic recipient-unavailable responses, role checks, request IDs, constrained CORS, JSON-only endpoints, security headers, idempotency fingerprints, UUID-ordered pessimistic account locks, a database wallet nonnegative constraint, immutable ledger entries, and security-relevant audit records.

Audit details reject keys that indicate passwords, tokens, secrets, phone numbers, credentials, or raw payloads. M-Pesa raw callbacks are retained in the JSONB request record for operational reconciliation; they are not written to application logs. Notification logs use masked phone numbers.

## Secrets and deployment

- Supply credentials through the process environment or a managed secret store. `.env` is for local development only and is git-ignored and excluded from Docker build context.
- The `prod` profile fails startup when `JWT_SECRET` is absent, short, or the development default. It also requires database values, Daraja credentials, an HTTPS callback origin, a long URL-safe callback token, and SMS provider credentials.
- Use independent, randomly generated JWT and callback secrets. Do not reuse passwords, commit credentials, add them to container images, or put them in audit details.
- Keep PostgreSQL and the Actuator management surface on trusted networks. `/actuator/health/**` and `/actuator/info` are public; other Actuator routes require ADMIN and remain unexposed by default.
- Terminate TLS at a trusted ingress, restrict access to the callback endpoint where possible, and rotate keys immediately after suspected exposure.
- Reconcile the cached account balances against the immutable ledger and alert on discrepancies. Never edit ledger rows to hide a mismatch.

## Deliberate limitations

- M-Pesa STK callbacks have no provider signature. The high-entropy callback path token and optional IP allow-list mitigate spoofing; they do not prove provider identity cryptographically.
- The IP rate limiter is in-memory and does not coordinate between instances. Deploy a shared limiter before horizontal scaling.
- The B2C client is a mock. Do not use this service to send real withdrawals until a provider-backed B2C adapter, callback verification, operational limits, and reconciliation procedures are implemented and reviewed.
- This repository does not implement MFA, device binding, customer support tooling, dispute handling, chargebacks, KYC/AML workflows, secrets-manager integration, or key rotation for existing JWTs. Production deployment needs separate controls and operational procedures for these areas.
- SMS is best-effort after commit. A provider outage does not roll back a payment; delivery monitoring and a durable outbox are needed if SMS delivery becomes a business requirement.
