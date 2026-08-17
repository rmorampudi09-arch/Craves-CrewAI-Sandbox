# Craves backend launch-critical completion handover

**Document date:** 31 July 2026  
**Repository:** `rmorampudi09-arch/Craves-Build-platform`  
**Scope:** Backend only  
**Frontend:** Explicitly excluded  
**Runtime changes performed while creating this document:** None  
**Pipeline executions performed:** None  
**Azure/APIM/provider changes performed:** None

---

## 1. Purpose

This document records the backend code completed for the controlled Craves launch target and provides the exact later sequence for CI, merge, deployment, activation and rollback.

## 2. Production-readiness meaning

“Code complete” in this document means the source, migrations, tests/gates, pipeline definitions and runbooks exist in Git. It does not mean the code has already passed CI, been merged, deployed or activated.

## 3. Launch target

The immediate backend target remains a controlled launch supporting approximately 50–100 concurrent users. Capacity for approximately one million concurrent users requires a later scaling phase.

## 4. Architecture baseline

The implementation remains aligned to the approved Craves HLD service boundaries:

```text
Auth
User-Chef
Catalog
Order
Subscription
Integration
Notification
```

## 5. Database ownership

Each service continues to access only its owned schema/database. Cross-service workflows use HTTP contracts or Service Bus events rather than database cross-reading.

## 6. Event reliability

Financial, delivery and subscription workflows use transactional outbox, durable inbox, idempotency keys, row locks, bounded retries and local dead-letter evidence.

## 7. External execution policy

Cashfree, delivery providers, Firebase administrative mutations, notification providers and Redis enforcement remain separately controlled and disabled until their staged activation steps are approved.

## 8. Business-rule policy

No delivery radius, minimum order, commission, settlement, refund credit, subscription grace period, FSSAI rule or payout value is invented by source code.

## 9. Branch discipline

All new work is held in stacked feature branches and draft pull requests. `main` was not modified by this batch.

## 10. Pipeline discipline

Every activation pipeline uses `trigger: none` and `pr: none`. Execution requires an explicit confirmation parameter and the established Azure DevOps service connection variable.

## 11. Azure service connection

```text
AZURE_SERVICE_CONNECTION=Craves-Dev-Service-Connection
```

The credential remains in Azure DevOps configuration and must never be committed or pasted into chat.

## 12. Launch-critical batch overview

The current backend launch-critical stack is represented by PRs #84 through #100, excluding temporary feature-only synchronization PR #93.

## 13. PR #84 — launch policy registry

Adds administrator-owned, versioned serviceability and minimum-order policies without supplying default product values.

## 14. PR #84 checkout enforcement

Checkout enforcement remains disabled until an approved active policy exists and the enforcement flag is explicitly enabled.

## 15. PR #84 safety flag

```text
CRAVES_LAUNCH_POLICY_ENFORCEMENT_ENABLED=false
```

## 16. PR #84 manual decision

An administrator must supply the approved radius and minimum-order values before enforcement can be activated.

## 17. PR #85 — delivery production readiness

Adds staged delivery-provider activation, dependency checks, emergency rollback and fail-closed verification.

## 18. PR #85 downstream-first rule

The Order delivery-status consumer must be healthy before the Integration delivery publisher, webhook handling, tracking or provider booking is enabled.

## 19. PR #85 provider boundary

A real delivery provider still requires approved production credentials, callback registration and Hyderabad field validation.

## 20. PR #85 provider flags

Delivery command, reconciliation, webhook processing, tracking reconciliation, status publishing and provider execution remain independently controlled.

## 21. PR #86 — Cashfree production hardening

Adds durable signed webhook receipt, replay/idempotency protection, asynchronous processing, production-readiness evidence and separated payment execution.

## 22. PR #86 webhook-first cutover

Production webhook validation and processing must be proven before real payment creation is enabled.

## 23. PR #86 payment execution boundary

Real Cashfree payment execution has an independent production switch and cannot be enabled merely by changing the environment label.

## 24. PR #86 credential boundary

Cashfree production client ID and secret must remain Key Vault or Container App secret references.

