CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS subscription_schema;

CREATE TABLE IF NOT EXISTS subscription_schema.subscription_plan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_code VARCHAR(80) NOT NULL UNIQUE,
    chef_identity_id UUID,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    billing_period VARCHAR(30) NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_subscription_plan_billing_period CHECK (billing_period IN ('WEEKLY', 'MONTHLY')),
    CONSTRAINT ck_subscription_plan_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_subscription_plan_amount CHECK (amount >= 0)
);

CREATE INDEX IF NOT EXISTS ix_subscription_plan_status
    ON subscription_schema.subscription_plan (status, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_subscription_plan_chef
    ON subscription_schema.subscription_plan (chef_identity_id, created_at DESC);

CREATE TABLE IF NOT EXISTS subscription_schema.customer_subscription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_identity_id UUID NOT NULL,
    plan_id UUID NOT NULL REFERENCES subscription_schema.subscription_plan(id),
    chef_identity_id UUID,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING_PAYMENT',
    start_date DATE NOT NULL,
    end_date DATE,
    next_service_date DATE,
    delivery_address_id UUID,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_customer_subscription_status CHECK (status IN ('PENDING_PAYMENT', 'ACTIVE', 'PAUSED', 'PAYMENT_FAILED', 'EXPIRED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS ix_customer_subscription_customer
    ON subscription_schema.customer_subscription (customer_identity_id, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_customer_subscription_status
    ON subscription_schema.customer_subscription (status, next_service_date, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_customer_subscription_plan
    ON subscription_schema.customer_subscription (plan_id, created_at DESC);

CREATE TABLE IF NOT EXISTS subscription_schema.subscription_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES subscription_schema.customer_subscription(id),
    old_status VARCHAR(40),
    new_status VARCHAR(40) NOT NULL,
    reason TEXT,
    actor_identity_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_subscription_status_history_subscription
    ON subscription_schema.subscription_status_history (subscription_id, created_at DESC);
