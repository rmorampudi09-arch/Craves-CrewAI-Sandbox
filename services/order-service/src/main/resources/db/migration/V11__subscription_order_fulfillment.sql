ALTER TABLE order_schema.customer_order
    ALTER COLUMN checkout_id DROP NOT NULL,
    ADD COLUMN order_source VARCHAR(30) NOT NULL DEFAULT 'ON_DEMAND',
    ADD COLUMN subscription_occurrence_id UUID,
    ADD COLUMN subscription_id UUID,
    ADD COLUMN scheduled_service_at TIMESTAMPTZ,
    ADD COLUMN financial_allocation_status VARCHAR(40) NOT NULL DEFAULT 'NOT_APPLICABLE';

ALTER TABLE order_schema.customer_order
    ADD CONSTRAINT ck_order_source CHECK (order_source IN ('ON_DEMAND', 'SUBSCRIPTION')),
    ADD CONSTRAINT ck_order_financial_allocation CHECK (
        financial_allocation_status IN ('NOT_APPLICABLE', 'PENDING_POLICY', 'ALLOCATED', 'REVERSED')
    ),
    ADD CONSTRAINT ck_subscription_order_fields CHECK (
        (order_source = 'ON_DEMAND' AND subscription_occurrence_id IS NULL AND subscription_id IS NULL)
        OR
        (order_source = 'SUBSCRIPTION' AND subscription_occurrence_id IS NOT NULL
         AND subscription_id IS NOT NULL AND scheduled_service_at IS NOT NULL
         AND financial_allocation_status = 'PENDING_POLICY')
    );

CREATE UNIQUE INDEX ux_order_subscription_occurrence
    ON order_schema.customer_order (subscription_occurrence_id)
    WHERE subscription_occurrence_id IS NOT NULL;

CREATE INDEX ix_order_subscription
    ON order_schema.customer_order (subscription_id, scheduled_service_at)
    WHERE subscription_id IS NOT NULL;

CREATE TABLE order_schema.subscription_order_request_inbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    event_version VARCHAR(20) NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID,
    subject UUID NOT NULL,
    occurrence_id UUID NOT NULL UNIQUE,
    subscription_id UUID NOT NULL,
    payload JSONB NOT NULL,
    processing_status VARCHAR(30) NOT NULL,
    order_id UUID,
    error_message VARCHAR(1000),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT ck_subscription_order_inbox_status CHECK (
        processing_status IN ('RECEIVED', 'PROCESSED', 'DUPLICATE', 'REJECTED', 'FAILED')
    )
);

CREATE INDEX ix_subscription_order_inbox_status
    ON order_schema.subscription_order_request_inbox (processing_status, received_at);

CREATE TABLE order_schema.subscription_order_callback_outbox (
    id UUID PRIMARY KEY,
    occurrence_id UUID NOT NULL UNIQUE,
    order_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lock_token UUID,
    locked_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_subscription_order_callback_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'FAILED', 'DEAD_LETTER')
    )
);

CREATE INDEX ix_subscription_order_callback_due
    ON order_schema.subscription_order_callback_outbox (status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FAILED', 'PROCESSING');
