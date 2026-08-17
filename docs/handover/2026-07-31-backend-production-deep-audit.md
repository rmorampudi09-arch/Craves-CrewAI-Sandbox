# Craves backend production deep-audit handover

**Document date:** 31 July 2026  
**Repository:** `rmorampudi09-arch/Craves-Build-platform`  
**Stack branch:** `feature/backend-production-readiness-completion`  
**Draft pull request:** #107  
**Target:** Controlled launch for approximately 50–100 concurrent users  
**Runtime changes performed while preparing this handover:** None  
**Pipelines executed while preparing this handover:** None  
**Azure, APIM, database, Firebase, Redis, Cashfree, ACS or delivery-provider mutations performed:** None

---

## 1. Purpose

This handover records the final source-level deep audit performed before Craves starts its CI, merge, Azure deployment and CDN execution phase.

## 2. Meaning of production ready

Source-level production readiness means code, tests, migrations, deployment pipelines, activation controls, rollback controls and runbooks are present and fail closed. It does not mean the code has already passed Azure DevOps, been merged, deployed or field-tested.

## 3. Scope

The audit covers all seven Spring Boot services, administrator Next.js surfaces, APIM operations, Redis security controls, notification delivery, Cashfree payment boundaries, deployment variables and Azure Container App references.

## 4. Services in scope

```text
Auth
User-Chef
Catalog
Order
Subscription
Integration
Notification
```

## 5. Frontend scope

Only administrator web surfaces and their server-side BFF contracts are included. Customer and chef product UX work remains outside this backend production audit.

## 6. No runtime execution

No pipeline was queued, no PR was merged, no image was pushed and no Azure resource was changed during this audit.

## 7. Why pipelines were not run

The owner requested source completion and cross-checking first. Pipeline execution and CDN setup are deliberately reserved for the next controlled phase.

## 8. Branch discipline

All changes remain on the stacked feature branch and draft PR. `main` was not modified.

## 9. Local executable evidence

The strict administrator TypeScript contract harness compiled with the available TypeScript compiler and passed six executable Node tests with zero failures.

## 10. APIM local evidence

Account-intervention and notification-recovery APIM scripts passed shell syntax checks, XML parsing and mocked Azure CLI positive/fail-closed scenarios.

## 11. Environment limitation

The local container cannot resolve GitHub/Maven internet dependencies, so a full seven-service Maven build cannot honestly be claimed locally.

## 12. Mandatory full compile gate

`azure-pipelines-backend-production-readiness-final.yml` runs `mvn -B -ntp clean verify` for all seven services and fails the release when any module fails.

## 13. Mandatory administrator web gate

The same final pipeline runs dependency installation, TypeScript checking, tests and Next.js production build.

## 14. Test skipping prohibited

All seven service deployment pipelines now run Maven tests. The former Notification `-DskipTests` behavior was removed.

## 15. Test result publication

The final Java gate publishes Surefire JUnit reports through Azure DevOps.

## 16. Administrator contract runner correction

New tests originally referenced Vitest although the repository uses Node's built-in test runner. Tests were converted to `node:test` and `node:assert/strict`.

## 17. UUID normalization correction

Administrator account and notification UUIDs are normalized to lowercase before comparison with Java UUID responses.

## 18. Account response correlation

Account BFF responses must match the requested identity UUID. Mutation responses must also match the requested action.

## 19. Notification response correlation

Notification retry responses must match the requested notification UUID.

## 20. Strict account statuses

Only `ACTIVE` and `SUSPENDED` account states are accepted by the browser contract.

## 21. Strict provider statuses

