# Admin operational investigations

This module adds a read-only administrator workspace for exact-UUID operational evidence.

## Route

```text
/admin/operations
POST /api/admin/operations/investigate
```

## Backend contracts

```text
GET /api/v1/admin/operations/orders/{orderId}
GET /api/v1/admin/operations/payments/{paymentOrderId}
GET /api/v1/admin/operations/refunds/{refundId}
GET /api/v1/admin/operations/delivery-commands/{commandId}
```

The Next.js route converts a same-origin POST into the owning backend GET and sends the mandatory `X-Admin-Reason` header. The owning Spring service validates the ADMIN role, creates the audit row and returns `X-Correlation-ID`.

## Privacy and safety

- HTTP-only Craves session only
- exact UUID lookup; no broad customer or transaction search
- audit reason must contain 10–500 characters
- strict privacy-reduced response parser
- raw provider/webhook payloads are never returned
- no access token, signature, device token or full contact data is rendered
- no browser storage or sensitive logging
- no retry, refund, payment, delivery, account or provider mutation
- no-store responses

## Local verification

```bash
cd apps/customer-web-next
npm install --ignore-scripts
npm run typecheck
npm run test
npm run build
```

Set `CRAVES_API_BASE_URL` to the HTTPS APIM origin. The backend investigation operations must be deployed and separately exposed through the guarded APIM child module before a live lookup can succeed.

## Environment variables

No new frontend secret is introduced. Existing variables remain authoritative:

```text
CRAVES_API_BASE_URL
NEXT_PUBLIC_FIREBASE_*
```

## Deployment

This PR is build-only. It does not run a pipeline, change APIM, deploy the web app or inspect production data. Deploy only after the parent backend stack and the dedicated APIM rollout pass CI and merge in order.
