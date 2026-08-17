# Craves backend production execution plan

**Document date:** 31 July 2026  
**Target:** controlled production launch for approximately 50–100 concurrent users  
**Scope:** Spring backend, administrator platform access, APIM and runtime dependencies  
**Not included in this execution:** CDN/Front Door cutover, DNS cutover or million-user scaling  
**Actions performed while writing this runbook:** none

---

## 1. Purpose

This is the single execution order to use after all draft PRs are reviewed. It converts the completed source stack into a production-ready backend without turning on providers, workers or enforcement prematurely.

## 2. Meaning of complete

Source complete means code, migrations, CI, activation pipelines, rollback pipelines and handovers exist. Production complete requires successful CI, merge, deployment, migration, dependency configuration, staged activation and operational evidence.

## 3. Mandatory stop rule

Stop immediately when any compile, test, health, migration, APIM read-back, Service Bus, provider, security, load or rollback gate fails. Never continue because a later phase might fix an earlier failure.

## 4. No-secret rule

Secrets belong only in Azure DevOps secret variables, Azure Key Vault or Container App secret references. Never place a secret in Git, pipeline parameters, CLI history, screenshots or chat.

## 5. No invented business rules

Do not activate launch-policy enforcement until approved values exist for serviceability radius and minimum order. Do not invent commission, settlement, refund, subscription grace, delivery SLA or FSSAI values.

## 6. Stack groups

Merge and validate these groups parent-first: customer web PRs 34–43; chef web PRs 46–55; ownership/subscription/admin PRs 58–68; release-readiness PRs 69–83; launch-critical backend PRs 84–100 excluding temporary synchronization PR 93; administrator refinements PRs 101–106; final readiness completion PR 107.

## 7. Phase A — source freeze

Record the selected head SHA, branch name, UTC timestamp and operator. No new feature work should enter the release branch after this point without restarting the readiness gate.

## 8. Phase A — pipeline inventory

Run `scripts/release/generate-backend-production-run-plan.sh`. Preserve the generated artifact. It discovers CI, status, activation and rollback YAML files from the selected checkout.

## 9. Phase A — final source verifier

Run `scripts/release/verify-backend-production-readiness-pack.sh`. It validates the eight-workstream manifest, seven service directories, safety flags, required handovers and manual-only pipeline triggers.

## 10. Phase B — module CI

Run every module-specific CI pipeline first. Do not run activation, APIM write, deployment or rollback pipelines during this phase.

## 11. Phase B — Java baseline

Every Spring service must pass Java 21 `mvn -B clean verify`: Auth, User-Chef, Catalog, Order, Subscription, Integration and Notification.

## 12. Phase B — web baseline

The Next.js administrator web must pass dependency installation, typecheck, tests and production build. Review same-origin guards, no-store behavior and privacy-reduced parsers.

## 13. Phase B — final preflight

Run `azure-pipelines-backend-production-readiness-final.yml`. This pipeline is intentionally non-deploying and publishes the immutable run plan artifact.

## 14. Phase B evidence

Capture pipeline URL, run ID, source SHA, test results and artifact digest. A screenshot alone is insufficient because it cannot reliably identify source or logs.

## 15. Phase C — parent-first merge

Merge only the current parent. Retarget or rebase its direct child, rerun required CI and then merge the child. Repeat until PR 107 is merged.

## 16. Phase C — synchronization PR 93

PR 93 was feature-branch synchronization, not an independent production feature. Preserve its ancestry while resolving the parent chain, but do not treat it as an additional mainline business module.

## 17. Phase C — protected main

Require successful checks, disallow force pushes and record merge SHAs. Do not squash in a way that destroys required ancestry unless every child is deliberately retargeted and revalidated.

## 18. Phase D — immutable images

Build one immutable image per service from the final merged SHA. Use unique tags and record ACR digest, repository and build run. Never deploy `latest`.

## 19. Phase D — initial feature state

