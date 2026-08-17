# Craves Subscription Production Completion — Engineering Handover

Date: 2026-08-11
Branch: `feature/subscription-production-completion`
Architecture basis: CRV-ARCH-HLD-001 v1.0 subscription lifecycle plus prior approved Craves subscription decisions.

## Scope

This change turns the previous prepaid subscription foundation into an administrator-managed meal-subscription module with production-safe lifecycle, schedule, audit, idempotency, occurrence generation and web/admin runtime surfaces. It does not activate Cashfree, delivery-provider execution or other external provider stages.

## Product authority

Subscription plans are administrator-managed. CHEF users cannot create, list-all or change plan status. Administrators assign an approved chef to a draft plan, define its meal schedule, define lifecycle policy and activate those artifacts before the plan can be made ACTIVE.

No pricing, commission, unused-meal credit, refund, holiday, cancellation-cutoff, payout or compliance rule is inferred by code. Policy values and policy-reference strings are administrator inputs.

## Backend completion

- Admin-only plan management.
- Active plan readiness requires assigned chef, active meal schedule and active lifecycle policy.
- Customer enrollment requires a saved delivery address.
- Enrollment uses a required idempotency key with a database uniqueness guarantee.
- Customer self-service pause, resume, cancel and skip are policy gated.
- Policy cutoff/lead-time enforcement is server-side.
- Skip requests are auditable and can be registered before occurrence generation.
- Date-level customer skip is applied atomically to all eligible meal slots on the date.
- Pause/cancel prevents undispatched future occurrences from reaching recurring order dispatch.
- Admin subscription list uses bounded keyset pagination.
- Admin status history is queryable.
- Public schedule/policy reads expose only active runtime configuration.
- Admin schedule edits use a shadow draft so the currently active schedule remains live until atomic activation.

## HLD meal-slot alignment

Occurrence identity is now `(subscription_id, service_date, meal_slot_code)` rather than the old date-only uniqueness rule. Schedule items include an administrator-defined `meal_slot_code` and per-slot `service_time`. Multiple meal slots on the same service date produce separate duplicate-safe occurrences.

## Database migrations

- `V8__subscription_product_runtime_hardening.sql`
  - enrollment idempotency
  - policy/version/audit tables
  - skip-request table
  - operational indexes
- `V9__subscription_meal_slots.sql`
  - schedule item meal slots and per-slot service time
  - occurrence meal-slot identity
  - slot-aware unique constraint and indexes
  - legacy rows migrate to technical `LEGACY` slot
- `V10__subscription_schedule_drafts.sql`
  - zero-downtime schedule draft and draft-item tables
  - migration of old DRAFT schedules into shadow-draft storage

## Customer web completion

- retry-safe subscription enrollment idempotency key
- plan schedule BFF with Catalog item-name enrichment
- active policy BFF
- occurrence list BFF
- resume and skip BFFs
- customer detail workspace shows lifecycle policy, admin-managed schedule, meal slots and generated occurrences
- pause/resume/cancel/skip controls appear only when state/policy permit them
- list page no longer exposes invalid pause controls for payment states

## Admin web completion

- create and manage administrator-owned plans
- assign approved chef
- configure weekly/monthly meal schedule
- configure multiple meal slots and menu items
- save schedule drafts without replacing the active schedule
- activation reason and server-side Catalog revalidation
- configure versioned customer lifecycle policy
- explicit pause/resume/cancel/skip cutoffs
- external holiday/unused-meal/refund policy references
- plan readiness display
- scalable subscription operations list with status/plan filtering and keyset pagination
- audited status-history view

## APIM completion

The APIM configuration/status/rollback scripts now cover:

Public subscription operations:
- list plans
- get plan
- get active schedule
- get active policy

Authenticated customer operations:
- enroll
- list/get subscriptions
- list occurrences
- pause
- resume
- cancel
- skip service date

Admin plan operations:
- list/create/status
- schedule get/save/activate
- policy get/save/activate
- readiness

Admin subscription operations:
- keyset list
- status history
- audited status change

Existing chef-review operations are retained by the combined backoffice APIM script.

## External stages intentionally fail closed

The following runtime flags stay disabled until their separate controlled activation and provider contracts are approved/tested:

