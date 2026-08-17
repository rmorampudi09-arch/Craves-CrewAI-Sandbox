ALTER TABLE auth_admin_intervention
    DROP CONSTRAINT IF EXISTS ck_auth_admin_intervention_provider_status;

ALTER TABLE auth_admin_intervention
    ADD CONSTRAINT ck_auth_admin_intervention_provider_status CHECK (
        provider_status IN (
            'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'DEAD_LETTER', 'SUPERSEDED'
        )
    );

-- The provider worker is disabled before this migration is deployed. Normalizing any
-- historical processing lease prevents an interrupted old revision from blocking the
-- identity-level uniqueness rule below.
UPDATE auth_admin_intervention
   SET provider_status = 'FAILED',
       provider_lock_token = NULL,
       provider_locked_at = NULL,
       provider_next_attempt_at = now(),
       provider_last_error = COALESCE(
           provider_last_error,
           'Recovered during identity-serialized provider migration'
       ),
       updated_at = now()
 WHERE provider_status = 'PROCESSING';

CREATE UNIQUE INDEX ux_auth_admin_intervention_one_processing_per_identity
    ON auth_admin_intervention (identity_id)
    WHERE provider_status = 'PROCESSING';

CREATE INDEX ix_auth_admin_intervention_identity_provider_due
    ON auth_admin_intervention (
        identity_id,
        provider_status,
        provider_next_attempt_at,
        created_at DESC
    )
    WHERE provider_status IN ('PENDING', 'FAILED', 'PROCESSING');

COMMENT ON INDEX ux_auth_admin_intervention_one_processing_per_identity IS
    'Prevents concurrent Firebase account mutations for the same Craves identity across worker replicas.';
