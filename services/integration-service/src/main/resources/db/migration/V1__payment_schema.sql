CREATE SCHEMA IF NOT EXISTS payment_schema;

CREATE TABLE payment_schema.payment_order (
    id UUID PRIMARY KEY,
    checkout_id UUID NOT NULL,
    customer_identity_id UUID NOT NULL,
    craves_payment_order_ref VARCHAR(80) NOT NULL UNIQUE,
    cashfree_order_id VARCHAR(120) UNIQUE,
    cashfree_cf_order_id VARCHAR(120),
    payment_session_id TEXT,
    amount NUMERIC(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(40) NOT NULL,
    provider_status VARCHAR(80),
    request_payload JSONB,
    response_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_schema.payment_attempt (
    id UUID PRIMARY KEY,
    payment_order_id UUID NOT NULL REFERENCES payment_schema.payment_order(id) ON DELETE CASCADE,
    cf_payment_id VARCHAR(120),
    payment_status VARCHAR(80),
    payment_amount NUMERIC(10,2),
    payment_currency VARCHAR(3),
    raw_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_schema.payment_event (
    id UUID PRIMARY KEY,
    payment_order_id UUID REFERENCES payment_schema.payment_order(id) ON DELETE SET NULL,
    provider_event_id VARCHAR(180) NOT NULL UNIQUE,
    event_type VARCHAR(120),
    payment_status VARCHAR(80),
    raw_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_schema.webhook_inbox (
    id UUID PRIMARY KEY,
    provider VARCHAR(40) NOT NULL,
    signature_hash VARCHAR(128),
    event_identity VARCHAR(180),
    processing_status VARCHAR(40) NOT NULL,
    raw_payload TEXT NOT NULL,
    error_message VARCHAR(500),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);

CREATE TABLE payment_schema.integration_idempotency (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    operation VARCHAR(80) NOT NULL,
    outcome_ref VARCHAR(160),
    response_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_schema.refund (
    id UUID PRIMARY KEY,
    payment_order_id UUID REFERENCES payment_schema.payment_order(id) ON DELETE SET NULL,
    refund_ref VARCHAR(120) NOT NULL UNIQUE,
    amount NUMERIC(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(40) NOT NULL,
    reason VARCHAR(255),
    provider_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_order_checkout ON payment_schema.payment_order (checkout_id);
CREATE INDEX idx_payment_order_customer ON payment_schema.payment_order (customer_identity_id, created_at DESC);
CREATE INDEX idx_payment_order_status ON payment_schema.payment_order (status);
CREATE INDEX idx_payment_attempt_order ON payment_schema.payment_attempt (payment_order_id, created_at DESC);
CREATE INDEX idx_webhook_inbox_status ON payment_schema.webhook_inbox (processing_status, received_at);
