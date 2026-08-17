#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MIGRATION="$ROOT/services/auth-service/src/main/resources/db/migration/V6__internal_admin_rbac.sql"
CONTROLLER="$ROOT/services/auth-service/src/main/java/in/craves/auth/admin/InternalAdminRoleController.java"
REPOSITORY="$ROOT/services/auth-service/src/main/java/in/craves/auth/admin/InternalAdminRoleRepository.java"
STAFF_GRANT_SERVICE="$ROOT/services/auth-service/src/main/java/in/craves/auth/admin/StaffRoleGrantService.java"
ROLE_CATALOG="$ROOT/services/auth-service/src/main/java/in/craves/auth/admin/InternalAdminRoles.java"
APIM_POLICY="$ROOT/infra/apim/internal-admin-rbac/authenticated-policy.xml"
APIM_CONFIG="$ROOT/scripts/apim/configure-internal-admin-rbac-apim.sh"
APIM_STATUS="$ROOT/scripts/apim/status-internal-admin-rbac-apim.sh"
APIM_ROLLBACK="$ROOT/scripts/apim/rollback-internal-admin-rbac-apim.sh"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

for file in "$MIGRATION" "$CONTROLLER" "$REPOSITORY" "$STAFF_GRANT_SERVICE" "$ROLE_CATALOG"; do
  [[ -s "$file" ]] || fail "missing internal-admin RBAC file: $file"
done

for file in "$APIM_POLICY" "$APIM_CONFIG" "$APIM_STATUS" "$APIM_ROLLBACK"; do
  [[ -s "$file" ]] || fail "missing internal-admin APIM file: $file"
done

roles=(
  PLATFORM_ADMIN SUPPORT_ADMIN PAYMENTS_ADMIN OPERATIONS_ADMIN CHEF_ADMIN
  COMPLIANCE_ADMIN SUBSCRIPTION_ADMIN NOTIFICATION_ADMIN AUDIT_ADMIN
)

for role in "${roles[@]}"; do
  grep -Fq "$role" "$MIGRATION" || fail "migration does not seed $role"
  grep -Fq "$role" "$ROLE_CATALOG" || fail "role catalog does not define $role"
done

grep -Fq "WHERE role_code = 'ADMIN'" "$MIGRATION" \
  || fail 'legacy ADMIN identities are not backfilled to PLATFORM_ADMIN'
grep -Fq 'auth_internal_role_change_audit' "$MIGRATION" \
  || fail 'role-change audit table is missing'
grep -Fq 'pg_advisory_xact_lock' "$REPOSITORY" \
  || fail 'role changes are not serialized'
grep -Fq 'The last active platform administrator cannot be removed' "$REPOSITORY" \
  || fail 'last-platform-admin protection is missing'
grep -Fq "revoke_reason = CASE WHEN revoked_at IS NULL THEN 'ADMIN_ROLE_CHANGE'" "$REPOSITORY" \
  || fail 'refresh sessions are not revoked by role changes'
grep -Fq 'token_version = token_version + 1' "$REPOSITORY" \
  || fail 'role changes do not invalidate access-token versions'
grep -Fq 'expectedTargetTokenVersion' "$REPOSITORY" \
  || fail 'stale role replacement protection is missing'

grep -Fq '@PutMapping("/staff-role-grants")' "$CONTROLLER" \
  || fail 'platform-admin staff-role grant endpoint is missing'
grep -Fq 'pg_advisory_xact_lock' "$STAFF_GRANT_SERVICE" \
  || fail 'staff role grants are not serialized with administrator role changes'
grep -Fq "'STAFF_ROLES_GRANTED'" "$STAFF_GRANT_SERVICE" \
  || fail 'staff role grant auth audit is missing'
grep -Fq "'STAFF_ROLE_GRANT'" "$STAFF_GRANT_SERVICE" \
  || fail 'staff role grants do not revoke refresh sessions'
grep -Fq 'token_version = token_version + 1' "$STAFF_GRANT_SERVICE" \
  || fail 'staff role grants do not invalidate access-token versions'
if grep -Fq 'DELETE FROM auth_identity_role' "$STAFF_GRANT_SERVICE"; then
  fail 'staff role grant endpoint must remain grant-only and non-destructive'
fi

