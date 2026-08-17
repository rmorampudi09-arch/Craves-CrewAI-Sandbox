CREATE TABLE payment_schema.subscription_payment_intent (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL UNIQUE,
    subscription_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    customer_identity_id UUID NOT NULL,
    chef_identity_id UUID,
    cycle_start DATE NOT NULL,
    cycle_end DATE NOT NULL,
    amount NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PAYMENT_REQUESTED',
    cashfree_order_id VARCHAR(120) UNIQUE,
    cashfree_cf_order_id VARCHAR(120),
    payment_session_id TEXT,
    provider_status VARCHAR(80),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at TIMESTAMPTZ,
    CONSTRAINT ck_subscription_payment_cycle CHECK (cycle_end > cycle_start),
    CONSTRAINT ck_subscription_payment_status CHECK (
        status IN ('PAYMENT_REQUESTED', 'PAYMENT_PENDING', 'PAID', 'FAILED', 'CANCELLED')
    )
);

CREATE INDEX ix_subscription_payment_customer
    ON payment_schema.subscription_payment_intent (customer_identity_id, created_at DESC);

CREATE INDEX ix_subscription_payment_status
    ON payment_schema.subscription_payment_intent (status, updated_at);

CREATE TABLE payment_schema.subscription_payment_request_inbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    event_version VARCHAR(20) NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID,
    subject UUID NOT NULL,
    payload JSONB NOT NULL,
    processing_status VARCHAR(30) NOT NULL,
    error_message VARCHAR(1000),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT ck_subscription_payment_inbox_status CHECK (
        processing_status IN ('RECEIVED', 'PROCESSED', 'DUPLICATE', 'REJECTED', 'FAILED')
    )
);

CREATE INDEX ix_subscription_payment_inbox_status
    ON payment_schema.subscription_payment_request_inbox (processing_status, received_at);

CREATE TABLE payment_schema.subscription_payment_status_outbox (
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
    CONSTRAINT ck_subscription_payment_status_outbox_state CHECK (
        status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'DEAD_LETTER')
    )
);

CREATE INDEX ix_subscription_payment_status_outbox_due
    ON payment_schema.subscription_payment_status_outbox (status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FAILED', 'PROCESSING');
