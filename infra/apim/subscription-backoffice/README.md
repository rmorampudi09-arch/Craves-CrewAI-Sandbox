# Subscription and Backoffice APIM Package

This package configures four APIM paths only after explicit confirmation:

```text
api/v1/subscriptions
api/v1/admin/subscription-plans
api/v1/admin/subscriptions
api/v1/backoffice/chef-reviews
```

Public operations:

```text
GET /api/v1/subscriptions/plans
GET /api/v1/subscriptions/plans/{planId}
```

Every customer/admin/backoffice operation requires a Bearer header at APIM and is cryptographically authorized again by the owning service.

## Files

```text
authenticated-policy.xml
public-policy.xml
scripts/apim/configure-subscription-backoffice-apim.sh
scripts/apim/status-subscription-backoffice-apim.sh
scripts/apim/rollback-subscription-backoffice-apim.sh
```

## Safety

- write/rollback confirmations default false
- Container App readiness and health checks
- exactly one API owner per path
- refuses subscription-key relaxation
- refuses inherited `backend-id`
- operation-scoped policies and rollback
- API containers retained during rollback
- no-store and `nosniff`
- no credentials in source

## Later order

1. Run CI.
2. Deploy User-Chef Service proof-stream code.
3. Deploy Subscription Service ownership/audit code.
4. Run the APIM write pipeline with explicit confirmation.
5. Run the read-only status pipeline.
6. Execute authenticated/non-authenticated smoke tests.
7. Use rollback only for verified gateway faults.