Accepted account provider states are bounded to `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, `DEAD_LETTER`, `SUPERSEDED` and `NOT_REQUIRED`.

## 22. Strict notification statuses

Notification administration accepts only `FAILED`, `DEAD_LETTER` and the post-requeue `PENDING` state.

## 23. Numeric validation

Notification list limits reject exponent, decimal, empty, negative and out-of-range forms.

## 24. Browser privacy reduction

Recipient identity IDs, request keys, provider payloads, Firebase UIDs, raw phone values and access tokens are not exposed through the administrator browser contracts.

## 25. Same-origin enforcement

Administrator mutations continue to use same-origin BFF protection and HTTP-only Craves sessions.

## 26. Backend authorization authority

The Next.js shell never grants an administrator role. Each owning Spring service revalidates the ADMIN role.

## 27. Account idempotency defect found

A repeated suspend/reactivate request previously returned early without recording a fresh audit event or provider synchronization request.

## 28. Account idempotency correction

Every explicit administrator intervention now creates immutable audit evidence and provider work. Token version/session mutation occurs only when local account state changes.

## 29. Canonical reactivation audit action

The action name is normalized to `ACCOUNT_REACTIVATED`.

## 30. Self-suspension protection

An administrator still cannot suspend their own identity.

## 31. Token revocation on state change

A real local account state change increments token version and revokes active Craves refresh sessions transactionally.

## 32. Firebase ordering risk found

Multiple replicas could previously claim different Firebase actions for the same identity and complete them out of order.

## 33. Firebase serialization migration

`V5__serialize_account_provider_sync.sql` adds `SUPERSEDED`, normalizes interrupted processing leases and creates one-processing-row-per-identity enforcement.

## 34. Firebase identity claim locking

Provider claims lock the owning `auth_identity` row with `FOR UPDATE ... SKIP LOCKED`.

## 35. Newest intervention selection

The provider claim selects only the newest due intervention for each identity.

## 36. Stale pending supersession

A new intervention marks prior `PENDING` and `FAILED` work for that identity as `SUPERSEDED`.

## 37. Current-state provider convergence

The Firebase worker now reads current local identity status before applying the external disabled state, instead of trusting an old historical action.

## 38. Firebase refresh revocation

When current local state is suspended, Firebase refresh tokens are revoked after the user is disabled.

## 39. Provider lease completion safety

Provider completion requires the expected lock token and `PROCESSING` status.

## 40. Missing Firebase UID behavior

A missing Firebase UID fails the provider work item and follows bounded retry/dead-letter behavior instead of silently completing.

## 41. Account regression tests

Tests cover repeated interventions, supersession SQL, identity-serialized claim SQL and canonical audit names.

## 42. Notification dead-letter defect found

The dead-letter projection used `ON CONFLICT DO NOTHING`, leaving stale error/count evidence after repeated failures.

## 43. Notification dead-letter correction

The unique projection now updates final error code, final error message, final attempt count and timestamp while immutable attempt history remains preserved.

## 44. Notification regression test

A repository test verifies the conflict-update behavior and expected values.

## 45. Payment boundary defect found

Disabling production payment execution did not disable sandbox payment-order creation.

## 46. Webhook boundary defect found

Disabling the Cashfree webhook worker did not prevent the public endpoint from persisting new webhook receipts.

## 47. Explicit payment API gate

`CRAVES_PAYMENT_ORDER_API_ENABLED=false` gates customer payment-order creation, verification and subscription payment-order creation.

## 48. Explicit webhook ingress gate

`CRAVES_CASHFREE_WEBHOOK_INGRESS_ENABLED=false` blocks Cashfree webhook receipt before persistence.

## 49. Read-only payment access

Owned local payment-order lookup remains available while mutation/external-execution gates are disabled.

## 50. Payment gate tests

`PaymentApiPropertiesTest` verifies both gates default closed and open only through explicit activation.

## 51. Cashfree staged activation

Cashfree webhook ingress and worker are configured first. Payment-order API and real production execution are enabled only after readiness evidence passes.

## 52. Cashfree rollback

Rollback disables payment API, production execution, webhook ingress, webhook worker and refund execution/reconciliation without deleting financial evidence.

## 53. APIM operation shape defect

Earlier APIM status checks proved policy presence but did not prove exact HTTP method or URL template.

## 54. APIM exact read-back

Account and notification APIM rollout/status scripts now verify operation method and URL template exactly.

## 55. APIM XML validation

APIM CI parses policy XML before any Azure task.

## 56. APIM inherited-backend protection

Scripts reject inherited `backend-id` routing conflicts.

## 57. APIM authentication boundary

Gateway policy checks Bearer syntax; the owning Spring service remains authoritative for token and ADMIN validation.

## 58. APIM response hardening

Administrator responses receive no-store/no-cache, nosniff and frame-deny headers.

## 59. APIM unauthenticated smoke

Rollout verifies an unauthenticated request returns HTTP 401.

## 60. Canonical Azure resource inventory

`config/production/azure-resource-inventory.json` records the exact existing seven Container App names, resource group, ACR, APIM and Service Bus namespace.

## 61. Shortened real resource names

User-Chef, Catalog, Subscription, Integration and Notification use intentional shortened names required by existing Azure resources.

## 62. Resource-name drift found

Redis and Notification pipelines referenced nonexistent `...-prodlow` names for shortened Container Apps.

## 63. Resource-name correction

All new Redis, Notification recovery, provider and APIM paths now use the canonical inventory.

## 64. Stale-name rejection

`verify-azure-resource-inventory-references.sh` rejects known stale Container App names in runtime pipelines and scripts.

## 65. Azure secret-name constraint

New CLI-managed Container App secret keys use lowercase, digits and hyphens and remain within the documented 20-character CLI limit.

## 66. Canonical secret names

```text
db-password
jwt-verify-pem
jwt-private-pem
firebase-admin-json
internal-secret
redis-url
acs-email-conn
cashfree-client-id
cashfree-client-key
servicebus-conn
```

## 67. Existing Key Vault secret preservation

The already-provisioned `craves-internal-service-secret` Key Vault reference is preserved where it is already used. It is not recreated through `az containerapp secret set`.

## 68. Secret-name validation

`verify-container-app-secret-names.sh` parses single-line and multiline Azure CLI secret commands and validates create/reference consistency.

## 69. Plaintext database password prevention

Every service deployment binds `SPRING_DATASOURCE_PASSWORD` through `secretref:db-password`.

## 70. Plaintext JWT prevention

JWT private/verification material is stored under short Container App secrets and referenced by `secretref`.

## 71. Auth deployment hardening

Auth runs tests, verifies AcrPull, stores Firebase/JWT/database/internal secrets, disables account worker/API, Redis publisher/consumer and rate limiting, then verifies the exact healthy image.

## 72. User-Chef deployment hardening

User-Chef runs tests, secret-binds database/JWT, validates the shared Key Vault secret, resolves live Auth health and disables direct notification, notification outbox and revocation.

## 73. Catalog deployment hardening

Catalog runs tests, secret-binds database/JWT, preserves media configuration and disables revocation.

## 74. Order deployment hardening

Order runs tests, validates User-Chef and the shared Key Vault secret, and disables launch policy, direct notification, notification outbox, chef acceptance, refund/delivery consumers, subscription-order paths, domain-event paths and revocation.

## 75. Subscription deployment hardening

Subscription runs tests, secret-binds database/JWT/internal secret, and disables occurrence, billing, payment-status, order-request/order-publisher and revocation paths.

## 76. Integration deployment hardening

Integration runs tests, secret-binds database/JWT/internal key, keeps provider environment sandbox and disables payment API, webhook ingress/worker, delivery intelligence/commands/reconciliation/webhook/tracking/publisher, refund paths, subscription-payment paths, Borzo and revocation.

## 77. Notification deployment hardening

Notification now runs tests, secret-binds database/JWT/internal key and disables Service Bus, delivery worker, push, email, recovery API and revocation.

## 78. Immutable image evidence

Every deployment uses an explicit immutable build tag, rejects redeploying the same current image and records the previous image for rollback.

## 79. Revision evidence

Every service deployment polls until latest revision equals latest ready revision, running status is Running, health state is Healthy and the exact expected image is active.

## 80. Live health evidence

Every service deployment calls `/actuator/health` and requires `status=UP`.

## 81. Failure diagnostics

Unhealthy revisions attempt to emit bounded Container App console logs before failing the pipeline.

## 82. Disabled-flag read-back

Each service deployment reads its critical feature flags from the resulting Container App template and fails unless required values are false.

## 83. No-op environment protection

`verify-deployment-environment-bindings.sh` requires each deployment variable to be consumed by its owning service source, except standard Spring runtime variables.

## 84. Ineffective flag removed

`CRAVES_DELIVERY_PROVIDER_EXECUTION_ENABLED` was removed because Integration did not consume it. Real provider gating uses command/reconciliation plus Borzo enable/approval flags.

## 85. Delivery intelligence source default

`CRAVES_DELIVERY_INTELLIGENCE_ENABLED` now defaults false in Integration `application.yml`.

## 86. Direct notification source default

Order and User-Chef legacy direct dispatch now default false in Java and YAML, with regression tests.

## 87. Launch side-effect manifest

The production manifest inventories every real side-effecting worker, consumer, publisher, mutation API and provider approval flag that must default disabled.

## 88. Redis dependency gap found

Redis activation existed, but no source-controlled pipeline bound one validated Redis URL to all seven services.

## 89. Redis binding pipeline

`azure-pipelines-backend-redis-secret-binding.yml` authenticates and PINGs Redis before changing any Container App.

## 90. Redis secret binding

The pipeline writes `redis-url`, binds `SPRING_DATA_REDIS_URL=secretref:redis-url` to all seven services and keeps publisher, consumers and rate limiter disabled.

## 91. Redis staged activation

Publisher activates first, consumers second and Auth rate limiting last with explicit positive thresholds.

## 92. Redis rollback

Rollback disables rate limiting, every consumer and the publisher in reverse order while preserving durable revocation evidence and Redis keys.

## 93. Notification provider secret gap found

Notification activation expected overlength pre-existing Firebase/ACS secret names.

## 94. Notification provider correction

The activation pipeline accepts secure Azure DevOps variables and creates `firebase-admin-json` and `acs-email-conn` itself.

## 95. Notification provider stages

Push configuration, email configuration and worker activation remain separate explicit stages.

## 96. Notification worker prerequisite

The worker cannot start unless at least one provider is enabled and its required secret reference/sender is present.

## 97. Azure DevOps variable inventory

`config/production/azure-devops-variable-inventory.json` documents required non-secret, secret and pre-existing Container App secret dependencies.

## 98. Variable preflight

`azure-pipelines-production-variable-preflight.yml` validates deployment variables without any Azure mutation.

## 99. JDBC validation

The preflight requires `jdbc:postgresql://` URLs and rejects embedded passwords/user credentials.

