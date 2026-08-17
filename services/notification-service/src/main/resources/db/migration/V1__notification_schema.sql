CREATE SCHEMA IF NOT EXISTS notification_schema;

CREATE TABLE IF NOT EXISTS notification_schema.notification_template (
    code                VARCHAR(80) PRIMARY KEY,
    channel             VARCHAR(24) NOT NULL,
    locale              VARCHAR(16) NOT NULL DEFAULT 'en-IN',
    subject_template    TEXT,
    body_template       TEXT NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    version             INTEGER NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS notification_schema.notification_request (
    id                      UUID PRIMARY KEY,
    request_key             VARCHAR(160) NOT NULL UNIQUE,
    source_service          VARCHAR(80) NOT NULL,
    event_type              VARCHAR(80) NOT NULL,
    recipient_identity_id   UUID NOT NULL,
    recipient_role          VARCHAR(40),
    channel                 VARCHAR(24) NOT NULL,
    template_code           VARCHAR(80),
    delivery_address        VARCHAR(320),
    title                   VARCHAR(240),
    body                    TEXT NOT NULL,
    target_type             VARCHAR(80),
    target_id               UUID,
    payload                 JSONB NOT NULL DEFAULT '{}'::jsonb,
    priority                INTEGER NOT NULL DEFAULT 5,
    status                  VARCHAR(32) NOT NULL,
    attempt_count           INTEGER NOT NULL DEFAULT 0,
    next_attempt_at         TIMESTAMPTZ,
    last_error              TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notification_request_recipient_status
    ON notification_schema.notification_request (recipient_identity_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notification_request_next_attempt
    ON notification_schema.notification_request (status, next_attempt_at);

CREATE TABLE IF NOT EXISTS notification_schema.notification_delivery_attempt (
    id                      UUID PRIMARY KEY,
    request_id              UUID NOT NULL REFERENCES notification_schema.notification_request(id),
    channel                 VARCHAR(24) NOT NULL,
    provider                VARCHAR(80),
    provider_message_id     VARCHAR(160),
    attempt_number          INTEGER NOT NULL,
    status                  VARCHAR(32) NOT NULL,
    error_message           TEXT,
    started_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at            TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS notification_schema.in_app_notification (
    id                      UUID PRIMARY KEY,
    request_id              UUID REFERENCES notification_schema.notification_request(id),
    recipient_identity_id   UUID NOT NULL,
    recipient_role          VARCHAR(40),
    title                   VARCHAR(240) NOT NULL,
    body                    TEXT NOT NULL,
    notification_type       VARCHAR(80) NOT NULL,
    target_type             VARCHAR(80),
    target_id               UUID,
    read_at                 TIMESTAMPTZ,
    expires_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_in_app_notification_recipient
    ON notification_schema.in_app_notification (recipient_identity_id, read_at, created_at DESC);

CREATE TABLE IF NOT EXISTS notification_schema.notification_preference (
    recipient_identity_id   UUID NOT NULL,
    channel                 VARCHAR(24) NOT NULL,
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (recipient_identity_id, channel)
);

CREATE TABLE IF NOT EXISTS notification_schema.notification_event_inbox (
    id                      UUID PRIMARY KEY,
    message_id              VARCHAR(160) NOT NULL UNIQUE,
    event_type              VARCHAR(80) NOT NULL,
    payload                 JSONB NOT NULL,
    status                  VARCHAR(32) NOT NULL,
    error_message           TEXT,
    received_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at            TIMESTAMPTZ
);

INSERT INTO notification_schema.notification_template(code, channel, subject_template, body_template)
VALUES
    ('CHEF_APPROVED_IN_APP', 'IN_APP', NULL, 'Your Craves chef profile is approved. You can now publish your kitchen and menu.'),
    ('ORDER_CREATED_IN_APP', 'IN_APP', NULL, 'Your order has been created and is waiting for payment confirmation.'),
    ('PAYMENT_SUCCEEDED_IN_APP', 'IN_APP', NULL, 'Payment received. Your order is waiting for chef acceptance.'),
    ('CHEF_ACCEPTED_ORDER_IN_APP', 'IN_APP', NULL, 'Chef accepted your order and preparation will start soon.'),
    ('DELIVERY_STATUS_CHANGED_IN_APP', 'IN_APP', NULL, 'Your delivery status has been updated.'),
    ('REFUND_COMPLETED_IN_APP', 'IN_APP', NULL, 'Your refund has been completed.')
ON CONFLICT (code) DO NOTHING;
