ALTER TABLE subscription_schema.subscription_plan_schedule_item
    ADD COLUMN meal_slot_code VARCHAR(40),
    ADD COLUMN service_time TIME;

UPDATE subscription_schema.subscription_plan_schedule_item item
SET meal_slot_code = 'LEGACY',
    service_time = schedule.service_time
FROM subscription_schema.subscription_plan_schedule schedule
WHERE schedule.plan_id = item.plan_id;

ALTER TABLE subscription_schema.subscription_plan_schedule_item
    ALTER COLUMN meal_slot_code SET NOT NULL,
    ALTER COLUMN service_time SET NOT NULL;

ALTER TABLE subscription_schema.subscription_plan_schedule_item
    ADD CONSTRAINT ck_subscription_schedule_meal_slot_code
        CHECK (meal_slot_code ~ '^[A-Z0-9][A-Z0-9_-]{0,39}$');

CREATE INDEX ix_subscription_plan_schedule_item_slot
    ON subscription_schema.subscription_plan_schedule_item (
        plan_id,
        (COALESCE(iso_day_of_week, day_of_month)),
        meal_slot_code,
        service_time,
        sequence_number
    );

ALTER TABLE subscription_schema.subscription_occurrence
    ADD COLUMN meal_slot_code VARCHAR(40);

UPDATE subscription_schema.subscription_occurrence
SET meal_slot_code = 'LEGACY';

ALTER TABLE subscription_schema.subscription_occurrence
    ALTER COLUMN meal_slot_code SET NOT NULL;

ALTER TABLE subscription_schema.subscription_occurrence
    ADD CONSTRAINT ck_subscription_occurrence_meal_slot_code
        CHECK (meal_slot_code ~ '^[A-Z0-9][A-Z0-9_-]{0,39}$');

ALTER TABLE subscription_schema.subscription_occurrence
    DROP CONSTRAINT ux_subscription_occurrence_date;

ALTER TABLE subscription_schema.subscription_occurrence
    ADD CONSTRAINT ux_subscription_occurrence_slot
        UNIQUE (subscription_id, service_date, meal_slot_code);

DROP INDEX IF EXISTS subscription_schema.ix_subscription_occurrence_subscription_service;

CREATE INDEX ix_subscription_occurrence_subscription_service
    ON subscription_schema.subscription_occurrence (
        subscription_id,
        service_date,
        meal_slot_code,
        status
    );
