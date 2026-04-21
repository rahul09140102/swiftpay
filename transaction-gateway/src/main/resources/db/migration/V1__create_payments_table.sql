-- V1: Create payments table for Transaction Gateway

CREATE TABLE IF NOT EXISTS payments (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id VARCHAR(255) NOT NULL UNIQUE,
    sender_id      UUID         NOT NULL,
    receiver_id    UUID         NOT NULL,
    amount         NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency       CHAR(3)      NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    failure_reason VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_payments_sender_id     ON payments (sender_id);
CREATE INDEX IF NOT EXISTS idx_payments_receiver_id   ON payments (receiver_id);
CREATE INDEX IF NOT EXISTS idx_payments_status        ON payments (status);
CREATE INDEX IF NOT EXISTS idx_payments_created_at    ON payments (created_at DESC);
