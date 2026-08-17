-- Durable recovery state for temporary delivery-provider outages.
-- Provider unavailability must not consume the normal delivery-command retry budget.

ALTER TABLE delivery_schema.delivery_command
    ADD COLUMN IF NOT EXISTS provider_wait_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS provider_wait_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS next_provider_retry_at TIMESTAMPTZ;

ALTER TABLE delivery_schema.delivery_command
    DROP CONSTRAINT IF EXISTS ck_delivery_command_status;

ALTER TABLE delivery_schema.delivery_command
    ADD CONSTRAINT ck_delivery_command_status CHECK (
        status IN (
            'SCHEDULED',
            'PROCESSING',
            'COMPLETED',
            'FAILED',
            'WAITING_FOR_PROVIDER',
            'RECONCILIATION_PENDING',
            'DEAD_LETTER'
        )
    );

ALTER TABLE delivery_schema.delivery_command
    DROP CONSTRAINT IF EXISTS ck_delivery_command_provider_wait_attempts;

ALTER TABLE delivery_schema.delivery_command
    ADD CONSTRAINT ck_delivery_command_provider_wait_attempts CHECK (
        provider_wait_attempt_count >= 0
    );

ALTER TABLE delivery_schema.delivery_command
    DROP CONSTRAINT IF EXISTS ck_delivery_command_provider_wait_identity;

ALTER TABLE delivery_schema.delivery_command
    ADD CONSTRAINT ck_delivery_command_provider_wait_identity CHECK (
        status <> 'WAITING_FOR_PROVIDER'
        OR (
            provider_wait_started_at IS NOT NULL
            AND next_provider_retry_at IS NOT NULL
        )
    );

CREATE INDEX IF NOT EXISTS ix_delivery_command_provider_wait_due
    ON delivery_schema.delivery_command (
        status,
        next_provider_retry_at,
        provider_wait_started_at
    )
    WHERE status = 'WAITING_FOR_PROVIDER';
