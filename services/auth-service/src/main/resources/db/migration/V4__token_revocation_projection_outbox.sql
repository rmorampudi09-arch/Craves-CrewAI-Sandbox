CREATE TABLE auth_token_revocation_outbox (
    id UUID PRIMARY KEY,
    identity_id UUID NOT NULL REFERENCES auth_identity(id),
    account_status VARCHAR(32) NOT NULL,
    minimum_token_version BIGINT NOT NULL CHECK (minimum_token_version > 0),
    event_key VARCHAR(160) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lock_token UUID,
    locked_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_auth_token_revocation_outbox_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'DEAD_LETTER')
    )
);

CREATE INDEX ix_auth_token_revocation_outbox_due
    ON auth_token_revocation_outbox (status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FAILED', 'PROCESSING');

CREATE OR REPLACE FUNCTION enqueue_auth_token_revocation_projection()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status
       OR NEW.token_version IS DISTINCT FROM OLD.token_version THEN
        INSERT INTO auth_token_revocation_outbox (
            id, identity_id, account_status, minimum_token_version,
            event_key, status, next_attempt_at, created_at, updated_at
        ) VALUES (
            gen_random_uuid(), NEW.id, NEW.status, NEW.token_version,
            NEW.id::text || ':' || NEW.token_version::text,
            'PENDING', now(), now(), now()
        )
        ON CONFLICT (event_key) DO UPDATE
            SET account_status = EXCLUDED.account_status,
                status = 'PENDING',
                attempt_count = 0,
                next_attempt_at = now(),
                lock_token = NULL,
                locked_at = NULL,
                last_error = NULL,
                updated_at = now();
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_enqueue_auth_token_revocation_projection ON auth_identity;

CREATE TRIGGER trg_enqueue_auth_token_revocation_projection
AFTER UPDATE OF status, token_version ON auth_identity
FOR EACH ROW
EXECUTE FUNCTION enqueue_auth_token_revocation_projection();

INSERT INTO auth_token_revocation_outbox (
    id, identity_id, account_status, minimum_token_version,
    event_key, status, next_attempt_at, created_at, updated_at
)
SELECT gen_random_uuid(), identity.id, identity.status, identity.token_version,
       identity.id::text || ':' || identity.token_version::text,
       'PENDING', now(), now(), now()
  FROM auth_identity identity
 WHERE identity.status <> 'ACTIVE'
ON CONFLICT (event_key) DO NOTHING;

COMMENT ON TABLE auth_token_revocation_outbox IS
    'Durable projection source for short-lived Redis account-status and minimum-token-version keys.';
