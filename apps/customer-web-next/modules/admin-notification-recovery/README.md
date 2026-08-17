# Admin notification recovery web

## Purpose

Provides an ADMIN-only, privacy-reduced view of FAILED and DEAD_LETTER notification requests and allows one audited requeue to PENDING.

## Routes

- `GET /api/admin/notifications/recovery?status=FAILED|DEAD_LETTER&limit=1..100`
- `POST /api/admin/notifications/recovery/{requestId}`
- `/admin/notifications`

## Safety controls

- HTTP-only Craves session
- backend ADMIN authorization
- same-origin mutation guard
- bounded backlog status and size
- recipient identity and request keys omitted from the browser contract
- 10–500 character reason
- exact `RETRY` typed confirmation
- strict backend response parsing
- no browser storage or sensitive logging
- no provider call in the administrator transaction
- no-store responses

## Activation dependency

`CRAVES_NOTIFICATION_RECOVERY_API_ENABLED=false` remains the default. The notification worker/provider flags separately govern actual delivery after requeue.

## Local verification

```bash
cd apps/customer-web-next
npm install --ignore-scripts
npm run typecheck
npm run test
npm run build
```
