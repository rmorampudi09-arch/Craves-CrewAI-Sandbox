CREATE SCHEMA IF NOT EXISTS payment_schema;

ALTER TABLE payment_schema.refund
    ADD COLUMN IF NOT EXISTS checkout_id UUID,
    ADD COLUMN IF NOT EXISTS chef_sub_order_id UUID,
    ADD COLUMN IF NOT EXISTS customer_identity_id UUID,
    ADD COLUMN IF NOT EXISTS cashfree_order_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS cf_refund_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS provider_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS idempotency_key UUID,
    ADD COLUMN IF NOT EXISTS request_event_id UUID,
    ADD COLUMN IF NOT EXISTS request_event_payload JSONB,
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS lock_token UUID,
    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_error TEXT,
    ADD COLUMN IF NOT EXISTS requested_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS processed_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS ux_refund_chef_sub_order
    ON payment_schema.refund (chef_sub_order_id)
    WHERE chef_sub_order_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_refund_request_event
    ON payment_schema.refund (request_event_id)
    WHERE request_event_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_refund_idempotency_key
    ON payment_schema.refund (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_refund_due
    ON payment_schema.refund (status, next_attempt_at, created_at)
    WHERE status IN ('REQUESTED', 'RETRY', 'PENDING', 'ONHOLD', 'PROCESSING');

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_refund_workflow_status'
          AND conrelid = 'payment_schema.refund'::regclass
    ) THEN
        ALTER TABLE payment_schema.refund
            ADD CONSTRAINT ck_refund_workflow_status
            CHECK (status IN (
                'REQUESTED', 'PROCESSING', 'RETRY', 'PENDING', 'ONHOLD',
                'SUCCESS', 'FAILED', 'CANCELLED', 'DEAD_LETTER'
            )) NOT VALID;
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_refund_attempt_count'
          AND conrelid = 'payment_schema.refund'::regclass
    ) THEN
        ALTER TABLE payment_schema.refund
            ADD CONSTRAINT ck_refund_attempt_count
            CHECK (attempt_count >= 0) NOT VALID;
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS payment_schema.refund_request_inbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    event_version VARCHAR(20) NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID NOT NULL,
    subject UUID NOT NULL,
    payload JSONB NOT NULL,
    processing_status VARCHAR(30) NOT NULL,
    error_message TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT ck_refund_request_inbox_status CHECK (
        processing_status IN ('RECEIVED', 'PROCESSED', 'DUPLICATE', 'REJECTED', 'FAILED')
    )
);

CREATE INDEX IF NOT EXISTS ix_refund_request_inbox_status
    ON payment_schema.refund_request_inbox (processing_status, received_at);

CREATE TABLE IF NOT EXISTS payment_schema.refund_status_outbox (
    id UUID PRIMARY KEY,
    event_key VARCHAR(180) NOT NULL UNIQUE,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    event_version VARCHAR(20) NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID NOT NULL,
    subject UUID NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lock_token UUID,
    locked_at TIMESTAMPTZ,
    broker_message_id VARCHAR(180),
    published_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_refund_status_outbox_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'DEAD_LETTER')
    ),
    CONSTRAINT ck_refund_status_outbox_attempts CHECK (attempt_count >= 0)
);

CREATE INDEX IF NOT EXISTS ix_refund_status_outbox_due
    ON payment_schema.refund_status_outbox (status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FAILED', 'PROCESSING');

COMMENT ON TABLE payment_schema.refund_request_inbox IS
    'Idempotent inbox for REFUND_REQUESTED domain events received from Azure Service Bus.';

COMMENT ON TABLE payment_schema.refund_status_outbox IS
    'Transactional outbox for REFUND_STATUS_CHANGED events. Payloads may contain customer and payment identifiers and must not be logged.';
