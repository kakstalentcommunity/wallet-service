CREATE TABLE mpesa_deposit_requests (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL UNIQUE REFERENCES transactions(id),
    checkout_request_id VARCHAR(80) UNIQUE,
    merchant_request_id VARCHAR(80),
    phone VARCHAR(13) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    initiated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_status_checked_at TIMESTAMPTZ,
    raw_callback JSONB
);
CREATE INDEX mpesa_pending_created_idx ON mpesa_deposit_requests(initiated_at)
    WHERE checkout_request_id IS NOT NULL;