- `CRAVES_SUBSCRIPTION_BILLING_GENERATOR_ENABLED=false`
- `CRAVES_SUBSCRIPTION_BILLING_PUBLISHER_ENABLED=false`
- `CRAVES_SUBSCRIPTION_PAYMENT_STATUS_CONSUMER_ENABLED=false`
- `CRAVES_SUBSCRIPTION_ORDER_REQUEST_WORKER_ENABLED=false`
- `CRAVES_SUBSCRIPTION_ORDER_PUBLISHER_ENABLED=false`

Occurrence generation is code-complete, but production activation must be deliberate after migrations, schedule/policy smoke tests and runtime verification.

## Known architecture dependency not invented here

The HLD calls for subscription-capacity validation/reservation before plan sale and recurring generation. The currently reviewed repository does not expose a subscription-specific capacity reservation contract that can be safely integrated without inventing behavior. Therefore this change preserves the gap explicitly rather than creating a fake capacity API. Capacity remains a production-scale dependency for the final architecture exit gate.

## Manual steps required

### Azure / runtime

1. Deploy Subscription Service through the runtime-preserving service pipeline.
2. Verify the new Flyway migrations applied successfully.
3. Verify Container App latest revision is Ready/Healthy.
4. Keep provider/payment/order automation flags unchanged during the first deploy.
5. Configure APIM using the controlled script only after backend health is green.
6. Deploy customer web after backend/APIM contract verification.

### Azure DevOps

Use the established service connection:

`Craves-Dev-Service-Connection`

Do not add application secrets to the deployment pipeline. Existing service pipelines are expected to preserve Key Vault-backed runtime configuration.

### APIM

Run the configuration script with `CONFIRM_APIM_WRITE=true`, then run the status script. The rollback script removes only the named subscription/backoffice operations and retains API containers.

### Secrets

No new browser secret is required. Do not paste Firebase, DB, Service Bus, Cashfree or Azure credentials into chat or source control.

## Production smoke sequence

1. Admin creates a DRAFT weekly or monthly plan.
2. Admin assigns an approved chef.
3. Admin saves meal schedule with real Catalog menu-item UUIDs, day, meal-slot code, service time, quantity and sequence.
4. Admin activates the schedule with an operational reason.
5. Admin saves lifecycle policy with explicit enabled actions and cutoffs.
6. Admin activates the policy.
7. Readiness must show chef/schedule/policy ready.
8. Admin sets plan ACTIVE.
9. Customer sees the plan and its meal schedule.
10. Customer enrolls using a saved delivery address; retrying the same request with the same idempotency key must not create a second row.
11. Customer sees the subscription detail.
12. For core smoke while payment activation is still deferred, authorized admin may move the test subscription to ACTIVE with an explicit audit reason.
13. Validate pause, resume, skip and cancel only within the configured policy windows.
14. Validate skip history/audit and no order-eligible occurrence survives a pause/cancel when it should not.
15. Validate admin keyset list and history.

## Scale design

The module now uses bounded list sizes, database uniqueness for enrollment and occurrence idempotency, `FOR UPDATE SKIP LOCKED` worker claims, stale-lock recovery, schedule shadow drafts, keyset admin pagination and indexed lifecycle queries. This is a scale-safe source design, not a claim that prod-low is certified for one million concurrent users. Final scale certification still requires load testing, PostgreSQL sizing/partition review, Redis strategy, autoscaling and observability work.

## Validation required before merge/deploy

- Spring Boot compile/tests for Subscription Service
- Flyway migration validation
- Next.js lint/typecheck/tests/build
- APIM script syntax/static validation
- repository CI checks
- controlled runtime smoke after deployment

## Rollback

Application deployment rollback remains image-only through the hardened service pipeline. Database migrations are forward-only; do not attempt ad-hoc Flyway rollback. APIM named operations can be removed with the provided rollback script. Schedule rollout itself is safe because active configuration remains intact while a new draft is edited.

## Deferred external work

- Cashfree production activation/payment lifecycle
- recurring order publisher activation to Order Service
- delivery/provider integrations
- final capacity reservation integration once the approved contract exists
- Redis-based distributed protections/caching
- final 1M-scale certification

These are explicit later-stage dependencies, not hidden hardcoded behavior in this module.
