-- Fail-closed recovery for provider create requests whose responses were not received.
-- The reconciliation worker is disabled by default and performs read-only provider lookups only.

ALTER TABLE delivery_schema.delivery_command
    ADD COLUMN IF NOT EXISTS reconciliation_provider_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS reconciliation_client_reference VARCHAR(200),
    ADD COLUMN IF NOT EXISTS reconciliation_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reconciliation_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reconciliation_processing_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS next_reconciliation_at TIMESTAMPTZ;

ALTER TABLE delivery_schema.delivery_command
    DROP CONSTRAINT IF EXISTS ck_delivery_command_status;

ALTER TABLE delivery_schema.delivery_command
    ADD CONSTRAINT ck_delivery_command_status CHECK (
        status IN (
            'SCHEDULED',
            'PROCESSING',
            'COMPLETED',
            'FAILED',
            'RECONCILIATION_PENDING',
            'DEAD_LETTER'
        )
    );

ALTER TABLE delivery_schema.delivery_command
    DROP CONSTRAINT IF EXISTS ck_delivery_command_reconciliation_attempts;

ALTER TABLE delivery_schema.delivery_command
    ADD CONSTRAINT ck_delivery_command_reconciliation_attempts CHECK (
        reconciliation_attempt_count >= 0
    );

ALTER TABLE delivery_schema.delivery_command
    DROP CONSTRAINT IF EXISTS ck_delivery_command_reconciliation_identity;

ALTER TABLE delivery_schema.delivery_command
    ADD CONSTRAINT ck_delivery_command_reconciliation_identity CHECK (
        status <> 'RECONCILIATION_PENDING'
        OR (
            reconciliation_provider_id IS NOT NULL
            AND reconciliation_client_reference IS NOT NULL
            AND reconciliation_started_at IS NOT NULL
            AND next_reconciliation_at IS NOT NULL
        )
    );

CREATE INDEX IF NOT EXISTS ix_delivery_command_reconciliation_due
    ON delivery_schema.delivery_command (
        status,
        next_reconciliation_at,
        reconciliation_processing_started_at
    )
    WHERE status = 'RECONCILIATION_PENDING';
