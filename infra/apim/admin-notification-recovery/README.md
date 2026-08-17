# Admin notification recovery APIM

## API

- API ID: `craves-admin-notification-recovery-v1`
- Path: `api/v1/admin/notifications/operations`
- Backend: Notification Service

## Operations

- `GET /backlog`
- `POST /{requestId}/retry`

The rollout requires explicit confirmation, one exact path owner, a healthy Notification Service and safe operation-level base URL policies. APIM performs a Bearer syntax guard; Notification Service remains authoritative for JWT validation, ADMIN authorization, status validation and audit evidence.

Pipelines are manual only: static CI, rollout, status and bounded rollback.
