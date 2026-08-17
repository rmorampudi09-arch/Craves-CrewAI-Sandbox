CREATE OR REPLACE FUNCTION normalize_account_intervention_audit_action()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.action = 'ACCOUNT_REACTIVATEED' THEN
        NEW.action := 'ACCOUNT_REACTIVATED';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_normalize_account_intervention_audit_action ON auth_audit;

CREATE TRIGGER trg_normalize_account_intervention_audit_action
BEFORE INSERT OR UPDATE OF action ON auth_audit
FOR EACH ROW
EXECUTE FUNCTION normalize_account_intervention_audit_action();

COMMENT ON FUNCTION normalize_account_intervention_audit_action() IS
    'Normalizes the deterministic admin intervention audit action label without changing historical account state.';
