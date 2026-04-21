CREATE TABLE IF NOT EXISTS payment_analytics (
    id                    UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id            UUID           NOT NULL UNIQUE,
    transaction_id        VARCHAR(255)   NOT NULL,
    sender_id             UUID           NOT NULL,
    receiver_id           UUID           NOT NULL,
    amount                NUMERIC(19,4)  NOT NULL,
    currency              CHAR(3)        NOT NULL,
    sender_balance_after  NUMERIC(19,4),
    receiver_balance_after NUMERIC(19,4),
    completed_at          TIMESTAMPTZ,
    ingested_at           TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_analytics_sender     ON payment_analytics (sender_id);
CREATE INDEX IF NOT EXISTS idx_analytics_receiver   ON payment_analytics (receiver_id);
CREATE INDEX IF NOT EXISTS idx_analytics_completed  ON payment_analytics (completed_at DESC);
CREATE INDEX IF NOT EXISTS idx_analytics_currency   ON payment_analytics (currency);
CREATE INDEX IF NOT EXISTS idx_analytics_ingested   ON payment_analytics (ingested_at DESC);