## 100. Firebase validation

The preflight decodes the service-account JSON, requires `type=service_account`, validates mandatory fields and verifies project ID equality.

## 101. JWT keypair validation

The preflight validates private/public PEM structures, derives the public key from the private key and requires identical DER fingerprints.

## 102. Internal secret equality

Core preflight requires `CRAVES_INTERNAL_SERVICE_SECRET` and `CRAVES_INTERNAL_SERVICE_KEY` to match without printing either value.

## 103. Provider secret equality

The all-dependencies scope additionally requires the internal smoke secret to match the shared internal secret.

## 104. Redis variable validation

The all-dependencies scope validates Redis URL structure; the binding pipeline performs authenticated connectivity.

## 105. ACS variable validation

The all-dependencies scope validates the expected ACS endpoint/access-key connection-string structure.

## 106. Variable confidentiality

Secret values are supplied through Azure DevOps environment injection, command tracing is disabled and values are never printed.

## 107. Seven-service deployment contract gate

`verify-seven-service-deployment-contracts.sh` requires tests, immutable image handling, secret references, disabled flags, revision evidence, health probes and rollback-image output.

## 108. Test-skip rejection

The deployment contract gate rejects `-DskipTests` and Maven test skipping.

## 109. Plaintext-key rejection

The deployment contract gate rejects database passwords or JWT key material passed as plain environment macro values.

