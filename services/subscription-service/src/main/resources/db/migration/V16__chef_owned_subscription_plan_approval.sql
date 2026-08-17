ALTER TABLE subscription_schema.subscription_plan
    DROP CONSTRAINT IF EXISTS ck_subscription_plan_status;

ALTER TABLE subscription_schema.subscription_plan
    ADD CONSTRAINT ck_subscription_plan_status CHECK (
        status IN ('DRAFT', 'PENDING_APPROVAL', 'ACTIVE', 'REJECTED', 'INACTIVE')
    );

ALTER TABLE subscription_schema.subscription_plan
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reviewed_by_identity_id UUID,
    ADD COLUMN IF NOT EXISTS review_reason VARCHAR(1000);

ALTER TABLE subscription_schema.subscription_plan_audit
    ADD COLUMN IF NOT EXISTS reason VARCHAR(1000);

CREATE INDEX IF NOT EXISTS ix_subscription_plan_review_queue
    ON subscription_schema.subscription_plan (status, submitted_at, created_at)
    WHERE status = 'PENDING_APPROVAL';
