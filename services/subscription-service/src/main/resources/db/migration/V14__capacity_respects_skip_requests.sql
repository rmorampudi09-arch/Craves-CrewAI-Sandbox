CREATE OR REPLACE FUNCTION subscription_schema.fn_capacity_respects_skip_request()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM subscription_schema.subscription_skip_request skip_request
         WHERE skip_request.subscription_id = NEW.subscription_id
           AND skip_request.service_date = NEW.service_date
           AND skip_request.status IN ('REQUESTED', 'APPLIED')
    ) THEN
        NEW.status := 'RELEASED';
        NEW.hold_expires_at := NULL;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_capacity_respects_skip_request
    ON subscription_schema.subscription_capacity_allocation;

CREATE TRIGGER trg_capacity_respects_skip_request
BEFORE INSERT OR UPDATE OF status, hold_expires_at
ON subscription_schema.subscription_capacity_allocation
FOR EACH ROW
EXECUTE FUNCTION subscription_schema.fn_capacity_respects_skip_request();

COMMENT ON FUNCTION subscription_schema.fn_capacity_respects_skip_request() IS
    'Fail-safe invariant preventing capacity projection, retry or reconciliation from re-reserving a customer service date with an active skip request.';