## 110. Final non-deploying preflight

`azure-pipelines-backend-production-readiness-final.yml` runs source integrity, all seven Java builds/tests and administrator web verification without pushing images or changing Azure.

## 111. Final source integrity contents

The source stage validates JSON, shell syntax, APIM XML, resource names, secret names, deployment contracts and source-to-environment bindings.

## 112. Mandatory nature

Java and web stages are no longer optional parameters. A successful final gate means both actually ran.

## 113. Service Bus model

Established domain-event paths use managed identity and verify Service Bus Data Sender/Receiver roles before activation rather than adding a duplicate connection-string path.

## 114. Service Bus order

Receivers/subscriptions must be healthy before corresponding publishers are enabled.

## 115. Database migration order

Deploy services with workers/providers disabled, allow owned Flyway migrations, verify Flyway history/checksums, then proceed to dependency activation.

## 116. Auth migration prerequisite

The Firebase account-intervention worker must remain disabled while the V5 serialization migration is applied.

## 117. Cashfree order

Webhook ingress → webhook worker/readiness → payment API and real payment execution.

## 118. Notification order

Push/email configuration → controlled provider test → delivery worker.

## 119. Redis order

Validated secret binding → publisher → all consumers → rate limiter.

## 120. Account intervention order

Local account intervention API → verify local state/session revocation → Firebase worker.

## 121. Delivery order

Order delivery-status receiver → Integration command/reconciliation/webhook/tracking/status publisher → Borzo execution approval.

## 122. Refund order

Cashfree webhook readiness → refund consumer/status paths → reconciliation → controlled provider execution.

## 123. Subscription order

Schedule/occurrence/billing dependencies → payment consumer/status → order request/fulfillment paths.

## 124. Rollback principle

Disable upstream producers/external execution first, then internal workers/consumers, while preserving databases, audit, inbox, outbox and dead-letter evidence.

