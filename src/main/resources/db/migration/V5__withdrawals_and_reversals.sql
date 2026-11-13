ALTER TABLE transactions ADD COLUMN reversal_of_id UUID REFERENCES transactions(id);
CREATE UNIQUE INDEX transactions_single_reversal_uq ON transactions(reversal_of_id) WHERE reversal_of_id IS NOT NULL;
CREATE TABLE withdrawal_requests (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL UNIQUE REFERENCES transactions(id),
    phone VARCHAR(13) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    provider_reference VARCHAR(80),
    initiated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX withdrawal_pending_created_idx ON withdrawal_requests(initiated_at);
