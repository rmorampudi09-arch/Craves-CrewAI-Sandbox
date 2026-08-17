ALTER TABLE order_schema.customer_order
    ADD COLUMN IF NOT EXISTS refund_id UUID,
    ADD COLUMN IF NOT EXISTS refund_reference VARCHAR(40),
    ADD COLUMN IF NOT EXISTS refund_provider_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS cf_refund_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS refund_status_event_id UUID,
    ADD COLUMN IF NOT EXISTS refund_status_updated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS refund_completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS refund_failed_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS ux_customer_order_refund_id
    ON order_schema.customer_order (refund_id)
    WHERE refund_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_customer_order_refund_reference
    ON order_schema.customer_order (refund_reference)
    WHERE refund_reference IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_customer_order_refund_status_event
    ON order_schema.customer_order (refund_status_event_id)
    WHERE refund_status_event_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_customer_order_refund_provider_status'
          AND conrelid = 'order_schema.customer_order'::regclass
    ) THEN
        ALTER TABLE order_schema.customer_order
            ADD CONSTRAINT chk_customer_order_refund_provider_status
            CHECK (
                refund_provider_status IS NULL
                OR refund_provider_status IN ('PENDING', 'ONHOLD', 'SUCCESS', 'FAILED', 'CANCELLED')
            )
            NOT VALID;
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS order_schema.refund_status_inbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    event_version VARCHAR(20) NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID NOT NULL,
    subject UUID NOT NULL,
    refund_id UUID NOT NULL,
    normalized_status VARCHAR(40) NOT NULL,
    provider_status VARCHAR(40) NOT NULL,
    payload JSONB NOT NULL,
    processing_status VARCHAR(24) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT chk_refund_status_inbox_event_type CHECK (event_type = 'REFUND_STATUS_CHANGED'),
    CONSTRAINT chk_refund_status_inbox_normalized_status CHECK (
        normalized_status IN ('REFUND_PENDING', 'REFUNDED', 'REFUND_FAILED')
    ),
    CONSTRAINT chk_refund_status_inbox_provider_status CHECK (
        provider_status IN ('PENDING', 'ONHOLD', 'SUCCESS', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT chk_refund_status_inbox_processing_status CHECK (
        processing_status IN ('RECEIVED', 'PROCESSED', 'STALE')
    )
);

CREATE INDEX IF NOT EXISTS ix_refund_status_inbox_subject
    ON order_schema.refund_status_inbox (subject, received_at DESC);

CREATE INDEX IF NOT EXISTS ix_refund_status_inbox_processing
    ON order_schema.refund_status_inbox (processing_status, received_at);

COMMENT ON COLUMN order_schema.customer_order.refund_id IS
    'Integration Service refund identifier from the latest accepted REFUND_STATUS_CHANGED event.';

COMMENT ON COLUMN order_schema.customer_order.refund_status_updated_at IS
    'Provider-derived UTC status timestamp used to reject stale or out-of-order refund events.';

COMMENT ON TABLE order_schema.refund_status_inbox IS
    'Idempotent inbox for REFUND_STATUS_CHANGED events consumed by Order Service.';
