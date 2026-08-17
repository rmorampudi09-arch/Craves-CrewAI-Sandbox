CREATE TABLE order_schema.launch_policy (
    id UUID PRIMARY KEY,
    policy_name VARCHAR(120) NOT NULL,
    minimum_order_amount NUMERIC(12,2) NOT NULL CHECK (minimum_order_amount >= 0),
    maximum_serviceability_radius_meters INTEGER NOT NULL CHECK (maximum_serviceability_radius_meters > 0),
    cancellation_cutoff_minutes INTEGER NOT NULL CHECK (cancellation_cutoff_minutes >= 0),
    delivery_sla_minutes INTEGER NOT NULL CHECK (delivery_sla_minutes > 0),
    currency CHAR(3) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_by_identity_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at TIMESTAMPTZ,
    CONSTRAINT chk_launch_policy_activation CHECK (
        (active = FALSE AND activated_at IS NULL) OR
        (active = TRUE AND activated_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX ux_launch_policy_single_active
    ON order_schema.launch_policy (active)
    WHERE active = TRUE;

CREATE TABLE order_schema.launch_policy_audit (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL REFERENCES order_schema.launch_policy(id),
    actor_identity_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL,
    previous_policy_id UUID,
    reason VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_launch_policy_audit_policy_created
    ON order_schema.launch_policy_audit (policy_id, created_at DESC);
