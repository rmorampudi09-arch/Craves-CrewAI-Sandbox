ALTER TABLE notification_schema.notification_request
    ADD COLUMN IF NOT EXISTS lock_token UUID,
    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS provider_message_id VARCHAR(200),
    ADD COLUMN IF NOT EXISTS sent_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_notification_request_delivery_due
    ON notification_schema.notification_request (channel, status, next_attempt_at, priority, created_at)
    WHERE status IN ('PENDING', 'FAILED', 'PROCESSING');

CREATE TABLE notification_schema.push_device_registration (
    id UUID PRIMARY KEY,
    recipient_identity_id UUID NOT NULL,
    platform VARCHAR(20) NOT NULL,
    device_token TEXT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    app_instance_id VARCHAR(160),
    app_version VARCHAR(40),
    active BOOLEAN NOT NULL DEFAULT true,
    failure_count INTEGER NOT NULL DEFAULT 0 CHECK (failure_count >= 0),
    last_failure_code VARCHAR(120),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    disabled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_push_device_platform CHECK (platform IN ('ANDROID', 'IOS'))
);

CREATE INDEX idx_push_device_identity_active
    ON notification_schema.push_device_registration (recipient_identity_id, active, last_seen_at DESC);

CREATE TABLE notification_schema.channel_delivery_dead_letter (
    id UUID PRIMARY KEY,
    notification_request_id UUID NOT NULL UNIQUE REFERENCES notification_schema.notification_request(id),
    channel VARCHAR(24) NOT NULL,
    final_error_code VARCHAR(120),
    final_error_message VARCHAR(1000),
    attempt_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE notification_schema.notification_preference
    ADD COLUMN IF NOT EXISTS updated_by_identity_id UUID;
