INSERT INTO auth_role (code, description)
VALUES
    ('PLATFORM_ADMIN', 'Full internal administration and role management'),
    ('SUPPORT_ADMIN', 'Read-only customer support investigations and account status'),
    ('PAYMENTS_ADMIN', 'Payment, refund, earnings and settlement operations'),
    ('OPERATIONS_ADMIN', 'Order, delivery and launch-policy operations'),
    ('CHEF_ADMIN', 'Chef application decisions and onboarding operations'),
    ('COMPLIANCE_ADMIN', 'Chef KYC and compliance document review'),
    ('SUBSCRIPTION_ADMIN', 'Subscription plan, schedule and lifecycle operations'),
    ('NOTIFICATION_ADMIN', 'Notification delivery recovery operations'),
    ('AUDIT_ADMIN', 'Read-only operational and internal-role audit access')
ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description;

-- Preserve every existing administrator while moving authorization to least-privilege roles.
-- The legacy ADMIN role remains only as a backoffice-shell compatibility marker.
INSERT INTO auth_identity_role (identity_id, role_code, created_at)
SELECT identity_id, 'PLATFORM_ADMIN', now()
  FROM auth_identity_role
 WHERE role_code = 'ADMIN'
ON CONFLICT (identity_id, role_code) DO NOTHING;

CREATE TABLE auth_internal_role_change_audit (
    id UUID PRIMARY KEY,
    target_identity_id UUID NOT NULL REFERENCES auth_identity(id),
    actor_identity_id UUID NOT NULL REFERENCES auth_identity(id),
    previous_roles VARCHAR(1000) NOT NULL,
    new_roles VARCHAR(1000) NOT NULL,
    previous_token_version BIGINT NOT NULL CHECK (previous_token_version > 0),
    new_token_version BIGINT NOT NULL CHECK (new_token_version > 0),
    changed BOOLEAN NOT NULL,
    reason VARCHAR(500) NOT NULL,
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_auth_internal_role_reason CHECK (char_length(trim(reason)) BETWEEN 10 AND 500),
    CONSTRAINT ck_auth_internal_role_version CHECK (new_token_version >= previous_token_version)
);

CREATE INDEX ix_auth_internal_role_change_target
    ON auth_internal_role_change_audit (target_identity_id, created_at DESC);
CREATE INDEX ix_auth_internal_role_change_actor
    ON auth_internal_role_change_audit (actor_identity_id, created_at DESC);
CREATE INDEX ix_auth_internal_role_change_correlation
    ON auth_internal_role_change_audit (correlation_id);

COMMENT ON TABLE auth_internal_role_change_audit IS
    'Append-only evidence for exact-set internal administrator role replacements.';
