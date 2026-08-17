# Customer Notification Inbox

## Route

```text
/notifications
```

## Backend contract reused

```text
GET   /api/v1/notifications/in-app?limit=50
PATCH /api/v1/notifications/in-app/{noticeId}/read
```

## Security

- access token is read only by the Next.js server BFF;
- mark-read requires a same-origin PATCH;
- inbox responses are no-store;
- notice IDs and timestamps are validated;
- at most 50 notifications are requested and 100 accepted by the parser;
- raw provider payloads, event keys, delivery transaction IDs and dispatch metadata are not part of the browser contract.

## Main files

```text
src/lib/notification-contract.ts
src/lib/notification-contract.test.ts
src/app/api/notifications/route.ts
src/app/api/notifications/[noticeId]/read/route.ts
src/components/notification-inbox.tsx
src/app/notifications/page.tsx
```

## CI

```text
azure-pipelines-customer-web-next-notifications-ci.yml
```

## Manual testing later

1. Sign in with a Firebase test customer.
2. Open `/notifications`.
3. Confirm only that customer's notices are returned.
4. Mark one unread notice as read.
5. Refresh and confirm `readAt` is retained.
6. Sign out and confirm the route shows the session-expired state.

No new Notification Service endpoint, database migration, APIM operation or Azure resource is required because the existing JWT-based inbox API is already deployed and verified.
