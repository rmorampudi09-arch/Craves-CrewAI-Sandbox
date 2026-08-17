ALTER TABLE subscription_schema.customer_subscription
    ADD COLUMN generation_lock_token UUID,
    ADD COLUMN generation_locked_at TIMESTAMPTZ;

CREATE INDEX ix_customer_subscription_generation_due
    ON subscription_schema.customer_subscription (status, next_service_date, generation_locked_at)
    WHERE status = 'ACTIVE' AND next_service_date IS NOT NULL;

CREATE TABLE subscription_schema.subscription_occurrence (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES subscription_schema.customer_subscription(id),
    plan_id UUID NOT NULL REFERENCES subscription_schema.subscription_plan(id),
    customer_identity_id UUID NOT NULL,
    chef_identity_id UUID NOT NULL,
    delivery_address_id UUID NOT NULL,
    service_date DATE NOT NULL,
    service_at TIMESTAMPTZ NOT NULL,
    schedule_version INTEGER NOT NULL CHECK (schedule_version > 0),
    status VARCHAR(40) NOT NULL DEFAULT 'BILLING_PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_subscription_occurrence_date UNIQUE (subscription_id, service_date),
    CONSTRAINT ck_subscription_occurrence_status CHECK (
        status IN (
            'BILLING_PENDING', 'PAYMENT_PENDING', 'READY_FOR_ORDER',
            'ORDER_REQUESTED', 'ORDER_CREATED', 'SKIPPED', 'CANCELLED', 'FAILED'
        )
    )
);

CREATE INDEX ix_subscription_occurrence_status_service
    ON subscription_schema.subscription_occurrence (status, service_at, created_at);

CREATE TABLE subscription_schema.subscription_occurrence_item (
    id UUID PRIMARY KEY,
    occurrence_id UUID NOT NULL REFERENCES subscription_schema.subscription_occurrence(id) ON DELETE CASCADE,
    menu_item_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 100),
    sequence_number INTEGER NOT NULL CHECK (sequence_number BETWEEN 1 AND 100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_subscription_occurrence_item UNIQUE (occurrence_id, menu_item_id)
);

CREATE TABLE subscription_schema.subscription_occurrence_history (
    id UUID PRIMARY KEY,
    occurrence_id UUID NOT NULL REFERENCES subscription_schema.subscription_occurrence(id),
    old_status VARCHAR(40),
    new_status VARCHAR(40) NOT NULL,
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_subscription_occurrence_history_occurrence
    ON subscription_schema.subscription_occurrence_history (occurrence_id, created_at DESC);
