ALTER TABLE transactions ADD COLUMN amount_minor BIGINT;
ALTER TABLE transactions ADD COLUMN fee_minor BIGINT;
ALTER TABLE transactions ADD COLUMN idempotency_key VARCHAR(80);
ALTER TABLE transactions ADD COLUMN request_hash CHAR(64);
ALTER TABLE transactions ADD CONSTRAINT transactions_amount_nonnegative_check CHECK (amount_minor IS NULL OR amount_minor > 0);
ALTER TABLE transactions ADD CONSTRAINT transactions_fee_nonnegative_check CHECK (fee_minor IS NULL OR fee_minor >= 0);
ALTER TABLE transactions ADD CONSTRAINT transactions_idempotency_pair_check CHECK ((idempotency_key IS NULL) = (request_hash IS NULL));
ALTER TABLE transactions ADD CONSTRAINT transactions_idempotency_actor_check CHECK (idempotency_key IS NULL OR actor_user_id IS NOT NULL);
CREATE UNIQUE INDEX transactions_actor_idempotency_key_uq ON transactions(actor_user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX transactions_user_transfer_day_idx ON transactions(actor_user_id, created_at)
    WHERE type = 'TRANSFER' AND status = 'COMPLETED';
