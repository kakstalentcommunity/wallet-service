ALTER TABLE accounts ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE accounts ADD COLUMN account_type VARCHAR(24) NOT NULL DEFAULT 'USER';
ALTER TABLE accounts ADD COLUMN code VARCHAR(40) UNIQUE;
ALTER TABLE accounts ADD CONSTRAINT accounts_type_check CHECK (account_type IN ('USER', 'FEE_REVENUE', 'MPESA_CLEARING', 'SUSPENSE'));
ALTER TABLE accounts DROP CONSTRAINT accounts_balance_minor_check;
ALTER TABLE accounts ADD CONSTRAINT accounts_wallet_nonnegative_check CHECK (balance_minor >= 0 OR account_type <> 'USER');

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    reference_code VARCHAR(32) NOT NULL UNIQUE,
    type VARCHAR(24) NOT NULL CHECK (type IN ('TRANSFER', 'DEPOSIT', 'WITHDRAWAL', 'REVERSAL', 'ADJUSTMENT')),
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REVERSED')),
    currency CHAR(3) NOT NULL,
    actor_user_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX transactions_created_at_idx ON transactions(created_at DESC);
CREATE INDEX transactions_actor_created_idx ON transactions(actor_user_id, created_at DESC);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    direction VARCHAR(6) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ledger_entries_account_created_idx ON ledger_entries(account_id, created_at DESC);
CREATE INDEX ledger_entries_transaction_idx ON ledger_entries(transaction_id);

INSERT INTO accounts (id, user_id, balance_minor, currency, active, account_type, code)
VALUES
 ('00000000-0000-0000-0000-000000000001', NULL, 0, 'KES', TRUE, 'FEE_REVENUE', 'FEE_REVENUE'),
 ('00000000-0000-0000-0000-000000000002', NULL, 0, 'KES', TRUE, 'MPESA_CLEARING', 'MPESA_CLEARING'),
 ('00000000-0000-0000-0000-000000000003', NULL, 0, 'KES', TRUE, 'SUSPENSE', 'SUSPENSE');

CREATE FUNCTION reject_ledger_entry_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries are append-only' USING ERRCODE = '55000';
END;
$$;
CREATE TRIGGER ledger_entries_immutable
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_entry_mutation();
