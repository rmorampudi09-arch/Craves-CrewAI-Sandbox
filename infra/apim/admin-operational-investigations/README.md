# Admin operational investigations APIM

This module exposes only the four read-only administrator investigation operations required by the Next.js workspace.

## API

```text
API ID: craves-admin-operational-investigations-v1
Path:   api/v1/admin/operations
Subscription key: not required
Bearer header: required at the gateway and revalidated by the backend
```

## Operation routing

| Operation | Backend owner |
|---|---|
| `GET /orders/{resourceId}` | Order Service |
| `GET /payments/{resourceId}` | Integration Service |
| `GET /refunds/{resourceId}` | Integration Service |
| `GET /delivery-commands/{resourceId}` | Integration Service |

One dedicated API is used, with operation-level `set-backend-service base-url` policies. This avoids duplicating or merging Order and Integration service ownership.

## Safety controls

- explicit `CONFIRM_APIM_WRITE=true`
- exact API path ownership check
- refuses multiple path owners
- refuses a different existing API owner
- refuses subscription-key relaxation
- refuses inherited `backend-id` policy conflicts
- verifies both Container Apps are healthy
- Bearer-header precheck
- no-store, no-cache, nosniff and frame-deny response headers
- unauthenticated 401 smoke test
- no authenticated production data lookup in rollout/status scripts
- rollback removes only the four approved operations

## Pipelines

```text
azure-pipelines-admin-operational-investigations-apim-ci.yml
azure-pipelines-admin-operational-investigations-apim.yml
azure-pipelines-admin-operational-investigations-apim-status.yml
azure-pipelines-admin-operational-investigations-apim-rollback.yml
```

The Azure pipelines use the established variable:

```text
AZURE_SERVICE_CONNECTION=Craves-Dev-Service-Connection
```

Do not place service-principal credentials in Git or chat.

## Run order

1. Merge/deploy backend stack through PR #100 with execution flags false.
2. Merge/build PR #101.
3. Run APIM CI for this child PR.
4. Run rollout with `confirmApimWrite=true`.
5. Run status verification.
6. Deploy the merged customer web image.
7. Perform one authorized smoke investigation with a real audit reason.

## Rollback

Run the rollback pipeline with `confirmApimRollback=true`. Leave `deleteEmptyApi=false` unless the empty API has been independently confirmed to have no other owner.
