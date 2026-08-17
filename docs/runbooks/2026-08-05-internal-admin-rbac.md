# Craves internal administrator RBAC — backend handover

Date: 2026-08-05

## Outcome

The backend has a complete least-privilege internal-role model instead of one unrestricted `ADMIN` permission. This source change does not touch customer, chef, admin or mobile frontend code. It does not activate Cashfree, Firebase provider operations, delivery providers, email, push, SMS or subscription workers.

The legacy `ADMIN` role remains only as a compatibility marker for the existing backoffice shell. Sensitive Spring Boot operations authorize named internal roles.

## Existing provider-neutral backend retained

The following capabilities already existed on `main` and remain authoritative:

- chef application, KYC review, approval/rejection and CHEF-role grant;
- subscription plans, schedules, occurrences, billing and order-request workflows;
- customer account intervention with Firebase synchronization queue;
- notification delivery outbox, retry, dead-letter and admin recovery;
- order, payment, refund and delivery investigations;
- payment/refund/delivery provider-neutral state machines and reconciliation workers;
- chef earnings allocation and settlement records;
- launch-policy creation and activation;
- idempotent inbox/outbox processing and service-owned audit evidence;
- guarded seven-service image build, deployment, health verification and rollback.

This module completes the remaining provider-neutral authorization gap.

## Roles and ownership

Auth Service owns internal roles, assignments, token invalidation and role-change audit.

| Role | Backend authority |
|---|---|
| `PLATFORM_ADMIN` | Full internal operations and role management |
| `SUPPORT_ADMIN` | Read-only customer/account/order/payment/refund/delivery investigations |
| `PAYMENTS_ADMIN` | Payment/refund investigations, earnings and settlement operations |
| `OPERATIONS_ADMIN` | Order/delivery investigations and launch policy |
| `CHEF_ADMIN` | Chef application review and decisions |
| `COMPLIANCE_ADMIN` | Chef application and KYC review without decision authority |
| `SUBSCRIPTION_ADMIN` | Subscription plans, schedules and lifecycle |
| `NOTIFICATION_ADMIN` | Notification backlog and recovery |
| `AUDIT_ADMIN` | Read-only investigations and role-change audit |

Downstream services trust signed Craves JWT role claims but recheck operation-specific authorization inside the owning service.

## Internal role-management API

Base path:

```text
/api/v1/admin/internal-access
```

Operations:

```text
GET /roles
GET /users?limit=100
GET /users/{identityId}
PUT /users/{identityId}/roles
GET /role-changes?identityId={optional}&limit=100
```

Read operations require `X-Admin-Reason` containing 10–500 characters. Role replacement requires `PLATFORM_ADMIN`, an exact replacement role set, `expectedTokenVersion`, a reason and optional UUID `X-Correlation-ID`.

Example:

```json
{
  "roles": ["SUPPORT_ADMIN"],
  "expectedTokenVersion": 4,
  "reason": "Assigning customer support responsibilities"
}
```

The API cannot grant or revoke `CUSTOMER`, `CHEF` or the legacy `ADMIN` marker directly.

## Operation matrix

| Backend operation | Authorized roles |
|---|---|
| Internal role assignment | `PLATFORM_ADMIN` |
| Internal user and role-audit reads | `PLATFORM_ADMIN`, `AUDIT_ADMIN` |
| Suspend/reactivate identity | `PLATFORM_ADMIN` |
| Read intervention status | `PLATFORM_ADMIN`, `SUPPORT_ADMIN`, `AUDIT_ADMIN` |
| List/detail chef applications | `PLATFORM_ADMIN`, `CHEF_ADMIN`, `COMPLIANCE_ADMIN`, `AUDIT_ADMIN` |
| Download KYC proof | `PLATFORM_ADMIN`, `CHEF_ADMIN`, `COMPLIANCE_ADMIN` |
| Approve/reject chef | `PLATFORM_ADMIN`, `CHEF_ADMIN` |
| Subscription administration | `PLATFORM_ADMIN`, `SUBSCRIPTION_ADMIN` |
| Order investigation | `PLATFORM_ADMIN`, `SUPPORT_ADMIN`, `PAYMENTS_ADMIN`, `OPERATIONS_ADMIN`, `AUDIT_ADMIN` |
| Payment/refund investigation | `PLATFORM_ADMIN`, `SUPPORT_ADMIN`, `PAYMENTS_ADMIN`, `AUDIT_ADMIN` |
| Delivery investigation | `PLATFORM_ADMIN`, `SUPPORT_ADMIN`, `OPERATIONS_ADMIN`, `AUDIT_ADMIN` |
| Earnings/settlement mutations | `PLATFORM_ADMIN`, `PAYMENTS_ADMIN` |
| Earnings/settlement reads | `PLATFORM_ADMIN`, `PAYMENTS_ADMIN`, `AUDIT_ADMIN` |
| Launch-policy administration | `PLATFORM_ADMIN`, `OPERATIONS_ADMIN` |
| Notification backlog read | `PLATFORM_ADMIN`, `NOTIFICATION_ADMIN`, `AUDIT_ADMIN` |
| Notification retry | `PLATFORM_ADMIN`, `NOTIFICATION_ADMIN` |