## 25. PR #87 — refund production activation

Adds production refund approval/execution controls, readiness evidence, reconciliation gates and non-destructive rollback.

## 26. PR #87 financial preservation

Refund rollback disables execution and reconciliation but never deletes refund, inbox, outbox or audit rows.

## 27. PR #87 manual dependency

A controlled real low-value payment/refund test is required after Cashfree production credentials and webhook registration are complete.

## 28. PR #88 — subscription schedule definitions

Adds explicit, versioned weekly/monthly plan schedule and menu definitions owned by Subscription Service.

## 29. PR #88 no hidden schedule

A subscription plan cannot generate recurring meal dates from an implicit or client-only schedule.

## 30. PR #89 — occurrence generation

Generates deterministic dated meal occurrences from active subscriptions and active schedule versions.

## 31. PR #89 initial state

Every generated occurrence starts in:

```text
BILLING_PENDING
```

## 32. PR #89 safety flag

```text
CRAVES_SUBSCRIPTION_OCCURRENCE_GENERATOR_ENABLED=false
```

## 33. PR #90 — billing lifecycle

Adds weekly/monthly invoice-cycle generation, immutable amount/currency snapshots and a transactional `SUBSCRIPTION_PAYMENT_REQUESTED` outbox.

## 34. PR #90 idempotency

A unique subscription/cycle-start constraint prevents duplicate invoices.

## 35. PR #90 safety flags

```text
CRAVES_SUBSCRIPTION_BILLING_GENERATOR_ENABLED=false
CRAVES_SUBSCRIPTION_BILLING_PUBLISHER_ENABLED=false
```

## 36. PR #91 — subscription payment intents

Adds Integration-owned payment intents for subscription invoices and customer-authorized Cashfree hosted payment sessions.

## 37. PR #91 no silent debit

The module does not automatically debit a saved mandate or silently charge a customer.

## 38. PR #91 ownership

Before creating or exposing a payment session, Integration Service revalidates that the caller owns the referenced subscription.

## 39. PR #91 webhook routing

Cashfree subscription order references use a deterministic `CRVSUB_` prefix and are routed through the durable webhook processor.

## 40. PR #92 — payment-status consumption

Subscription Service consumes `SUBSCRIPTION_PAYMENT_STATUS_CHANGED` through a durable, idempotent inbox.

## 41. PR #92 immutable validation

Webhook amount and currency must match the immutable invoice snapshot.

## 42. PR #92 occurrence release

Only occurrences whose service dates fall within a paid billing cycle become `READY_FOR_ORDER`.

## 43. PR #92 safety flag

```text
CRAVES_SUBSCRIPTION_PAYMENT_STATUS_CONSUMER_ENABLED=false
```

## 44. PR #93 — feature synchronization only

PR #93 synchronized parent fixes into the payment-status feature branch. It was merged only into a feature branch and did not modify `main`.

## 45. PR #94 — subscription order fulfillment

Adds the complete bridge from a paid recurring occurrence to one idempotent Order Service order and back to occurrence completion.

## 46. PR #94 Subscription side

Subscription Service claims due `READY_FOR_ORDER` occurrences, creates a transactional request outbox and publishes `SUBSCRIPTION_ORDER_REQUESTED`.

## 47. PR #94 Order side

Order Service consumes the request, validates active Catalog menu/kitchen ownership and the active customer-owned address, then stores immutable pickup/drop-off/package snapshots.

## 48. PR #94 one-order rule

A unique subscription occurrence identifier enforces one Order per occurrence.

## 49. PR #94 financial boundary

Subscription Orders store zero immediate customer-charge fields and:

```text
financial_allocation_status=PENDING_POLICY
```

## 50. PR #94 dispatch decision

The dispatch lead has no coded commercial default and must be explicitly configured before the occurrence request worker starts.

## 51. PR #95 — chef financial ledger

Adds administrator-supplied, database-validated earning allocations and settlement recording.

## 52. PR #95 arithmetic control

PostgreSQL validates gross, commission, tax, adjustment and net-payable arithmetic.