Deploy every service with new execution, provider, consumer, publisher, worker and enforcement flags disabled. Production environment naming must not implicitly enable anything.

## 20. Phase D — service deployment order

Deploy Auth, User-Chef, Catalog, Order, Subscription, Integration and Notification with health verification after each revision. This is a deployment sequence, not an activation sequence.

## 21. Phase D — revision readiness

For each Container App, latest revision must equal latest ready revision, running status must be Running and `/actuator/health` must return HTTP 200.

## 22. Phase D — rollback map

Before traffic changes, record the previous healthy revision and image digest for every service. Confirm the rollback command or pipeline can restore it.

## 23. Phase E — Flyway migrations

Allow each service to apply only its owned migrations. Verify Flyway history table entries, checksums and success state immediately after service startup.

## 24. Phase E — database ownership

No service may query another service’s database. Cross-service IDs remain application-level references carried by APIs or events.

## 25. Phase E — backup prerequisite

Record PostgreSQL backup/restore-point evidence before applying production migrations. A later restore rehearsal is still required.

## 26. Phase F — managed identities

Verify each Container App identity has only the necessary roles for ACR, Key Vault and Service Bus. Avoid broad Contributor rights.

## 27. Phase F — Key Vault and secret references

Verify secret names and references without printing values. Confirm every Container App resolves required secrets and can restart successfully.

## 28. Phase F — Service Bus

Create or validate topics, subscriptions, filters and dead-letter settings required by order, delivery, payment, subscription, financial ledger, notification and token-revocation flows.

## 29. Phase F — downstream-first messaging

Activate and validate the downstream consumer before the upstream publisher. This rule applies to payment status, delivery status, subscription fulfillment, financial ledger, notifications and token-revocation projections.

## 30. Phase F — APIM

Run APIM static CI first. Then run guarded APIM rollout pipelines one module at a time. Each rollout must pass exact path ownership, backend health, policy read-back and unauthenticated 401 smoke.

## 31. Phase F — administrator APIs

Configure operational investigations, account intervention and notification recovery paths only after their owning services are healthy. Backend JWT and ADMIN authorization remain authoritative.

## 32. Phase G — Redis

Provision or validate Azure Cache for Redis, TLS connection, firewall/network access and secret reference. Bind `SPRING_DATA_REDIS_URL` to all seven services before any revocation or rate-limit feature is enabled.

## 33. Phase G — Firebase

Verify Firebase project ID and service-account permissions. Keep account-intervention API and Firebase worker disabled until local session revocation and rollback have been validated.

## 34. Phase G — FCM and ACS Email

Validate FCM and ACS credentials, sender identity and non-customer test destinations. Provider workers remain disabled until durable notification state and recovery operations are proven.

## 35. Phase G — Cashfree

Verify production account, KYC, client credentials, webhook URL, signing validation and network reachability. Prove webhook receipt and replay protection before enabling payment execution.

## 36. Phase G — delivery provider

The final provider must have approved credentials, callback registration, pickup-location coverage and Hyderabad field validation. Do not activate an unapproved dynamic provider policy.

## 37. Phase H — observability before activation

Dashboards, logs, metrics and alerts must exist before enabling workers. Minimum coverage includes error rate, latency, queue backlog, dead letters, provider failures, database saturation, Redis failures and revision health.

## 38. Phase H — activation snapshots

Before every activation, export the current non-secret environment-variable names and values for feature flags, current revision, image digest and rollback target.

## 39. Phase H — auth activation

First validate Redis connectivity. Then enable token-revocation publisher, then revocation enforcement service-by-service, then rate limiting with approved thresholds. Reverse that order for rollback.

## 40. Phase H — account intervention activation

Enable the local Auth API first. Test read-only status and one controlled test identity. Enable Firebase worker separately only after local audit and provider synchronization evidence are healthy.

## 41. Phase H — notification recovery activation

Enable recovery API before provider workers. Requeue one non-customer FAILED request and verify it returns to PENDING without provider execution inside the admin transaction.

