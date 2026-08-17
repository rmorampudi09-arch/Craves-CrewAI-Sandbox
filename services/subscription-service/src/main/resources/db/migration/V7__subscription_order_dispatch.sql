ALTER TABLE subscription_schema.subscription_occurrence
    ADD COLUMN order_id UUID,
    ADD COLUMN order_requested_at TIMESTAMPTZ,
    ADD COLUMN order_created_at TIMESTAMPTZ,
    ADD COLUMN order_dispatch_lock_token UUID,
    ADD COLUMN order_dispatch_locked_at TIMESTAMPTZ;

CREATE UNIQUE INDEX ux_subscription_occurrence_order
    ON subscription_schema.subscription_occurrence (order_id)
    WHERE order_id IS NOT NULL;

CREATE INDEX ix_subscription_occurrence_order_due
    ON subscription_schema.subscription_occurrence (status, service_at, order_dispatch_locked_at)
    WHERE status = 'READY_FOR_ORDER';

CREATE TABLE subscription_schema.subscription_order_request_outbox (
    id UUID PRIMARY KEY,
    event_key VARCHAR(200) NOT NULL UNIQUE,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    event_version VARCHAR(20) NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID,
    subject UUID NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lock_token UUID,
    locked_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    broker_message_id VARCHAR(180),
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_subscription_order_request_outbox_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'DEAD_LETTER')
    )
);

CREATE INDEX ix_subscription_order_request_outbox_due
    ON subscription_schema.subscription_order_request_outbox (status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FAILED', 'PROCESSING');