## 53. PR #95 chef privacy

A chef can read only their own ledger and settlement state.

## 54. PR #95 payout boundary

No API initiates a bank transfer or calls a payout provider. External payout references are recorded after an authorized operational action.

## 55. PR #95 subscription allocation boundary

Subscription earning allocation remains `PENDING_POLICY` until approved amounts are supplied; no commission or allocation rule is inferred.

## 56. PR #96 — production notification delivery

Adds FCM push and Azure Communication Services Email adapters with durable multi-replica delivery claims.

## 57. PR #96 device ownership

Push device registration/list/deactivation is authenticated and tied to the current identity.

## 58. PR #96 preference enforcement

Disabled customer channel preferences result in `SKIPPED`; provider workers do not override them.

## 59. PR #96 invalid-token cleanup

Permanent invalid FCM tokens are deactivated to prevent repeated provider failures.

## 60. PR #96 provider flags

```text
CRAVES_NOTIFICATION_DELIVERY_WORKER_ENABLED=false
CRAVES_NOTIFICATION_PUSH_ENABLED=false
CRAVES_NOTIFICATION_EMAIL_ENABLED=false
```

## 61. PR #96 SMS boundary

SMS delivery remains blocked. Firebase Phone Authentication continues to own phone OTP.

## 62. PR #96 credential boundary

Firebase service-account JSON and ACS Email connection string/sender address must be secret references and approved console/resource values.

## 63. PR #97 — admin investigations

Adds read-only operational investigation APIs for Order, payment, refund and delivery state.

## 64. PR #97 mandatory reason

Every successful investigation requires a 10–500 character `X-Admin-Reason`.

## 65. PR #97 correlation

Every successful investigation returns a UUID `X-Correlation-ID` and stores it in append-only audit evidence.

## 66. PR #97 privacy

Responses exclude raw provider payloads, webhook signatures, tokens, device tokens and full customer phone/address details.

## 67. PR #97 refund outbox normalization

Integration migration V107 ensures `refund_status_outbox.aggregate_id` consistently represents the owning refund UUID for historical and future rows.

## 68. PR #98 — account intervention

Adds administrator-only account suspension/reactivation with transactional local enforcement and durable Firebase synchronization.

## 69. PR #98 local suspension

The Auth transaction changes status, increments token version, revokes active Craves refresh sessions, appends audit evidence and creates a Firebase work item.

## 70. PR #98 self-protection

An administrator cannot suspend their own identity through this endpoint.

## 71. PR #98 Firebase worker

The durable worker disables/enables the Firebase user and revokes Firebase refresh tokens during suspension.

## 72. PR #98 safety flags

```text
CRAVES_ADMIN_ACCOUNT_INTERVENTION_API_ENABLED=false
CRAVES_ADMIN_ACCOUNT_INTERVENTION_FIREBASE_WORKER_ENABLED=false
```

## 73. PR #99 — notification recovery

Adds typed failed/dead-letter backlog inspection and one-request audited requeue.

## 74. PR #99 requeue states

Only `FAILED` and `DEAD_LETTER` may be requeued.

## 75. PR #99 preference protection

`SKIPPED` requests cannot be requeued because that would override a customer preference.

## 76. PR #99 evidence preservation

Delivery attempts and the original dead-letter row remain intact; requeue adds a separate recovery-audit row.

## 77. PR #99 safety flag

```text
CRAVES_NOTIFICATION_RECOVERY_API_ENABLED=false
```

## 78. PR #100 — Redis security

Adds Auth exchange/refresh abuse protection and distributed token revocation across all seven Spring services.

## 79. PR #100 durable source

Auth migration V4 creates `auth_token_revocation_outbox` and a trigger on identity status/token-version changes.

## 80. PR #100 projection

Auth publishes short-lived Redis values in this format:

```text
<status>|<minimum token version>
```

## 81. PR #100 consumer behavior

Every service performs the Redis check only after its existing JWT authentication accepts a Bearer token.

## 82. PR #100 public routes

Requests without a Bearer header bypass Redis, preserving public Catalog and other anonymous routes.