## 42. Phase H — notification delivery activation

Enable durable consumers and internal workers before FCM or ACS execution. Validate customer preference SKIPPED behavior cannot be overridden by recovery.

## 43. Phase H — delivery activation

Enable Order delivery-status consumer before Integration publisher, webhook, reconciliation, tracking or provider booking. Activate real booking last.

## 44. Phase H — Cashfree activation

Enable signed webhook receipt and processing first. Validate idempotency and amount/currency checks. Enable payment creation only after webhook readiness. Enable refunds after payment production evidence.

## 45. Phase H — subscription activation

Activate occurrence generation, billing generation, billing publisher, payment intents, payment-status consumer and order fulfillment in dependency order. A customer must authorize hosted payment; no silent debit is permitted.

## 46. Phase H — financial ledger activation

Activate ledger consumers after upstream order/payment/refund events are stable. Verify idempotency, balancing and retry/dead-letter evidence before operational use.

## 47. Phase H — launch policy activation

Create an approved versioned policy, review values, activate it through ADMIN audit and only then enable checkout enforcement.

## 48. Phase I — authorization testing

Test anonymous, customer, chef and administrator access against every protected route. Confirm 401, 403, object ownership and self-suspension protections.

## 49. Phase I — security testing

Run dependency, static, container and endpoint security checks. Verify no secrets, tokens, raw provider payloads or customer identifiers are logged.

## 50. Phase I — load testing

Run a controlled profile for the initial 50–100 concurrent-user target. Measure p50, p95, p99, error rate, DB connections, CPU, memory, queue latency and Redis behavior.

## 51. Phase I — failure testing

Test Redis unavailable, Service Bus retry/dead-letter, provider timeout, webhook replay, database transient failure and one unhealthy Container App revision.

## 52. Phase I — backup and restore

Restore production-like backups into an isolated environment. Verify Flyway history, owned schemas and critical records. Record recovery time and data-loss window.

## 53. Phase I — controlled provider tests

Use low-risk, approved test identities and low-value payment/refund where required. Never use a real customer without explicit operational approval.

## 54. Phase I — administrator workflow tests

Test chef review, subscription management, operational investigations, account intervention and notification recovery using ADMIN JWTs and mandatory audit reasons.

## 55. Phase J — go-live review

Go-live requires evidence from all eight workstreams and explicit approval for product-owned values and provider readiness.

## 56. Phase J — CDN dependency

CDN/Front Door and custom-domain production cutover begin only after backend origin health, APIM routing, web build and security headers are proven. That is the next separate work package.

## 57. Phase J — DNS and certificates

DNS records and certificate bindings are manual owner actions. Do not change them during backend pipeline troubleshooting.

## 58. Emergency rollback order

Disable upstream execution and provider flags, stop publishers, preserve consumers long enough to drain safe messages, roll back APIM/web where required, restore previous service revisions and investigate with audit evidence.

## 59. Data-preservation rule

Rollback must not delete payment, refund, order, notification, inbox, outbox, dead-letter, audit or intervention evidence.

## 60. Final evidence package

Archive source SHA, PR merge list, CI runs, image digests, revisions, Flyway history, APIM policies, Service Bus configuration, flag snapshots, provider evidence, security/load/restore reports and approval record.

## 61. Manual actions required

Azure Portal or CLI: resource/identity/network checks and billable resource review. Azure DevOps: secret variables and pipeline execution. Firebase Console: provider/service-account configuration. Cashfree and delivery consoles: credentials and callback registration. DNS/CDN actions remain deferred.

## 62. Billing warning

Provisioning Redis, Front Door/CDN, additional Container App capacity, PostgreSQL tiers, Service Bus tiers or monitoring retention can create charges. Review SKU and startup credits before execution.

## 63. Current status

The source preparation is complete after PR 107. CI, merge, deployment, migrations, Azure configuration, activation and production tests are deliberately not claimed as complete until their evidence exists.
