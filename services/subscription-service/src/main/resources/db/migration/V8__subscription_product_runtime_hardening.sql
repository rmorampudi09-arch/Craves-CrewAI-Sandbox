ALTER TABLE subscription_schema.customer_subscription
    ADD COLUMN enrollment_idempotency_key VARCHAR(128);

CREATE UNIQUE INDEX ux_customer_subscription_enrollment_idempotency
    ON subscription_schema.customer_subscription (customer_identity_id, enrollment_idempotency_key)
    WHERE enrollment_idempotency_key IS NOT NULL;

ALTER TABLE subscription_schema.subscription_occurrence_history
    ADD COLUMN actor_identity_id UUID,
    ADD COLUMN source VARCHAR(40);

CREATE TABLE subscription_schema.subscription_plan_policy (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES subscription_schema.subscription_plan(id),
    version INTEGER NOT NULL CHECK (version > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    customer_pause_enabled BOOLEAN NOT NULL,
    customer_resume_enabled BOOLEAN NOT NULL,
    customer_cancel_enabled BOOLEAN NOT NULL,
    customer_skip_enabled BOOLEAN NOT NULL,
    pause_cutoff_minutes INTEGER,
    resume_lead_minutes INTEGER,
    cancel_cutoff_minutes INTEGER,
    skip_cutoff_minutes INTEGER,
    holiday_policy_reference VARCHAR(200),
    unused_meal_policy_reference VARCHAR(200),
    refund_policy_reference VARCHAR(200),
    notes TEXT,
    created_by_identity_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at TIMESTAMPTZ,
    CONSTRAINT ux_subscription_plan_policy_version UNIQUE (plan_id, version),
    CONSTRAINT ck_subscription_plan_policy_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_subscription_plan_policy_pause_cutoff CHECK (pause_cutoff_minutes IS NULL OR pause_cutoff_minutes >= 0),
    CONSTRAINT ck_subscription_plan_policy_resume_lead CHECK (resume_lead_minutes IS NULL OR resume_lead_minutes >= 0),
    CONSTRAINT ck_subscription_plan_policy_cancel_cutoff CHECK (cancel_cutoff_minutes IS NULL OR cancel_cutoff_minutes >= 0),
    CONSTRAINT ck_subscription_plan_policy_skip_cutoff CHECK (skip_cutoff_minutes IS NULL OR skip_cutoff_minutes >= 0),
    CONSTRAINT ck_subscription_plan_policy_pause_config CHECK (
        customer_pause_enabled = FALSE OR pause_cutoff_minutes IS NOT NULL
    ),
    CONSTRAINT ck_subscription_plan_policy_resume_config CHECK (
        customer_resume_enabled = FALSE OR resume_lead_minutes IS NOT NULL
    ),
    CONSTRAINT ck_subscription_plan_policy_cancel_config CHECK (
        customer_cancel_enabled = FALSE OR cancel_cutoff_minutes IS NOT NULL
    ),
    CONSTRAINT ck_subscription_plan_policy_skip_config CHECK (
        customer_skip_enabled = FALSE OR skip_cutoff_minutes IS NOT NULL
    ),
    CONSTRAINT ck_subscription_plan_policy_activation CHECK (
        (status = 'ACTIVE' AND activated_at IS NOT NULL) OR status <> 'ACTIVE'
    )
);

CREATE UNIQUE INDEX ux_subscription_plan_policy_one_active
    ON subscription_schema.subscription_plan_policy (plan_id)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_subscription_plan_policy_plan_status
    ON subscription_schema.subscription_plan_policy (plan_id, status, version DESC);

CREATE TABLE subscription_schema.subscription_plan_policy_audit (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES subscription_schema.subscription_plan(id),
    policy_id UUID NOT NULL REFERENCES subscription_schema.subscription_plan_policy(id),
    actor_identity_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL,
    policy_version INTEGER NOT NULL,
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_subscription_plan_policy_audit_plan
    ON subscription_schema.subscription_plan_policy_audit (plan_id, created_at DESC);

CREATE TABLE subscription_schema.subscription_skip_request (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES subscription_schema.customer_subscription(id),
    service_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    reason VARCHAR(1000),
    actor_identity_id UUID NOT NULL,
    occurrence_id UUID REFERENCES subscription_schema.subscription_occurrence(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_subscription_skip_request_date UNIQUE (subscription_id, service_date),
    CONSTRAINT ck_subscription_skip_request_status CHECK (status IN ('REQUESTED', 'APPLIED', 'REJECTED')),
    CONSTRAINT ck_subscription_skip_request_applied CHECK (
        (status = 'APPLIED' AND applied_at IS NOT NULL) OR status <> 'APPLIED'
    )
);

CREATE INDEX ix_subscription_skip_request_pending
    ON subscription_schema.subscription_skip_request (subscription_id, service_date)
    WHERE status = 'REQUESTED';

CREATE INDEX ix_subscription_occurrence_subscription_service
    ON subscription_schema.subscription_occurrence (subscription_id, service_date, status);

CREATE INDEX ix_customer_subscription_admin_keyset
    ON subscription_schema.customer_subscription (created_at DESC, id DESC);

CREATE INDEX ix_customer_subscription_admin_status_keyset
    ON subscription_schema.customer_subscription (status, created_at DESC, id DESC);
