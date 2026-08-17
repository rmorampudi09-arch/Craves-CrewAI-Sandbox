ALTER TABLE subscription_schema.customer_subscription
    ADD COLUMN next_billing_date DATE,
    ADD COLUMN billing_lock_token UUID,
    ADD COLUMN billing_locked_at TIMESTAMPTZ;

UPDATE subscription_schema.customer_subscription
   SET next_billing_date = start_date
 WHERE next_billing_date IS NULL;

CREATE OR REPLACE FUNCTION subscription_schema.initialize_next_billing_date()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.next_billing_date IS NULL THEN
        NEW.next_billing_date := NEW.start_date;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_initialize_next_billing_date
BEFORE INSERT OR UPDATE OF start_date
ON subscription_schema.customer_subscription
FOR EACH ROW
EXECUTE FUNCTION subscription_schema.initialize_next_billing_date();

CREATE INDEX ix_customer_subscription_billing_due
    ON subscription_schema.customer_subscription (status, next_billing_date, billing_locked_at)
    WHERE status IN ('PENDING_PAYMENT', 'ACTIVE', 'PAYMENT_FAILED') AND next_billing_date IS NOT NULL;

CREATE TABLE subscription_schema.subscription_invoice (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES subscription_schema.customer_subscription(id),
    plan_id UUID NOT NULL REFERENCES subscription_schema.subscription_plan(id),
    customer_identity_id UUID NOT NULL,
    chef_identity_id UUID,
    cycle_start DATE NOT NULL,
    cycle_end DATE NOT NULL,
    amount NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PAYMENT_REQUESTED',
    provider_payment_order_id UUID,
    provider_reference VARCHAR(160),
    failure_code VARCHAR(120),
    failure_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at TIMESTAMPTZ,
    CONSTRAINT ux_subscription_invoice_cycle UNIQUE (subscription_id, cycle_start),
    CONSTRAINT ck_subscription_invoice_cycle CHECK (cycle_end > cycle_start),
    CONSTRAINT ck_subscription_invoice_status CHECK (
        status IN ('PAYMENT_REQUESTED', 'PAYMENT_PENDING', 'PAID', 'FAILED', 'CANCELLED')
    )
);

CREATE INDEX ix_subscription_invoice_status
    ON subscription_schema.subscription_invoice (status, cycle_start, created_at);

CREATE TABLE subscription_schema.subscription_invoice_history (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES subscription_schema.subscription_invoice(id),
    old_status VARCHAR(40),
    new_status VARCHAR(40) NOT NULL,
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE subscription_schema.subscription_payment_outbox (
    id UUID PRIMARY KEY,
    event_key VARCHAR(200) NOT NULL UNIQUE,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    event_version VARCHAR(20) NOT NULL,
    correlation_id UUID NOT NULL,
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
    CONSTRAINT ck_subscription_payment_outbox_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'DEAD_LETTER')
    )
);

CREATE INDEX ix_subscription_payment_outbox_due
    ON subscription_schema.subscription_payment_outbox (status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FAILED', 'PROCESSING');
