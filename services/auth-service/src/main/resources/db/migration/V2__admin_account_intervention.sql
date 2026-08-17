CREATE TABLE auth_admin_intervention (
    id UUID PRIMARY KEY,
    identity_id UUID NOT NULL REFERENCES auth_identity(id),
    action VARCHAR(32) NOT NULL,
    requested_status VARCHAR(32) NOT NULL,
    previous_status VARCHAR(32) NOT NULL,
    actor_identity_id UUID NOT NULL REFERENCES auth_identity(id),
    reason VARCHAR(500) NOT NULL,
    correlation_id UUID NOT NULL,
    provider_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    provider_attempt_count INTEGER NOT NULL DEFAULT 0,
    provider_next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    provider_lock_token UUID,
    provider_locked_at TIMESTAMPTZ,
    provider_completed_at TIMESTAMPTZ,
    provider_last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_auth_admin_intervention_action CHECK (action IN ('SUSPEND', 'REACTIVATE')),
    CONSTRAINT ck_auth_admin_intervention_status CHECK (requested_status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_auth_admin_intervention_reason CHECK (char_length(trim(reason)) BETWEEN 10 AND 500),
    CONSTRAINT ck_auth_admin_intervention_provider_status CHECK (
        provider_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'DEAD_LETTER')
    )
);

CREATE INDEX ix_auth_admin_intervention_identity
    ON auth_admin_intervention (identity_id, created_at DESC);

CREATE INDEX ix_auth_admin_intervention_provider_due
    ON auth_admin_intervention (provider_status, provider_next_attempt_at, created_at)
    WHERE provider_status IN ('PENDING', 'FAILED', 'PROCESSING');

COMMENT ON TABLE auth_admin_intervention IS
    'Durable audited account suspension/reactivation requests. Firebase execution is asynchronous and fail-closed.';
