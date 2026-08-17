ALTER TABLE order_schema.customer_order
    ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMPTZ;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_customer_order_prep_time_positive'
          AND conrelid = 'order_schema.customer_order'::regclass
    ) THEN
        ALTER TABLE order_schema.customer_order
            ADD CONSTRAINT chk_customer_order_prep_time_positive
            CHECK (prep_time_minutes IS NULL OR prep_time_minutes > 0)
            NOT VALID;
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_customer_order_accepted_timestamps'
          AND conrelid = 'order_schema.customer_order'::regclass
    ) THEN
        ALTER TABLE order_schema.customer_order
            ADD CONSTRAINT chk_customer_order_accepted_timestamps
            CHECK (
                status <> 'CHEF_ACCEPTED'
                OR (
                    prep_time_minutes IS NOT NULL
                    AND accepted_at IS NOT NULL
                    AND ready_at IS NOT NULL
                    AND ready_at > accepted_at
                )
            )
            NOT VALID;
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS order_schema.domain_event_outbox (
    id UUID PRIMARY KEY,
    event_key VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    event_version VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID,
    source VARCHAR(120) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    payload_json JSONB NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lock_token UUID,
    locked_at TIMESTAMPTZ,
    broker_message_id VARCHAR(255),
    published_at TIMESTAMPTZ,
    last_error VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_domain_event_outbox_event_key UNIQUE (event_key),
    CONSTRAINT chk_domain_event_outbox_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'FAILED', 'PUBLISHED', 'DEAD')
    ),
    CONSTRAINT chk_domain_event_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX IF NOT EXISTS idx_domain_event_outbox_dispatch
    ON order_schema.domain_event_outbox (status, next_attempt_at, occurred_at)
    WHERE status IN ('PENDING', 'FAILED', 'PROCESSING');

CREATE INDEX IF NOT EXISTS idx_domain_event_outbox_aggregate
    ON order_schema.domain_event_outbox (aggregate_type, aggregate_id, created_at DESC);

COMMENT ON COLUMN order_schema.customer_order.accepted_at IS
    'UTC timestamp at which the chef-specific order was first accepted.';

COMMENT ON TABLE order_schema.domain_event_outbox IS
    'Transactional domain events. Payloads may contain delivery-required PII and must never be written to application logs.';

COMMENT ON COLUMN order_schema.domain_event_outbox.event_key IS
    'Stable uniqueness key preventing duplicate business events for one aggregate transition.';
