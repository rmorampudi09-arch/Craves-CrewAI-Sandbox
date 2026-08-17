CREATE TABLE IF NOT EXISTS order_schema.notification_outbox (
    id UUID PRIMARY KEY,
    event_key VARCHAR(180) NOT NULL UNIQUE,
    event_type VARCHAR(80) NOT NULL,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id UUID NOT NULL,
    user_identity_id UUID NOT NULL,
    user_role VARCHAR(40) NOT NULL,
    channel VARCHAR(30) NOT NULL DEFAULT 'IN_APP',
    template_code VARCHAR(120),
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    target_type VARCHAR(40),
    target_id UUID,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    CONSTRAINT chk_notification_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED')),
    CONSTRAINT chk_notification_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_notification_outbox_pending
    ON order_schema.notification_outbox (status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX IF NOT EXISTS idx_notification_outbox_aggregate
    ON order_schema.notification_outbox (aggregate_type, aggregate_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_outbox_user
    ON order_schema.notification_outbox (user_identity_id, created_at DESC);
