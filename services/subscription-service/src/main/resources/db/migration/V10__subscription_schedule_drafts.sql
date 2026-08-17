CREATE TABLE subscription_schema.subscription_plan_schedule_draft (
    plan_id UUID PRIMARY KEY REFERENCES subscription_schema.subscription_plan(id),
    recurrence_type VARCHAR(20) NOT NULL,
    timezone VARCHAR(80) NOT NULL,
    service_time TIME NOT NULL,
    generation_lead_hours INTEGER NOT NULL CHECK (generation_lead_hours BETWEEN 1 AND 168),
    version INTEGER NOT NULL CHECK (version > 0),
    created_by_identity_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_subscription_schedule_draft_recurrence CHECK (recurrence_type IN ('WEEKLY', 'MONTHLY'))
);

CREATE TABLE subscription_schema.subscription_plan_schedule_draft_item (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES subscription_schema.subscription_plan_schedule_draft(plan_id) ON DELETE CASCADE,
    menu_item_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 100),
    iso_day_of_week INTEGER,
    day_of_month INTEGER,
    meal_slot_code VARCHAR(40) NOT NULL,
    service_time TIME NOT NULL,
    sequence_number INTEGER NOT NULL CHECK (sequence_number BETWEEN 1 AND 100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_subscription_schedule_draft_day CHECK (
        (iso_day_of_week BETWEEN 1 AND 7 AND day_of_month IS NULL)
        OR (day_of_month BETWEEN 1 AND 28 AND iso_day_of_week IS NULL)
    ),
    CONSTRAINT ck_subscription_schedule_draft_slot CHECK (
        meal_slot_code ~ '^[A-Z0-9][A-Z0-9_-]{0,39}$'
    )
);

CREATE UNIQUE INDEX ux_subscription_schedule_draft_item
    ON subscription_schema.subscription_plan_schedule_draft_item (
        plan_id,
        menu_item_id,
        (COALESCE(iso_day_of_week, 0)),
        (COALESCE(day_of_month, 0)),
        meal_slot_code
    );

CREATE INDEX ix_subscription_schedule_draft_item_order
    ON subscription_schema.subscription_plan_schedule_draft_item (
        plan_id,
        (COALESCE(iso_day_of_week, day_of_month)),
        service_time,
        meal_slot_code,
        sequence_number
    );

INSERT INTO subscription_schema.subscription_plan_schedule_draft (
    plan_id, recurrence_type, timezone, service_time, generation_lead_hours,
    version, created_by_identity_id, created_at, updated_at
)
SELECT plan_id, recurrence_type, timezone, service_time, generation_lead_hours,
       version, created_by_identity_id, created_at, updated_at
FROM subscription_schema.subscription_plan_schedule
WHERE status = 'DRAFT';

INSERT INTO subscription_schema.subscription_plan_schedule_draft_item (
    id, plan_id, menu_item_id, quantity, iso_day_of_week, day_of_month,
    meal_slot_code, service_time, sequence_number, created_at
)
SELECT item.id, item.plan_id, item.menu_item_id, item.quantity, item.iso_day_of_week,
       item.day_of_month, item.meal_slot_code, item.service_time, item.sequence_number, item.created_at
FROM subscription_schema.subscription_plan_schedule_item item
JOIN subscription_schema.subscription_plan_schedule schedule ON schedule.plan_id = item.plan_id
WHERE schedule.status = 'DRAFT';

DELETE FROM subscription_schema.subscription_plan_schedule_item
WHERE plan_id IN (
    SELECT plan_id FROM subscription_schema.subscription_plan_schedule WHERE status = 'DRAFT'
);

DELETE FROM subscription_schema.subscription_plan_schedule
WHERE status = 'DRAFT';
