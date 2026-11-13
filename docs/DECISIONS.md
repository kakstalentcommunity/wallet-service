# Phase 1 decisions

- Kenyan subscriber numbers are stored in E.164 form (`+2547...` or `+2541...`); accepted local forms are normalized before persistence.
- Passwords use BCrypt with cost 12 and are limited to 8–72 characters, matching BCrypt's byte input bound for ordinary ASCII passwords.
- A SHA-256 pre-hash with a version prefix feeds BCrypt so the 8–72 character policy works for UTF-8 passwords without BCrypt's 72-byte truncation behavior.
- Failed login attempts commit their attempt counter and 15-minute lockout even though the request returns an authentication error.
- Access tokens last 15 minutes. Refresh tokens are random opaque values; only their SHA-256 hashes are stored. Reuse of a revoked refresh token revokes the user's refresh-token set.
- Unknown-phone login runs the same BCrypt verification against a fixed dummy hash to reduce account enumeration by timing.
- Authentication throttling uses an in-memory limiter behind `IpRateLimiter`; it is process-local and should be replaced with a shared implementation when running multiple app instances.
- Ledger entries are immutable, and account cache changes are posted in the same transaction after locking every involved account in UUID order.
- Ledger balances use signed `CREDIT - DEBIT` totals for reconciliation. User wallet accounts have a database nonnegative check; system accounts may carry negative signed totals because they balance user liabilities against external or revenue accounts.
- Reconciliation reports differences without repairing them. Automatic repair would hide the cause of ledger/cache drift and requires an audited operator workflow.
- Transfer idempotency keys are unique per acting user. Retries compare a SHA-256 fingerprint of normalized recipient, amount, and currency; the stored receipt is returned for an identical replay.
- The configured daily transfer limit counts transfer principal, excluding the fee. Fees are tiered as flat minor units plus optional basis points and are credited to `FEE_REVENUE`.
- Daraja callbacks are accepted only on a configured high-entropy path token, with an optional exact-IP allow-list. Callback content is treated as untrusted and must match the pending checkout ID, requested amount, and normalized phone before settlement.
- STK push is not automatically retried because a timeout can occur after Daraja accepts the request. OAuth and status-query calls use bounded retries; unresolved requests remain pending for polling and expire after ten minutes.
- Withdrawals reserve the requested amount by debiting the wallet and crediting `SUSPENSE`. The B2C client is an asynchronous local mock; a failure posts opposite entries before marking the original withdrawal failed.
- Reversals never mutate ledger rows. A completed transaction is reversed by posting a new transaction with opposite entries, then transitioning the source transaction to `REVERSED`.
- Fraud rules block excessive transfer velocity and new-recipient velocity, flag unusually large transfers and repeated login failures, and record only reason codes and transaction identifiers in audit details.
- Payment SMS events are delivered after commit. A listener retries three times and isolates failures; the development implementation logs only a masked number, while production uses Africa's Talking credentials supplied by the environment.
