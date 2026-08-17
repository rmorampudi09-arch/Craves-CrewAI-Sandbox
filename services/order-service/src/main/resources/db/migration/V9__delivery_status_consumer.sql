ALTER TABLE order_schema.customer_order
    ADD COLUMN IF NOT EXISTS delivery_job_id UUID,
    ADD COLUMN IF NOT EXISTS delivery_provider_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS delivery_provider_delivery_id VARCHAR(200),
    ADD COLUMN IF NOT EXISTS delivery_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS delivery_tracking_url VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS delivery_status_observed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS delivery_status_event_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS ux_customer_order_delivery_job
    ON order_schema.customer_order (delivery_job_id)
    WHERE delivery_job_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_customer_order_delivery_status_event
    ON order_schema.customer_order (delivery_status_event_id)
    WHERE delivery_status_event_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_customer_order_delivery_status'
          AND conrelid = 'order_schema.customer_order'::regclass
    ) THEN
        ALTER TABLE order_schema.customer_order
            ADD CONSTRAINT chk_customer_order_delivery_status
            CHECK (
                delivery_status IS NULL
                OR delivery_status IN (
                    'PENDING',
                    'SEARCHING',
                    'COURIER_ASSIGNED',
                    'COURIER_TO_PICKUP',
                    'AT_PICKUP',
                    'PICKED_UP',
                    'IN_TRANSIT',
                    'AT_DROPOFF',
                    'DELIVERED',
                    'CANCELLED',
                    'DELAYED',
                    'RETURNING',
                    'RETURNED',
                    'FAILED'
                )
            )
            NOT VALID;
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS order_schema.delivery_status_inbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    event_version VARCHAR(20) NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID,
    subject VARCHAR(255) NOT NULL,
    delivery_job_id UUID NOT NULL,
    chef_sub_order_id UUID NOT NULL,
    provider_id VARCHAR(80) NOT NULL,
    provider_delivery_id VARCHAR(200) NOT NULL,
    normalized_status VARCHAR(40) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT chk_delivery_status_inbox_event_type CHECK (
        event_type = 'DELIVERY_STATUS_CHANGED'
    ),
    CONSTRAINT chk_delivery_status_inbox_normalized_status CHECK (
        normalized_status IN (
            'PENDING',
            'SEARCHING',
            'COURIER_ASSIGNED',
            'COURIER_TO_PICKUP',
            'AT_PICKUP',
            'PICKED_UP',
            'IN_TRANSIT',
            'AT_DROPOFF',
            'DELIVERED',
            'CANCELLED',
            'DELAYED',
            'RETURNING',
            'RETURNED',
            'FAILED'
        )
    ),
    CONSTRAINT chk_delivery_status_inbox_processing CHECK (
        processing_status IN (
            'RECEIVED',
            'PROCESSED',
            'STALE',
            'TERMINAL_PROTECTED',
            'NO_CHANGE'
        )
    )
);

CREATE INDEX IF NOT EXISTS ix_delivery_status_inbox_order
    ON order_schema.delivery_status_inbox (chef_sub_order_id, received_at DESC);

CREATE INDEX IF NOT EXISTS ix_delivery_status_inbox_processing
    ON order_schema.delivery_status_inbox (processing_status, received_at);

CREATE TABLE IF NOT EXISTS order_schema.order_delivery_status_history (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES order_schema.customer_order(id),
    delivery_job_id UUID NOT NULL,
    event_id UUID NOT NULL,
    old_status VARCHAR(40),
    new_status VARCHAR(40) NOT NULL,
    provider_id VARCHAR(80) NOT NULL,
    provider_delivery_id VARCHAR(200) NOT NULL,
    tracking_url VARCHAR(2048),
    observed_at TIMESTAMPTZ NOT NULL,
    source VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_order_delivery_status_history_event UNIQUE (event_id),
    CONSTRAINT chk_order_delivery_history_new_status CHECK (
        new_status IN (
            'PENDING',
            'SEARCHING',
            'COURIER_ASSIGNED',
            'COURIER_TO_PICKUP',
            'AT_PICKUP',
            'PICKED_UP',
            'IN_TRANSIT',
            'AT_DROPOFF',
            'DELIVERED',
            'CANCELLED',
            'DELAYED',
            'RETURNING',
            'RETURNED',
            'FAILED'
        )
    )
);

CREATE INDEX IF NOT EXISTS ix_order_delivery_status_history_order
    ON order_schema.order_delivery_status_history (order_id, observed_at, created_at);

COMMENT ON COLUMN order_schema.customer_order.delivery_status IS
    'Provider-neutral delivery lifecycle projection. It does not replace the commercial order status.';

COMMENT ON COLUMN order_schema.customer_order.delivery_tracking_url IS
    'Latest provider tracking URL accepted from DELIVERY_STATUS_CHANGED; never used as an authentication credential.';

COMMENT ON TABLE order_schema.delivery_status_inbox IS
    'Idempotent Order Service inbox for DELIVERY_STATUS_CHANGED v1 events.';

COMMENT ON TABLE order_schema.order_delivery_status_history IS
    'Append-only customer-order delivery projection history; raw provider payload is retained only in the inbox.';
