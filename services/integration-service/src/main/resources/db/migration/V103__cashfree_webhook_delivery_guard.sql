CREATE TABLE payment_schema.cashfree_webhook_delivery (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    webhook_version VARCHAR(40) NOT NULL,
    webhook_timestamp BIGINT NOT NULL,
    webhook_signature VARCHAR(512) NOT NULL,
    raw_payload TEXT NOT NULL,
    processing_status VARCHAR(20) NOT NULL,
    lock_token UUID,
    processing_started_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    CONSTRAINT chk_cashfree_webhook_delivery_status CHECK (
        processing_status IN ('RECEIVED', 'PROCESSING', 'COMPLETED', 'FAILED', 'DEAD_LETTER')
    )
);

CREATE INDEX ix_cashfree_webhook_delivery_claim
    ON payment_schema.cashfree_webhook_delivery (processing_status, next_attempt_at, first_seen_at);
