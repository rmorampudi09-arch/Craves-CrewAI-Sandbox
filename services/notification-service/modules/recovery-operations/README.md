# Notification recovery operations

This module provides audited requeue controls for notification requests that exhausted automated delivery or are awaiting another retry.

## Endpoints

```text
GET  /api/v1/admin/notifications/operations/backlog?status=FAILED|DEAD_LETTER&limit=50
POST /api/v1/admin/notifications/operations/{requestId}/retry
```

Only an authenticated Craves `ADMIN` may use these endpoints.

## Requeue rules

A request can be requeued only when its current status is:

```text
FAILED
DEAD_LETTER
```

The following are deliberately rejected:

- `SENT`, because duplicate customer communication is unsafe;
- `PROCESSING`, because another replica owns the lease;
- `PENDING`, because the normal worker already owns it;
- `SKIPPED`, because customer notification preferences must not be overridden.

Requeue resets the automated attempt counter and places the request back into `PENDING`. Existing delivery attempts and the original dead-letter record are preserved. A separate append-only recovery audit stores the previous status, previous attempt count, administrator, reason and correlation ID.

## Privacy

Backlog responses contain typed operational metadata only. They exclude:

- notification body and arbitrary payload;
- customer email/phone delivery address;
- FCM device token;
- ACS connection string;
- Firebase service account;
- provider request/response payloads.

## Safety defaults

```text
CRAVES_NOTIFICATION_RECOVERY_API_ENABLED=false
```

The recovery API does not call FCM or ACS. It only updates durable Notification Service state. The existing delivery worker and provider flags continue to control actual sending.

## Later activation

1. Run `azure-pipelines-backend-notification-recovery-ci.yml`.
2. Deploy the merged Notification image with recovery false.
3. Confirm Flyway V3 and service health.
4. Enable the recovery API through the guarded activation pipeline.
5. Inspect one synthetic dead-letter request.
6. Requeue it with an operational reason and correlation UUID.
7. Confirm exactly one recovery-audit row and preserved historical attempts.
8. Enable the appropriate notification delivery worker/provider only through its separate rollout.

## Rollback

`azure-pipelines-backend-notification-recovery-rollback.yml` disables only the recovery API and preserves all evidence.