## Database migration

`services/auth-service/src/main/resources/db/migration/V6__internal_admin_rbac.sql`:

1. seeds all nine internal roles;
2. backfills every existing legacy `ADMIN` identity to `PLATFORM_ADMIN` to avoid lockout;
3. creates append-only `auth_internal_role_change_audit`;
4. indexes actor, target and correlation lookups;
5. adds reason and token-version constraints.

The migration preserves all customer, chef and legacy role rows and does not create identities.

## Concurrency, lockout and token controls

- Role replacement uses a PostgreSQL transaction advisory lock and target-row lock.
- `expectedTokenVersion` rejects stale screens and concurrent overwrites.
- An administrator cannot remove their own `PLATFORM_ADMIN` role.
- The last active platform administrator cannot be removed.
- Every real role change increments the target token version.
- Active refresh sessions are revoked with reason `ADMIN_ROLE_CHANGE`.
- The actor's live platform role and token version are rechecked inside Auth Service.
- No-change replacements remain audited but do not invalidate sessions unnecessarily.

## Privacy and audit

Responses expose only identity UUID, masked phone, masked email, display name, status, token version and internal roles. Firebase UID, credentials, raw tokens and refresh-session content are excluded. Responses use `Cache-Control: no-store`.

Every role replacement records actor, target, exact previous/new role sets, previous/new token versions, changed/no-change result, reason, correlation UUID and timestamp. Sensitive reads also write audit evidence.

## Safe default

```text
CRAVES_INTERNAL_ADMIN_RBAC_API_ENABLED=false
```

The management API remains disabled after deployment. No external-provider or business-rule flag is changed by this module.

## Verification

Run the manual Azure DevOps pipeline defined by:

```text
azure-pipelines-backend-internal-admin-rbac-ci.yml
```

It executes:

```text
bash scripts/release/validate-internal-admin-rbac.sh
mvn -B -ntp clean verify
```

for Auth, User/Chef, Order, Integration, Subscription and Notification services on Java 21. The normal seven-service backend completion pipeline remains the deployment authority.

Required non-secret pipeline variable:

```text
AZURE_SERVICE_CONNECTION = Craves-Dev-Service-Connection
```

## Safe deployment order

1. Run RBAC CI and the backend completion pipeline in `VERIFY_ONLY`.
2. Confirm a restorable PostgreSQL point-in-time backup.
3. Run the normal seven-service backend deployment pipeline.
4. Confirm Auth Flyway V6 completed.
5. Confirm at least one existing internal identity now has `PLATFORM_ADMIN`.
6. Configure APIM using `azure-pipelines-internal-admin-rbac-apim.yml`.
7. Refresh the platform administrator session.
8. Enable the API using `azure-pipelines-backend-internal-admin-rbac-activation.yml`.
9. Run the backend and APIM status pipelines.

## First production validation

1. Read the role catalog with a refreshed platform-administrator token.
2. Read the platform-administrator record with a reason and correlation UUID.
3. Submit the same exact role set and confirm `changed=false` plus audit evidence.
4. Assign `SUPPORT_ADMIN` to a non-production staff identity.
5. Confirm its old refresh session is revoked.
6. Sign in again and confirm its JWT contains `SUPPORT_ADMIN` plus compatibility `ADMIN`.
7. Confirm permitted investigation reads work.
8. Confirm chef decisions, settlement mutations, launch-policy mutations and notification retry return `403`.
9. Remove the test role and confirm backoffice compatibility access is removed.

## Rollback

- Disable the management API using `azure-pipelines-backend-internal-admin-rbac-rollback.yml`.
- Roll back APIM operations using `azure-pipelines-internal-admin-rbac-apim-rollback.yml` when required.
- Roll back service images using the guarded seven-service release pipeline.
- Do not delete V6 tables, assignments or append-only audit records. Flyway changes are forward-only.

## Remaining manual work

Only operator-controlled steps remain for this module:

- run CI/deployment pipelines;
- verify the database backup and V6 migration;
- select real staff identities and assign roles;
- configure APIM and enable the fail-closed feature flag;
- perform the authorization matrix smoke test.

No third-party provider credential or frontend change is required for this backend module.
