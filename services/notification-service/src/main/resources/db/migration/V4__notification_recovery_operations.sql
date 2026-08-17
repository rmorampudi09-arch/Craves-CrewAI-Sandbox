CREATE TABLE notification_schema.notification_recovery_audit (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES notification_schema.notification_request(id),
    actor_identity_id UUID NOT NULL,
    previous_status VARCHAR(32) NOT NULL,
    previous_attempt_count INTEGER NOT NULL,
    action VARCHAR(40) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_recovery_action CHECK (action = 'REQUEUE'),
    CONSTRAINT ck_notification_recovery_reason CHECK (char_length(trim(reason)) BETWEEN 10 AND 500)
);

CREATE INDEX ix_notification_recovery_request
    ON notification_schema.notification_recovery_audit (request_id, created_at DESC);

CREATE INDEX ix_notification_recovery_actor
    ON notification_schema.notification_recovery_audit (actor_identity_id, created_at DESC);

COMMENT ON TABLE notification_schema.notification_recovery_audit IS
    'Append-only ADMIN evidence for explicit notification request requeue operations.';