bash -n "$APIM_CONFIG"
bash -n "$APIM_STATUS"
bash -n "$APIM_ROLLBACK"
python3 - "$APIM_POLICY" <<'PY'
import sys
import xml.etree.ElementTree as ET
ET.parse(sys.argv[1])
PY
grep -Fq 'CONFIRM_APIM_WRITE' "$APIM_CONFIG" || fail 'APIM write confirmation is missing'
grep -Fq 'put-internal-admin-user-roles' "$APIM_CONFIG" || fail 'APIM role mutation is missing'
grep -Fq 'put-internal-admin-staff-role-grants' "$APIM_CONFIG" || fail 'APIM staff role grant operation is missing'
grep -Fq 'put-internal-admin-staff-role-grants' "$APIM_STATUS" || fail 'APIM staff role grant status check is missing'
grep -Fq 'put-internal-admin-staff-role-grants' "$APIM_ROLLBACK" || fail 'APIM staff role grant rollback coverage is missing'
grep -Fq 'EXPECTED_METHOD' "$APIM_CONFIG" || fail 'APIM method read-back is missing'
grep -Fq 'EXPECTED_TEMPLATE' "$APIM_CONFIG" || fail 'APIM path read-back is missing'
grep -Fq 'Bearer' "$APIM_POLICY" || fail 'APIM Bearer precheck is missing'
grep -Fq 'no-store' "$APIM_POLICY" || fail 'APIM no-store response policy is missing'
if grep -En 'set-backend-service[[:space:]]+backend-id=|set-header name="Authorization"' "$APIM_POLICY"; then
  fail 'unsafe APIM policy behavior detected'
fi

grep -Fq 'CRAVES_INTERNAL_ADMIN_RBAC_API_ENABLED:false' \
  "$ROOT/services/auth-service/src/main/resources/application.yml" \
  || fail 'internal-admin API does not default false'
grep -Fq 'CRAVES_INTERNAL_ADMIN_RBAC_API_ENABLED' \
  "$ROOT/config/production/backend-completion-pack.json" \
  || fail 'backend completion pack does not declare internal-admin safe default'

grep -Fq '"PLATFORM_ADMIN", "CHEF_ADMIN", "COMPLIANCE_ADMIN", "AUDIT_ADMIN"' \
  "$ROOT/services/user-chef-service/src/main/java/in/craves/userchef/service/ChefApplicationService.java" \
  || fail 'chef review read-role mapping is incomplete'
grep -Fq '"PLATFORM_ADMIN", "CHEF_ADMIN"' \
  "$ROOT/services/user-chef-service/src/main/java/in/craves/userchef/service/ChefApplicationService.java" \
  || fail 'chef decision role mapping is incomplete'
grep -Fq 'Only pending chef applications can be rejected' \
  "$ROOT/services/user-chef-service/src/main/java/in/craves/userchef/service/ChefApplicationService.java" \
  || fail 'chef rejection does not preserve the pending-state transition guard'
grep -Fq '"PLATFORM_ADMIN", "SUBSCRIPTION_ADMIN"' \
  "$ROOT/services/subscription-service/src/main/java/in/craves/subscription/service/SubscriptionService.java" \
  || fail 'subscription role mapping is incomplete'
grep -Fq '"PLATFORM_ADMIN", "OPERATIONS_ADMIN"' \
  "$ROOT/services/order-service/src/main/java/in/craves/order/launchpolicy/LaunchPolicyService.java" \
  || fail 'launch-policy role mapping is incomplete'
grep -Fq '"PLATFORM_ADMIN", "PAYMENTS_ADMIN"' \
  "$ROOT/services/integration-service/src/main/java/in/craves/integration/settlement/ChefFinancialService.java" \
  || fail 'payments role mapping is incomplete'
grep -Fq '"PLATFORM_ADMIN", "NOTIFICATION_ADMIN"' \
  "$ROOT/services/notification-service/src/main/java/in/craves/notification/recovery/AdminNotificationRecoveryController.java" \
  || fail 'notification role mapping is incomplete'

if grep -R -n -F 'hasRole("ADMIN")' \
  "$ROOT/services/auth-service/src/main/java/in/craves/auth/admin" \
  "$ROOT/services/user-chef-service/src/main/java/in/craves/userchef/service/ChefApplicationService.java" \
  "$ROOT/services/user-chef-service/src/main/java/in/craves/userchef/service/ChefDocumentReviewService.java" \
  "$ROOT/services/order-service/src/main/java/in/craves/order/admin" \
  "$ROOT/services/order-service/src/main/java/in/craves/order/launchpolicy/LaunchPolicyService.java" \
  "$ROOT/services/integration-service/src/main/java/in/craves/integration/admin" \
  "$ROOT/services/integration-service/src/main/java/in/craves/integration/settlement/ChefFinancialService.java" \
  "$ROOT/services/notification-service/src/main/java/in/craves/notification/recovery"; then
  fail 'a sensitive administrative operation still authorizes the broad legacy ADMIN role'
fi

if grep -R -En 'apps/customer-web|apps/mobile|NEXT_PUBLIC|React Native' \
  "$CONTROLLER" "$REPOSITORY" "$STAFF_GRANT_SERVICE" "$ROLE_CATALOG" "$MIGRATION"; then
  fail 'backend RBAC module contains web or mobile scope'
fi

echo 'SUCCESS: internal administrator RBAC contracts passed.'
