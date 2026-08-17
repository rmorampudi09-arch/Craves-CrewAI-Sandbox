CREATE TABLE payment_schema.admin_investigation_audit (
    id UUID PRIMARY KEY,
    actor_identity_id UUID NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id UUID NOT NULL,
    action VARCHAR(80) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_integration_admin_investigation_reason CHECK (char_length(trim(reason)) BETWEEN 10 AND 500)
);

CREATE INDEX ix_integration_admin_investigation_resource
    ON payment_schema.admin_investigation_audit (resource_type, resource_id, created_at DESC);

CREATE INDEX ix_integration_admin_investigation_actor
    ON payment_schema.admin_investigation_audit (actor_identity_id, created_at DESC);

COMMENT ON TABLE payment_schema.admin_investigation_audit IS
    'Append-only audit of authenticated ADMIN access to payment, refund and delivery operational views.';
