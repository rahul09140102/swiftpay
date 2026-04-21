-- V1: Ledger Service schema

CREATE TABLE IF NOT EXISTS accounts (
    id         UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID           NOT NULL UNIQUE,
    balance    NUMERIC(19,4)  NOT NULL DEFAULT 0 CHECK (balance >= 0),
    currency   CHAR(3)        NOT NULL DEFAULT 'USD',
    version    BIGINT         NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_accounts_user_id ON accounts (user_id);

CREATE TABLE IF NOT EXISTS ledger_entries (
    id             UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id VARCHAR(255)   NOT NULL,
    payment_id     UUID           NOT NULL,
    account_id     UUID           NOT NULL REFERENCES accounts(id),
    user_id        UUID           NOT NULL,
    amount         NUMERIC(19,4)  NOT NULL CHECK (amount > 0),
    entry_type     VARCHAR(10)    NOT NULL CHECK (entry_type IN ('DEBIT','CREDIT')),
    balance_before NUMERIC(19,4)  NOT NULL,
    balance_after  NUMERIC(19,4)  NOT NULL,
    currency       CHAR(3)        NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ledger_user_id        ON ledger_entries (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ledger_transaction_id ON ledger_entries (transaction_id);
CREATE INDEX IF NOT EXISTS idx_ledger_account_id     ON ledger_entries (account_id);
