-- Durable delivery webhook processing, out-of-order protection and tracking reconciliation.
-- All runtime workers remain disabled by default; this migration only adds backward-compatible state.

ALTER TABLE delivery_schema.delivery_webhook_inbox
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS delivery_job_id UUID REFERENCES delivery_schema.delivery_job(id),
    ADD COLUMN IF NOT EXISTS provider_order_id VARCHAR(200),
    ADD COLUMN IF NOT EXISTS provider_delivery_id VARCHAR(200),
    ADD COLUMN IF NOT EXISTS normalized_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS processing_result VARCHAR(120);

ALTER TABLE delivery_schema.delivery_webhook_inbox
    DROP CONSTRAINT IF EXISTS ck_delivery_webhook_status;

ALTER TABLE delivery_schema.delivery_webhook_inbox
    ADD CONSTRAINT ck_delivery_webhook_status CHECK (
        processing_status IN (
            'RECEIVED',
            'PROCESSING',
            'PROCESSED',
            'DUPLICATE',
            'REJECTED',
            'FAILED',
            'DEAD_LETTER'
        )
    );

ALTER TABLE delivery_schema.delivery_webhook_inbox
    DROP CONSTRAINT IF EXISTS ck_delivery_webhook_attempts;

ALTER TABLE delivery_schema.delivery_webhook_inbox
    ADD CONSTRAINT ck_delivery_webhook_attempts CHECK (attempt_count >= 0);

CREATE INDEX IF NOT EXISTS ix_delivery_webhook_process_due
    ON delivery_schema.delivery_webhook_inbox
        (processing_status, next_attempt_at, received_at)
    WHERE processing_status IN ('RECEIVED', 'FAILED', 'PROCESSING');

ALTER TABLE delivery_schema.delivery_job
    ADD COLUMN IF NOT EXISTS provider_status VARCHAR(120),
    ADD COLUMN IF NOT EXISTS last_status_observed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_status_source VARCHAR(30),
    ADD COLUMN IF NOT EXISTS next_tracking_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS tracking_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS tracking_processing_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS tracking_dead_lettered_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_tracking_error TEXT;

-- Existing rows receive audit metadata only. They are deliberately not scheduled for polling.
-- A fresh webhook, an explicit operational repair, or a job created after V102 may schedule track().
UPDATE delivery_schema.delivery_job
SET provider_status = COALESCE(provider_status, status),
    last_status_observed_at = COALESCE(last_status_observed_at, booked_at, created_at),
    last_status_source = COALESCE(last_status_source, 'CREATE')
WHERE provider_status IS NULL
   OR last_status_observed_at IS NULL
   OR last_status_source IS NULL;

ALTER TABLE delivery_schema.delivery_job
    DROP CONSTRAINT IF EXISTS ck_delivery_job_tracking_attempts;

ALTER TABLE delivery_schema.delivery_job
    ADD CONSTRAINT ck_delivery_job_tracking_attempts CHECK (tracking_attempt_count >= 0);

ALTER TABLE delivery_schema.delivery_job
    DROP CONSTRAINT IF EXISTS ck_delivery_job_status_source;

ALTER TABLE delivery_schema.delivery_job
    ADD CONSTRAINT ck_delivery_job_status_source CHECK (
        last_status_source IS NULL
        OR last_status_source IN ('CREATE', 'WEBHOOK', 'TRACK', 'RECONCILIATION')
    );

CREATE INDEX IF NOT EXISTS ix_delivery_job_tracking_due
    ON delivery_schema.delivery_job (next_tracking_at, updated_at)
    WHERE next_tracking_at IS NOT NULL
      AND status NOT IN ('DELIVERED', 'CANCELLED', 'RETURNED', 'FAILED');

CREATE INDEX IF NOT EXISTS ix_delivery_job_tracking_dead_letter
    ON delivery_schema.delivery_job (tracking_dead_lettered_at, updated_at)
    WHERE tracking_dead_lettered_at IS NOT NULL;

ALTER TABLE delivery_schema.delivery_event
    ADD COLUMN IF NOT EXISTS source VARCHAR(30) NOT NULL DEFAULT 'WEBHOOK',
    ADD COLUMN IF NOT EXISTS provider_status VARCHAR(120),
    ADD COLUMN IF NOT EXISTS applied BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS ignored_reason VARCHAR(120);

ALTER TABLE delivery_schema.delivery_event
    DROP CONSTRAINT IF EXISTS ck_delivery_event_source;

ALTER TABLE delivery_schema.delivery_event
    ADD CONSTRAINT ck_delivery_event_source CHECK (
        source IN ('WEBHOOK', 'TRACK')
    );

CREATE INDEX IF NOT EXISTS ix_delivery_event_applied_time
    ON delivery_schema.delivery_event (delivery_job_id, applied, occurred_at DESC);
