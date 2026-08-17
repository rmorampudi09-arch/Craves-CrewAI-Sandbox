#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_CONTROLLER="$ROOT/services/subscription-service/src/main/java/in/craves/subscription/plan/ChefPlanWorkflowController.java"
WORKFLOW_SERVICE="$ROOT/services/subscription-service/src/main/java/in/craves/subscription/plan/ChefPlanWorkflowService.java"
SUBSCRIPTION_SERVICE="$ROOT/services/subscription-service/src/main/java/in/craves/subscription/service/SubscriptionService.java"
DEFAULT_POLICY_SERVICE="$ROOT/services/subscription-service/src/main/java/in/craves/subscription/policy/DefaultSubscriptionPolicyService.java"
POLICY_SERVICE="$ROOT/services/subscription-service/src/main/java/in/craves/subscription/policy/SubscriptionPolicyService.java"
POLICY_REPOSITORY="$ROOT/services/subscription-service/src/main/java/in/craves/subscription/policy/SubscriptionPolicyRepository.java"
SCHEDULE_SERVICE="$ROOT/services/subscription-service/src/main/java/in/craves/subscription/schedule/PlanScheduleService.java"
ADMIN_CONTROLLER="$ROOT/services/subscription-service/src/main/java/in/craves/subscription/web/SubscriptionController.java"
ADMIN_SCHEDULE_CONTROLLER="$ROOT/services/subscription-service/src/main/java/in/craves/subscription/schedule/PlanScheduleController.java"
ADMIN_BFF="$ROOT/apps/customer-web-next/src/app/api/admin/subscription-plans/route.ts"
ADMIN_STATUS_BFF="$ROOT/apps/customer-web-next/src/app/api/admin/subscription-plans/[planId]/status/route.ts"
ADMIN_SCHEDULE_BFF="$ROOT/apps/customer-web-next/src/app/api/admin/subscription-plans/[planId]/schedule/route.ts"
ADMIN_RUNTIME_UI="$ROOT/apps/customer-web-next/src/components/admin-subscription-runtime-manager.tsx"
CHEF_BFF="$ROOT/apps/customer-web-next/src/app/api/chef/subscription-plans/route.ts"
CHEF_PAGE="$ROOT/apps/customer-web-next/src/app/chef/meal-plans/page.tsx"
APIM_WORKFLOW="$ROOT/scripts/apim/configure-chef-subscription-plan-workflow-apim.sh"
SNAPSHOT_MIGRATION="$ROOT/services/subscription-service/src/main/resources/db/migration/V17__subscription_meal_item_snapshots.sql"

fail() { echo "ERROR: $*" >&2; exit 1; }
require_text() { local file="$1" text="$2"; grep -Fq "$text" "$file" || fail "Missing required contract '$text' in ${file#$ROOT/}"; }
forbid_text() { local file="$1" text="$2"; ! grep -Fq "$text" "$file" || fail "Forbidden contract '$text' exists in ${file#$ROOT/}"; }

for file in "$BACKEND_CONTROLLER" "$WORKFLOW_SERVICE" "$SUBSCRIPTION_SERVICE" "$DEFAULT_POLICY_SERVICE" "$POLICY_SERVICE" "$POLICY_REPOSITORY" "$SCHEDULE_SERVICE" "$ADMIN_CONTROLLER" "$ADMIN_SCHEDULE_CONTROLLER" "$ADMIN_BFF" "$ADMIN_STATUS_BFF" "$ADMIN_SCHEDULE_BFF" "$ADMIN_RUNTIME_UI" "$CHEF_BFF" "$CHEF_PAGE" "$APIM_WORKFLOW" "$SNAPSHOT_MIGRATION"; do
  [[ -f "$file" ]] || fail "Required Chef-owned meal plan file is missing: ${file#$ROOT/}"
done

require_text "$BACKEND_CONTROLLER" '@PostMapping("/chef/subscription-plans")'
require_text "$BACKEND_CONTROLLER" '@PutMapping("/chef/subscription-plans/{planId}/schedule")'
require_text "$BACKEND_CONTROLLER" '@PostMapping("/chef/subscription-plans/{planId}/submit")'
require_text "$BACKEND_CONTROLLER" '@PostMapping("/admin/subscription-plans/{planId}/review")'
require_text "$WORKFLOW_SERVICE" 'defaultPolicyService.ensureActiveDefault'
require_text "$DEFAULT_POLICY_SERVICE" 'customer self-service pause, resume, cancel and skip are disabled'
require_text "$DEFAULT_POLICY_SERVICE" "'PLATFORM_DEFAULT_ACTIVATE'"
require_text "$POLICY_SERVICE" 'repository.findPublicActive(planId)'
require_text "$POLICY_REPOSITORY" "p.status = 'ACTIVE'"
require_text "$SCHEDULE_SERVICE" 'if (!"ACTIVE".equals(plan.status()))'
require_text "$SNAPSHOT_MIGRATION" 'menu_item_name_snapshot'
require_text "$SNAPSHOT_MIGRATION" 'menu_item_price_snapshot'

forbid_text "$ADMIN_CONTROLLER" '@PostMapping("/admin/subscription-plans")'
forbid_text "$ADMIN_SCHEDULE_CONTROLLER" '@PutMapping'
forbid_text "$ADMIN_SCHEDULE_CONTROLLER" '@PostMapping'
forbid_text "$SUBSCRIPTION_SERVICE" 'createPlan('
require_text "$SUBSCRIPTION_SERVICE" 'Administrators can only deactivate plans here'
forbid_text "$ADMIN_BFF" 'export async function POST'
forbid_text "$ADMIN_SCHEDULE_BFF" 'export async function PUT'
forbid_text "$ADMIN_RUNTIME_UI" 'AdminSubscriptionPolicyManager'
require_text "$ADMIN_STATUS_BFF" 'input.status !== "INACTIVE"'

require_text "$CHEF_BFF" 'export async function POST'
require_text "$CHEF_PAGE" 'ChefSubscriptionPlanManager'
require_text "$APIM_WORKFLOW" 'delete_operation_by_route "$ADMIN_PLAN_API" "POST" "/"'
require_text "$APIM_WORKFLOW" 'delete_operation_by_route "$ADMIN_PLAN_API" "PUT" "/{planId}/schedule"'
require_text "$APIM_WORKFLOW" 'delete_operation_by_route "$ADMIN_PLAN_API" "POST" "/{planId}/schedule/activate"'

echo "SUCCESS: Chef-owned subscription meal plan ownership, approval-only Admin boundaries, snapshots, and pre-approval visibility guards verified."
