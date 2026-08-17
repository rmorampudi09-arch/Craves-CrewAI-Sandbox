CREATE OR REPLACE FUNCTION payment_schema.normalize_refund_status_aggregate_id()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    parsed_refund_id UUID;
BEGIN
    IF NEW.event_type = 'REFUND_STATUS_CHANGED'
       AND NEW.event_key LIKE 'REFUND_STATUS_CHANGED:%' THEN
        BEGIN
            parsed_refund_id := split_part(NEW.event_key, ':', 2)::UUID;
            NEW.aggregate_id := parsed_refund_id;
        EXCEPTION WHEN invalid_text_representation THEN
            RAISE EXCEPTION 'Invalid refund identifier in refund status event key: %', NEW.event_key;
        END;
    END IF;
    RETURN NEW;
END;
$$;

UPDATE payment_schema.refund_status_outbox
   SET aggregate_id = split_part(event_key, ':', 2)::UUID
 WHERE event_type = 'REFUND_STATUS_CHANGED'
   AND event_key LIKE 'REFUND_STATUS_CHANGED:%';

DROP TRIGGER IF EXISTS trg_normalize_refund_status_aggregate_id
    ON payment_schema.refund_status_outbox;

CREATE TRIGGER trg_normalize_refund_status_aggregate_id
BEFORE INSERT OR UPDATE OF event_key, event_type, aggregate_id
ON payment_schema.refund_status_outbox
FOR EACH ROW
EXECUTE FUNCTION payment_schema.normalize_refund_status_aggregate_id();

COMMENT ON FUNCTION payment_schema.normalize_refund_status_aggregate_id() IS
    'Keeps refund_status_outbox.aggregate_id equal to the owning refund UUID while subject remains the chef sub-order UUID.';