## 125. Pipeline execution order

```text
1. Production variable preflight: core
2. Production variable preflight: all_dependencies
3. Final non-deploying production readiness pipeline
4. Remaining module-specific CI pipelines
5. Parent-first stacked merges
6. Auth deployment
7. User-Chef deployment
8. Catalog deployment
9. Order deployment
10. Subscription deployment
11. Integration deployment
12. Notification deployment
13. Flyway/revision/health evidence review
14. Redis binding
15. Service Bus/APIM guarded configuration
16. Provider configuration
17. Staged activation
18. Security/load/restore/provider field tests
19. Azure Front Door/CDN/DNS/certificate phase
```

## 126. Manual Azure DevOps variables

All names are documented in `config/production/azure-devops-variable-inventory.json`. Secret values must be placed only in secret variables or approved Azure secret stores.

## 127. Manual Azure Portal actions

Possible actions include role assignment verification, Key Vault access, Container App secret-reference inspection, PostgreSQL backup confirmation and Redis/network reachability.

## 128. Billing-sensitive actions

Provisioning or scaling Redis, Front Door/CDN, PostgreSQL, Container Apps, Service Bus or APIM can consume Azure credits and must be explicitly reviewed before execution.

## 129. Firebase Console actions

Production Firebase service account/provider configuration and authorized domains remain owner-controlled console actions.

## 130. Cashfree actions

Production KYC, credential generation and webhook registration remain owner-controlled Cashfree actions.

## 131. ACS actions

Verified sender/domain configuration and production connection string remain owner-controlled Azure Communication Services actions.

## 132. Delivery-provider actions

Production Borzo/other provider credentials, callback URL registration and Hyderabad field testing remain external dependencies.

## 133. Business-rule hard stop

No radius, minimum order, commission, settlement, refund, subscription grace or FSSAI values were invented.

## 134. Scaling boundary

The current target is approximately 50–100 concurrent users. One-million-concurrent scaling requires a later capacity, data-partitioning, network and load-engineering phase.

## 135. What is verified now

Source contracts, guard logic, static pipeline controls, selected executable TypeScript/APIM harnesses, JSON inventories and safety-default design were verified in this audit.

## 136. What is not yet verified

Full Maven execution, real Azure DevOps YAML compilation, Docker builds, Flyway against production databases, Container App revisions, APIM writes, Redis connectivity and provider calls have not run yet.

## 137. Honest residual risk

No engineering process can prove absolute absence of defects before execution. The remaining risk is now intentionally concentrated in CI/runtime/provider validation, with fail-closed controls and rollback evidence required.

## 138. Definition of pipeline success

A pipeline is successful only when tests pass, the exact expected image is active, the latest revision is ready/healthy, live health is UP and required secret references/disabled flags read back correctly.

## 139. Definition of deployment failure

Any unresolved variable, stale resource name, missing role, plaintext sensitive value, unhealthy revision, wrong image, unexpected enabled flag, migration failure or failed health probe stops progression.

## 140. Next handover entry point

Begin with `azure-pipelines-production-variable-preflight.yml` using `scope=core`. Do not start a service deployment before core variables and the final non-deploying gate are green.

---

# Manual steps required

- [ ] Create/verify Azure DevOps variables listed in `config/production/azure-devops-variable-inventory.json`.
- [ ] Mark every secret variable secret; never paste values into chat or Git.
- [ ] Verify `AZURE_SERVICE_CONNECTION=Craves-Dev-Service-Connection`.
- [ ] Verify the three database URLs/users/passwords.
- [ ] Verify Firebase project ID and service-account JSON.
- [ ] Verify JWT private/public base64 values are one keypair.
- [ ] Verify all internal-secret variables contain the same value.
- [ ] Verify storage endpoint and media public URL.
- [ ] Verify existing Key Vault-backed `craves-internal-service-secret` on User-Chef and Order.
- [ ] Verify Cashfree Container App secrets and production webhook registration later.
- [ ] Verify Redis URL and network path later.
- [ ] Verify ACS sender/domain and connection string later.
- [ ] Review any billable Azure resource or scaling change before running it.

# Local testing limitations

The current execution environment lacks repository clone/Maven dependency access. Full Java and Next.js production verification is therefore encoded as mandatory Azure DevOps gates rather than falsely reported as completed.

# Final source status

The identified source-level production blockers found during this audit were corrected on the feature branch. The next phase is controlled CI/runtime execution, not another known backend feature-development batch.