## 83. PR #100 revoked-token response

A suspended identity or older token version receives HTTP 401.

## 84. PR #100 Redis outage response

When revocation enforcement is enabled with fail-closed behavior, a Redis outage returns HTTP 503 for authenticated protected requests.

## 85. PR #100 rate-limit routes

```text
POST /api/v1/auth/firebase/exchange
POST /api/v1/auth/refresh
```

## 86. PR #100 rate-limit data

Redis rate keys contain a SHA-256 hash of the selected client IP. They never contain the Firebase token, refresh token, JWT, phone number or request body.

## 87. PR #100 explicit thresholds

Production exchange/refresh limits default to zero and must be supplied explicitly when the rate limiter is enabled.

## 88. PR #100 safety flags

```text
CRAVES_TOKEN_REVOCATION_PUBLISHER_ENABLED=false
CRAVES_TOKEN_REVOCATION_ENABLED=false
CRAVES_TOKEN_REVOCATION_FAIL_CLOSED=true
CRAVES_AUTH_RATE_LIMIT_ENABLED=false
```

## 89. PR #100 Redis secret

Every Container App must bind `SPRING_DATA_REDIS_URL` through a secret reference. The rollout pipeline rejects plaintext/missing bindings.

## 90. PR #100 activation phases

```text
PUBLISHER
→ CONSUMERS
→ RATE_LIMITER
```

## 91. PR #100 rollback order

```text
RATE_LIMITER off
→ CONSUMERS off
→ PUBLISHER off
```

## 92. Build order

Run CI in exact stacked order so each child is tested only after its parent head is accepted:

```text
#84
→ #85
→ #86
→ #87
→ #88
→ #89
→ #90
→ #91
→ #92
→ #94
→ #95
→ #96
→ #97
→ #98
→ #99
→ #100
```

## 93. Earlier prerequisite stack

Before the launch-critical batch, complete the earlier pending backend/platform PRs in their established order, including delivery-status downstream consumption, ownership hardening, subscription/admin foundations and release-readiness gates through PR #83.

## 94. Merge policy

For each PR:

1. record the exact head SHA;
2. run the module CI against that SHA;
3. resolve every compile/test/static-gate failure;
4. confirm the head did not move;
5. merge only after parent PRs are merged;
6. deploy only from the merged `main` commit.

## 95. Initial deployment policy

Deploy every merged backend image with all new execution flags false. Let Flyway run, verify health/readiness, inspect logs and confirm the revision before enabling a worker or provider.

## 96. Service Bus rollout policy

For every new event flow:

1. create/verify the filtered subscription;
2. grant managed-identity receiver/sender permissions;
3. enable the downstream consumer first;
4. test one event, duplicate and invalid event;
5. verify active/dead-letter counts;
6. enable the upstream publisher last.

## 97. Cashfree manual work

Manual actions later:

- complete merchant/KYC approval;
- create production credentials;
- store secrets in Key Vault/Container App references;
- register production webhook URL/version;
- verify signature/time/idempotency handling;
- perform a controlled low-value payment/refund;
- reconcile provider and Craves records.

## 98. Delivery-provider manual work

Manual actions later:

- finalize the launch provider;
- complete provider account/KYC/contract;
- create production credentials and callback secret;
- register callback URL;
- test quote/create/cancel/track/webhook;
- perform Hyderabad pickup/drop-off field tests;
- approve operational fallback and cancellation rules.

## 99. Subscription manual work

Manual decisions later:

- create approved plan schedules and prices;
- select billing periods;
- define customer payment/retry messaging;
- configure dispatch lead;
- define pause/skip/cancellation-effective behavior;
- define subscription refund/credit policy;
- supply chef earning allocation values.

## 100. Financial operations manual work

Manual decisions later:

- approve commission and tax-withholding rules;
- approve settlement frequency;
- select payout provider or manual bank process;
- define adjustment approval and segregation of duties;
- validate accountant/finance reconciliation outputs.

## 101. Notification manual work

Manual actions later:

