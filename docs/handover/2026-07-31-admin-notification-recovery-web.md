# Craves admin notification recovery web handover

**Date:** 31 July 2026  
**Runtime changes:** None  
**Pipeline runs:** None

## Outcome

The remaining Notification Service recovery operations now have a guarded administrator UI and BFF. The browser receives only operational fields needed to select and requeue a failed request.

## Files

- `apps/customer-web-next/src/lib/admin-notification-recovery-contract.ts`
- `apps/customer-web-next/src/lib/admin-notification-recovery-contract.test.ts`
- `apps/customer-web-next/src/app/api/admin/notifications/recovery/route.ts`
- `apps/customer-web-next/src/app/api/admin/notifications/recovery/[requestId]/route.ts`
- `apps/customer-web-next/src/components/admin-notification-recovery.tsx`
- `apps/customer-web-next/src/app/admin/notifications/page.tsx`
- `apps/customer-web-next/src/components/admin-shell.tsx`
- `azure-pipelines-admin-notification-recovery-web-ci.yml`

## Backend ownership

Notification Service remains the sole owner of request state, attempts, recovery audit, correlation ID and later provider delivery.

## Privacy

The parser intentionally drops `recipientIdentityId` and `requestKey`. Raw payloads, provider credentials and message bodies are not rendered.

## Safety

Only FAILED or DEAD_LETTER items may be selected. Exact `RETRY` confirmation and a 10–500 character reason are required. Requeue updates durable state to PENDING; it does not call FCM or ACS in the admin transaction.

## Deployment order

1. Merge parent stack.
2. Deploy Notification Service with recovery API false.
3. Configure APIM child module.
4. Deploy web.
5. Validate ADMIN backlog access.
6. Activate recovery API separately.
7. Validate one controlled non-customer test request before production use.

## Rollback

Disable recovery API, roll back web and remove only the named APIM operations. Existing notification and audit records remain intact.

## Pending

CI, merge, deployment, APIM configuration, activation and live requeue testing are intentionally deferred to the controlled pipeline session.
