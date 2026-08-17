CREATE TABLE IF NOT EXISTS subscription_schema.subscription_plan_audit (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES subscription_schema.subscription_plan(id),
    actor_identity_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL,
    old_status VARCHAR(40),
    new_status VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_subscription_plan_audit_plan_created
    ON subscription_schema.subscription_plan_audit(plan_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_subscription_plan_audit_actor_created
    ON subscription_schema.subscription_plan_audit(actor_identity_id, created_at DESC);
