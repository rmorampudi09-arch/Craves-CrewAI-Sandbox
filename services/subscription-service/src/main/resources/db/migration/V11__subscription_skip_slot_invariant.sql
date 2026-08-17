CREATE OR REPLACE FUNCTION subscription_schema.fn_hold_skip_until_all_slots_exist()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_plan_id UUID;
    v_expected_slots INTEGER;
    v_existing_slots INTEGER;
BEGIN
    IF NEW.status <> 'APPLIED' THEN
        RETURN NEW;
    END IF;

    SELECT cs.plan_id
      INTO v_plan_id
      FROM subscription_schema.customer_subscription cs
     WHERE cs.id = NEW.subscription_id;

    IF v_plan_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT COUNT(DISTINCT item.meal_slot_code)
      INTO v_expected_slots
      FROM subscription_schema.subscription_plan_schedule schedule
      JOIN subscription_schema.subscription_plan_schedule_item item
        ON item.plan_id = schedule.plan_id
     WHERE schedule.plan_id = v_plan_id
       AND schedule.status = 'ACTIVE'
       AND (
            (schedule.recurrence_type = 'WEEKLY'
             AND item.iso_day_of_week = EXTRACT(ISODOW FROM NEW.service_date)::INTEGER)
         OR (schedule.recurrence_type = 'MONTHLY'
             AND item.day_of_month = EXTRACT(DAY FROM NEW.service_date)::INTEGER)
       );

    SELECT COUNT(DISTINCT occurrence.meal_slot_code)
      INTO v_existing_slots
      FROM subscription_schema.subscription_occurrence occurrence
     WHERE occurrence.subscription_id = NEW.subscription_id
       AND occurrence.service_date = NEW.service_date;

    IF COALESCE(v_expected_slots, 0) > COALESCE(v_existing_slots, 0) THEN
        NEW.status := 'REQUESTED';
        NEW.applied_at := NULL;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_hold_subscription_skip_insert_until_all_slots_exist
    ON subscription_schema.subscription_skip_request;
DROP TRIGGER IF EXISTS trg_hold_subscription_skip_update_until_all_slots_exist
    ON subscription_schema.subscription_skip_request;
DROP TRIGGER IF EXISTS trg_hold_subscription_skip_until_all_slots_exist
    ON subscription_schema.subscription_skip_request;

CREATE TRIGGER trg_hold_subscription_skip_insert_until_all_slots_exist
BEFORE INSERT
ON subscription_schema.subscription_skip_request
FOR EACH ROW
WHEN (NEW.status = 'APPLIED')
EXECUTE FUNCTION subscription_schema.fn_hold_skip_until_all_slots_exist();

CREATE TRIGGER trg_hold_subscription_skip_update_until_all_slots_exist
BEFORE UPDATE OF status, applied_at
ON subscription_schema.subscription_skip_request
FOR EACH ROW
WHEN (NEW.status = 'APPLIED')
EXECUTE FUNCTION subscription_schema.fn_hold_skip_until_all_slots_exist();

COMMENT ON FUNCTION subscription_schema.fn_hold_skip_until_all_slots_exist() IS
    'Keeps a whole-service-date skip request REQUESTED until occurrences exist for every active configured meal slot on that date, preventing staggered generation from bypassing the skip.';
