# Internal administrator RBAC

This module replaces the broad backend `ADMIN` authorization check with named, least-privilege internal roles. The legacy `ADMIN` role remains only as a compatibility marker for the existing backoffice shell; sensitive Spring Boot operations do not authorize it directly.

## Roles

| Role | Backend access |
|---|---|
| `PLATFORM_ADMIN` | All internal operations, account intervention and role management |
| `SUPPORT_ADMIN` | Read-only account status and order/payment/refund/delivery investigations |
| `PAYMENTS_ADMIN` | Payment/refund investigations, chef earnings and settlement records |
| `OPERATIONS_ADMIN` | Order/delivery investigations and launch-policy administration |
| `CHEF_ADMIN` | Chef application review, approval and rejection |
| `COMPLIANCE_ADMIN` | Chef KYC/application inspection without approval authority |
| `SUBSCRIPTION_ADMIN` | Subscription plans, schedules and lifecycle status operations |
| `NOTIFICATION_ADMIN` | Notification backlog inspection and recovery |
| `AUDIT_ADMIN` | Read-only investigations and internal-role change audit |

## Role management API

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

User and audit reads require `X-Admin-Reason` with 10–500 characters. Mutations require `PLATFORM_ADMIN`, a complete exact-set role replacement, the last-read `expectedTokenVersion`, a reason, and an optional UUID `X-Correlation-ID`.

Example request body:

```json
{
  "roles": ["SUPPORT_ADMIN"],
  "expectedTokenVersion": 4,
  "reason": "Assigning customer support responsibilities"
}
```

The request can manage only the nine internal roles. It cannot grant or revoke `CUSTOMER`, `CHEF`, or the legacy `ADMIN` marker directly.

## Security behavior

- Existing `ADMIN` identities are migrated to `PLATFORM_ADMIN`, preventing lockout.
- Every internal role holder receives the legacy `ADMIN` marker only so the unchanged admin shell can recognize backoffice access.
- Removing all internal roles also removes that compatibility marker.
- An administrator cannot remove their own `PLATFORM_ADMIN` role.
- The last active platform administrator cannot be removed.
- Role replacements use a PostgreSQL transaction advisory lock and target-row lock.
- `expectedTokenVersion` rejects stale operator screens and concurrent overwrites.
- Every actual change increments `auth_identity.token_version`.
- Active refresh sessions are revoked with `ADMIN_ROLE_CHANGE`.
- The existing V4 token-revocation trigger creates a distributed revocation projection.
- Every request, including no-change replacements, creates append-only role-change audit evidence.
- Responses mask phone and email values and are returned with `Cache-Control: no-store`.

## Safe default

```text
CRAVES_INTERNAL_ADMIN_RBAC_API_ENABLED=false
```

Deploy the migration and all seven service images before enabling the API. Confirm at least one migrated `PLATFORM_ADMIN` identity, then use the guarded activation pipeline. Do not manually edit role rows in production.

## Verification

The canonical backend pipeline runs:

```text
bash scripts/release/validate-internal-admin-rbac.sh
mvn -B -ntp clean verify
```

The validator checks role seeding, migration backfill, audit evidence, session/token revocation, stale-write protection, least-privilege mappings and the absence of broad legacy authorization in sensitive backend modules.
