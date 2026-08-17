CREATE TABLE subscription_schema.subscription_plan_schedule (
    plan_id UUID PRIMARY KEY REFERENCES subscription_schema.subscription_plan(id),
    recurrence_type VARCHAR(20) NOT NULL,
    timezone VARCHAR(80) NOT NULL,
    service_time TIME NOT NULL,
    generation_lead_hours INTEGER NOT NULL CHECK (generation_lead_hours BETWEEN 1 AND 168),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
    created_by_identity_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at TIMESTAMPTZ,
    CONSTRAINT ck_subscription_plan_schedule_recurrence CHECK (recurrence_type IN ('WEEKLY', 'MONTHLY')),
    CONSTRAINT ck_subscription_plan_schedule_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_subscription_plan_schedule_activation CHECK (
        (status = 'ACTIVE' AND activated_at IS NOT NULL) OR status <> 'ACTIVE'
    )
);

CREATE TABLE subscription_schema.subscription_plan_schedule_item (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES subscription_schema.subscription_plan_schedule(plan_id) ON DELETE CASCADE,
    menu_item_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 100),
    iso_day_of_week SMALLINT,
    day_of_month SMALLINT,
    sequence_number INTEGER NOT NULL CHECK (sequence_number BETWEEN 1 AND 100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_subscription_plan_schedule_item_day CHECK (
        (iso_day_of_week BETWEEN 1 AND 7 AND day_of_month IS NULL) OR
        (day_of_month BETWEEN 1 AND 28 AND iso_day_of_week IS NULL)
    ),
    CONSTRAINT ux_subscription_plan_schedule_item UNIQUE (
        plan_id, menu_item_id, iso_day_of_week, day_of_month
    )
);

CREATE INDEX ix_subscription_plan_schedule_item_plan_day
    ON subscription_schema.subscription_plan_schedule_item (
        plan_id, iso_day_of_week, day_of_month, sequence_number
    );

CREATE TABLE subscription_schema.subscription_plan_schedule_audit (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES subscription_schema.subscription_plan(id),
    actor_identity_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL,
    schedule_version INTEGER NOT NULL,
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_subscription_plan_schedule_audit_plan
    ON subscription_schema.subscription_plan_schedule_audit (plan_id, created_at DESC);