- bind Firebase service account secret;
- confirm Android/iOS FCM configuration;
- verify ACS Email resource and sender domain;
- bind ACS connection string/sender address;
- test push invalid-token cleanup;
- test email delivery/bounce behavior;
- configure channel alerts and dead-letter operations.

## 102. Redis manual and billing-sensitive work

Creating or scaling an Azure Redis service is billable. Before activation:

- select the approved Azure Redis offering/tier;
- decide public/private networking;
- provision and secure the resource;
- bind `SPRING_DATA_REDIS_URL` as a secret reference in all seven apps;
- test TLS/connectivity;
- add latency/availability/connection alerts;
- run the three-phase rollout.

## 103. APIM manual work

APIM operations must be added only after their services are deployed and tested. Retain JWT validation, backend routing, no-store headers, request-size limits and body-trace restrictions for sensitive routes.

## 104. Observability requirements

Before production activation, alert on:

- Container App unhealthy/unready revisions;
- HTTP 5xx/latency/error-rate changes;
- PostgreSQL connection exhaustion;
- Service Bus active/dead-letter growth;
- outbox/inbox retry/dead-letter growth;
- Cashfree webhook failures;
- delivery provider failures;
- notification dead letters;
- Redis latency/unavailability;
- authentication rate limiting.

## 105. Backup requirements

Complete a PostgreSQL backup and restore rehearsal for every owned database/schema before production financial traffic.

## 106. Security requirements

Run dependency scanning, container scanning, secret scanning, API authorization testing, provider-webhook replay testing and an external penetration/security review before public launch.

## 107. Load requirements

Run load tests for the initial launch target across Auth, Catalog discovery, cart/checkout, order views, notification inbox and key event workers. Include database pools, Service Bus handlers and Redis connection pools.

## 108. Million-user risk

The current design is intentionally economical for the first controlled launch. One Redis lookup per authenticated request, single-region low-cost resources and one resource per service are not sufficient evidence for one million concurrent users.

## 109. Million-user future changes

Before high scale, evaluate local revocation near-cache, Redis clustering/zone redundancy, Front Door/APIM edge controls, multi-region service/data strategy, larger connection pools, queue partitioning, autoscaling, load shedding and disaster recovery.

## 110. CDN status

CDN/Azure Front Door remains the next infrastructure phase. It is intentionally not mixed into this backend code batch.

## 111. CDN prerequisites

Begin CDN/Front Door only after:

- backend PR stack passes CI and merges;
- backend services deploy successfully;
- APIM routing and authorization are verified;
- origin hostnames and health probes are stable;
- caching rules identify public versus private content;
- custom domains/DNS/certificates are available.

## 112. CDN security boundary

Never cache personalized API responses, authenticated order/subscription data, payment/refund responses, admin routes or notification inbox content.

## 113. CDN likely scope

CDN/Front Door should focus on static web assets, public images and carefully reviewed public Catalog responses. Blob/image origins require private-origin or signed-access review.

## 114. Current safety state

At document creation time:

- no new pipeline was run;
- no PR from this batch was merged to `main`;
- no Container App was updated;
- no migration was executed;
- no Service Bus subscription/message was changed;
- no APIM operation was changed;
- no Cashfree request was sent;
- no delivery-provider request was sent;
- no notification was sent;
- no Firebase identity was changed;
- no Redis resource/key was created or deleted.

## 115. Code-completion conclusion

All backend launch-critical functional code domains identified in the earlier gap analysis are now represented in Git: delivery policy/provider readiness, payment/refund production controls, recurring subscription fulfillment, chef financial ledger, production notifications, operations/account recovery, abuse protection and release controls.

## 116. Remaining work classification

Remaining work is execution and validation rather than another known launch-critical backend business-code module:

```text
CI fixes
→ stacked merges
→ deployments with flags false
→ migrations
→ Service Bus/APIM/manual cloud setup
→ controlled feature activation
→ provider field tests
→ security/load/restore tests
→ CDN/Front Door phase
```

## 117. Stop condition

Do not declare production launch readiness until every required CI/deployment/manual verification in this handover is evidenced and every unresolved product policy has an approved value or documented fail-closed launch exclusion.
