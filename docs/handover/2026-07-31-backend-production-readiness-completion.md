# Craves backend production-readiness preparation handover

**Date:** 31 July 2026  
**Repository:** `rmorampudi09-arch/Craves-Build-platform`  
**Latest branch in this work package:** `feature/backend-production-readiness-completion`  
**Pipeline runs performed:** none  
**Azure/APIM/provider/database changes performed:** none

---

## 1. User objective

Complete every remaining source-level task before the dedicated pipeline, CDN and production deployment-readiness session.

## 2. Result

All identified initial-launch backend functional modules, administrator operational surfaces, guarded APIM modules and production execution-control artifacts now exist in the stacked Git history.

## 3. Existing backend baseline

The seven Spring services are Auth, User-Chef, Catalog, Order, Subscription, Integration and Notification.

## 4. Previously completed backend

The launch-critical stack through PR 100 includes launch policy, delivery, payments, refunds, subscriptions, financial ledger, notifications, investigations, account intervention, notification recovery and Redis-backed abuse/revocation controls.

## 5. Administrator operational investigations

PR 101 adds a privacy-reduced read-only web workspace for order, payment, refund and delivery evidence. PR 102 adds its guarded APIM operations.

## 6. Administrator account intervention

PR 103 adds exact-identity status lookup and typed-confirmation suspension/reactivation UI. PR 104 adds a dedicated APIM boundary.

## 7. Administrator notification recovery

PR 105 adds FAILED/DEAD_LETTER inspection and single-request audited requeue UI. PR 106 adds a dedicated APIM boundary.

## 8. Final source-control pack

This branch adds the production-readiness manifest, internal verifier, run-plan generator, final non-deploying preflight pipeline, execution runbook and evidence checklist.

## 9. Eight workstreams represented

1. CI and source verification
2. Stacked merge control
3. Seven-service deployment
4. Database migration verification
5. Cloud routing and messaging
6. Provider and Redis dependencies
7. Staged activation and rollback
8. Production validation

## 10. Production readiness manifest

`config/production/backend-production-readiness.json` is the machine-readable source of workstreams, service directories, critical disabled-by-default flags, merge groups, required evidence and hard stops.

## 11. Source verifier

`scripts/release/verify-backend-production-readiness-pack.sh` checks the manifest, seven Maven/Docker service roots, safety flags, required artifacts and manual-only activation/rollback pipelines.

## 12. Pipeline-plan generator

`scripts/release/generate-backend-production-run-plan.sh` discovers CI, status, activation/APIM and rollback pipeline YAML files from the selected checkout and emits one Markdown execution artifact.

## 13. Final preflight pipeline

`azure-pipelines-backend-production-readiness-final.yml` runs source integrity, all seven Java builds, administrator web tests/build and a final non-deploying gate. It publishes the generated run plan.

## 14. Execution runbook

`docs/runbooks/2026-07-31-backend-production-execution-plan.md` contains the controlled sequence from source freeze through CI, merge, images, deployments, Flyway, Azure dependencies, provider validation, activation, testing and go-live evidence.

## 15. Evidence checklist

`docs/checklists/backend-production-evidence-checklist.md` prevents a verbal or screenshot-only production sign-off. Every completed item must link to verifiable evidence.

## 16. Account intervention safety

The browser requires an exact UUID, successful read-before-write status lookup, a 10–500 character reason and exact typed `SUSPEND` or `REACTIVATE`. Auth Service remains authoritative and blocks self-suspension.

## 17. Account privacy

The account UI accepts and renders only bounded backend fields and masked phone. It does not accept or expose Firebase UID, raw phone, access token, refresh token or provider payload.

## 18. Account APIM safety

The APIM rollout verifies exact path ownership, a healthy Auth Service, no inherited backend-ID conflict, operation-level base URL, Bearer syntax, response hardening and unauthenticated 401 behavior.

## 19. Notification recovery safety

The browser permits only FAILED or DEAD_LETTER lists, omits recipient identity and request key, requires exact `RETRY` confirmation and does not call FCM/ACS inside the administrator transaction.

## 20. Notification APIM safety

The APIM rollout exposes only bounded backlog GET and single retry POST, with exact path ownership, healthy backend, policy read-back, no-store headers and bounded rollback.

## 21. Runtime feature state

All newly introduced backend APIs, workers, consumers, publishers, providers, enforcement and Redis security controls remain disabled until controlled activation.

## 22. No business rule invention

No minimum order, serviceability radius, commission, settlement, refund, grace period, delivery SLA or FSSAI value was supplied by this work.

## 23. No runtime claim

No CI result, successful merge, deployed image, migration, APIM operation, Service Bus subscription, provider callback, Redis binding or production test is claimed because none was executed in this work package.

## 24. Next exact session

The next session begins with module CI and the final non-deploying preflight. Failures are fixed before any merge. After a green stack, merge parent-first and deploy all seven services with flags disabled.

## 25. CDN boundary

Azure Front Door/CDN, custom domains, certificate binding and DNS cutover remain the next separate package after backend origins, APIM and web are healthy.

## 26. Manual intervention categories

- Azure DevOps: run pipelines and maintain secret variables.
- Azure Portal/CLI: Container Apps, Service Bus, identities, Redis, APIM and monitoring.
- Firebase Console: project/service-account/provider configuration.
- Cashfree and delivery consoles: KYC, credentials and callback registration.
- DNS/CDN: records and certificates after backend readiness.

## 27. Billing warning

Redis, Front Door/CDN, higher Container App capacity, PostgreSQL scaling, Service Bus tiers and monitoring retention may create Azure charges. Review SKU and available startup credits before provisioning.

## 28. Rollback principle

Disable upstream provider execution and publishers first, preserve durable consumers/evidence, then roll back APIM/web and Container App revisions. Never delete financial, notification, inbox, outbox, dead-letter or audit records as a rollback shortcut.

## 29. Current source-level count

Known launch-critical Spring backend modules left to design: zero. Known administrator/platform source gaps left before CI: zero. Production execution and evidence remain pending.

## 30. Final pending list

- run all pending CI pipelines
- fix failures
- merge the stacked PR chain
- build and deploy immutable images
- verify Flyway migrations
- configure Azure routing/messaging/secrets
- validate Redis and providers
- activate features in dependency order
- complete security/load/restore/field tests
- set up CDN/Front Door and DNS after backend readiness

## 31. Safety statement

This work package intentionally stopped at source preparation. It did not incur Azure resource changes, send notifications, charge/refund money, book delivery, suspend an identity or alter production data.
