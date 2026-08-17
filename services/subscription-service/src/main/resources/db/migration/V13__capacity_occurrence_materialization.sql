CREATE OR REPLACE FUNCTION subscription_schema.fn_materialize_capacity_allocation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE subscription_schema.subscription_capacity_allocation allocation
       SET status = 'MATERIALIZED',
           occurrence_id = NEW.id,
           hold_expires_at = NULL,
           updated_at = now()
     WHERE allocation.subscription_id = NEW.subscription_id
       AND allocation.service_date = NEW.service_date
       AND allocation.meal_slot_code = NEW.meal_slot_code
       AND allocation.status = 'COMMITTED';

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_materialize_subscription_capacity
    ON subscription_schema.subscription_occurrence;

CREATE TRIGGER trg_materialize_subscription_capacity
AFTER INSERT
ON subscription_schema.subscription_occurrence
FOR EACH ROW
EXECUTE FUNCTION subscription_schema.fn_materialize_capacity_allocation();

COMMENT ON FUNCTION subscription_schema.fn_materialize_capacity_allocation() IS
    'Atomically changes the committed capacity allocation for a generated subscription meal slot to MATERIALIZED when its occurrence is inserted.';
