# Craves admin notification recovery APIM handover

**Date:** 31 July 2026  
**Runtime changes:** None  
**Pipeline runs:** None

## Scope

Adds a dedicated gateway boundary for the existing Notification Service backlog and retry operations. It does not enable recovery or any provider worker.

## Deployment order

1. Run static APIM CI.
2. Deploy Notification Service with recovery API false.
3. Run controlled APIM rollout with explicit confirmation.
4. Run read-only status verification.
5. Deploy administrator web.
6. Validate unauthenticated 401 and authenticated ADMIN backlog access.
7. Enable recovery API through the existing activation pipeline.
8. Validate one controlled non-customer FAILED request before operational use.

## Rollback

Disable the recovery API, roll back web, then remove only the backlog and retry APIM operations. Notification requests, dead-letter evidence and recovery audit rows are preserved.

## Pending

CI, merge, deployment, APIM mutation, activation and live requeue testing are intentionally deferred.
