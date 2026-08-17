ALTER TABLE subscription_schema.subscription_invoice
    ADD COLUMN provider_payment_intent_id UUID,
    ADD COLUMN provider_status VARCHAR(80),
    ADD COLUMN provider_payment_id VARCHAR(160);

CREATE TABLE subscription_schema.subscription_payment_status_inbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    event_version VARCHAR(20) NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID,
    subject UUID NOT NULL,
    invoice_id UUID NOT NULL,
    subscription_id UUID NOT NULL,
    payload JSONB NOT NULL,
    processing_status VARCHAR(30) NOT NULL,
    error_message VARCHAR(1000),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT ck_subscription_payment_status_inbox_state CHECK (
        processing_status IN ('RECEIVED', 'PROCESSED', 'DUPLICATE', 'REJECTED', 'FAILED')
    )
);

CREATE INDEX ix_subscription_payment_status_inbox_state
    ON subscription_schema.subscription_payment_status_inbox (processing_status, received_at);

CREATE OR REPLACE FUNCTION subscription_schema.resolve_occurrence_billing_status()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status = 'BILLING_PENDING' AND EXISTS (
        SELECT 1
          FROM subscription_schema.subscription_invoice invoice
         WHERE invoice.subscription_id = NEW.subscription_id
           AND invoice.status = 'PAID'
           AND invoice.cycle_start <= NEW.service_date
           AND invoice.cycle_end > NEW.service_date
    ) THEN
        NEW.status := 'READY_FOR_ORDER';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_subscription_occurrence_billing_status
BEFORE INSERT ON subscription_schema.subscription_occurrence
FOR EACH ROW
EXECUTE FUNCTION subscription_schema.resolve_occurrence_billing_status();
