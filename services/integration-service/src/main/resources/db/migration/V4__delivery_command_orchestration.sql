-- Delivery command scheduling metadata and processing leases.
-- Existing V2 tables remain authoritative; this migration only adds the fields needed
-- for Azure Service Bus scheduled enqueue, idempotent event intake and crash recovery.

ALTER TABLE delivery_schema.delivery_command
    ADD COLUMN IF NOT EXISTS source_event_id UUID,
    ADD COLUMN IF NOT EXISTS scheduled_sequence_number BIGINT,
    ADD COLUMN IF NOT EXISTS service_bus_message_id VARCHAR(200),
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMPTZ;

ALTER TABLE delivery_schema.delivery_outbox
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS ux_delivery_command_source_event
    ON delivery_schema.delivery_command (source_event_id)
    WHERE source_event_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_delivery_command_processing_lease
    ON delivery_schema.delivery_command (status, processing_started_at)
    WHERE status = 'PROCESSING';

CREATE INDEX IF NOT EXISTS ix_delivery_outbox_publish_due
    ON delivery_schema.delivery_outbox (status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX IF NOT EXISTS ix_delivery_outbox_processing_lease
    ON delivery_schema.delivery_outbox (status, processing_started_at)
    WHERE status = 'PROCESSING';
