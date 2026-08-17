CREATE TABLE auth_role (
    code        VARCHAR(32) PRIMARY KEY,
    description VARCHAR(255) NOT NULL
);

INSERT INTO auth_role (code, description)
VALUES
    ('CUSTOMER', 'Customer account role'),
    ('CHEF', 'Approved chef account role'),
    ('ADMIN', 'Craves administration role')
ON CONFLICT (code) DO NOTHING;

CREATE TABLE auth_identity (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid    VARCHAR(128) NOT NULL UNIQUE,
    phone_number    VARCHAR(32) NOT NULL UNIQUE,
    email           VARCHAR(320),
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    display_name    VARCHAR(160),
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    token_version   BIGINT NOT NULL DEFAULT 1,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_auth_identity_phone_number ON auth_identity (phone_number);
CREATE INDEX idx_auth_identity_email ON auth_identity (email);
CREATE INDEX idx_auth_identity_status ON auth_identity (status);

CREATE TABLE auth_identity_role (
    identity_id UUID NOT NULL REFERENCES auth_identity(id) ON DELETE CASCADE,
    role_code   VARCHAR(32) NOT NULL REFERENCES auth_role(code),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (identity_id, role_code)
);

CREATE TABLE refresh_session (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    identity_id          UUID NOT NULL REFERENCES auth_identity(id) ON DELETE CASCADE,
    refresh_token_hash   VARCHAR(128) NOT NULL UNIQUE,
    user_agent           VARCHAR(512),
    ip_address           VARCHAR(64),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at         TIMESTAMPTZ,
    expires_at           TIMESTAMPTZ NOT NULL,
    revoked_at           TIMESTAMPTZ,
    replaced_by_session_id UUID,
    revoke_reason        VARCHAR(160)
);

CREATE INDEX idx_refresh_session_identity_id ON refresh_session (identity_id);
CREATE INDEX idx_refresh_session_expires_at ON refresh_session (expires_at);
CREATE INDEX idx_refresh_session_revoked_at ON refresh_session (revoked_at);

CREATE TABLE login_attempt (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid   VARCHAR(128),
    phone_number   VARCHAR(32),
    success        BOOLEAN NOT NULL,
    failure_code   VARCHAR(80),
    ip_address     VARCHAR(64),
    user_agent     VARCHAR(512),
    attempted_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_login_attempt_phone_time ON login_attempt (phone_number, attempted_at DESC);
CREATE INDEX idx_login_attempt_firebase_uid_time ON login_attempt (firebase_uid, attempted_at DESC);

CREATE TABLE auth_audit (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    identity_id      UUID,
    action          VARCHAR(80) NOT NULL,
    actor_identity_id UUID,
    details         TEXT,
    correlation_id  VARCHAR(80),
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_auth_audit_identity_time ON auth_audit (identity_id, created_at DESC);
CREATE INDEX idx_auth_audit_action_time ON auth_audit (action, created_at DESC);
