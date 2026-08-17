# Craves Subscription Capacity Enforcement Handover

Date: 2026-08-11
Branch: `feature/subscription-capacity-enforcement-v2`
Stacked PR: #175 -> `feature/subscription-production-completion` (PR #173 -> `main`)

## Purpose

This module closes the missing subscription-capacity contract without inventing pricing, refund, unused-meal, holiday, delivery, payout or compliance rules.

Capacity ownership is split deliberately:

- Chef owns the capacity values for the chef's kitchen.
- Subscription Service owns admission, reservation, release, overbooking prevention and audit.
- Support/Operations can inspect and freeze new subscription sales during an incident.
- Support cannot increase the chef's declared cooking capacity.
- Subscription/Operations administrators may run audited reservation reconciliation.

The current Catalog model is one kitchen profile per chef identity, so the capacity owner key is `chef_identity_id`. No second kitchen registry is introduced.

## Capacity model

### Recurring slot capacity

For each chef + ISO weekday + `mealSlotCode`, the chef configures:

- `totalCapacityUnits`: total meal units the kitchen can prepare in the slot.
- `subscriptionCapacityUnits`: maximum portion of that total which recurring subscriptions may reserve.
- `salesEnabled`: whether new subscription capacity may be sold for the slot.

`subscriptionCapacityUnits` cannot exceed `totalCapacityUnits`.

### Optional menu-item capacity

A chef may configure a tighter capacity for a specific menu item when that dish has a preparation limit below the whole slot limit. When no menu-item rule exists, the slot capacity remains authoritative.

### Date overrides

Chef can override a future service date/slot, including setting a different capacity or closing the slot. An optional menu-item date override can constrain/close a specific dish on a date.

Date overrides never silently cancel existing committed subscribers. If the new limit is below existing commitments, Craves protects those commitments, blocks new sales and raises a capacity incident.

## Reservation lifecycle

### HOLD

New enrollment creates a bounded capacity hold. Default hold duration is 15 minutes and is operationally configurable through:

`CRAVES_SUBSCRIPTION_CAPACITY_HOLD_MINUTES`

The value is not a commercial entitlement. It is a technical concurrency protection window and is bounded by source validation.

### COMMITTED

Before a subscription becomes ACTIVE, its capacity must be committed. Payment/admin activation cannot bypass this requirement.

If an enrollment hold expired, activation must reacquire capacity atomically. If capacity is no longer available, the subscription is not activated.

### MATERIALIZED

When a dated subscription occurrence is inserted, PostgreSQL atomically changes the matching committed date allocation to `MATERIALIZED` in the same transaction.

### RELEASED / EXPIRED

Capacity is released as follows:

- uncompleted hold -> EXPIRED
- payment failure/cancellation -> RELEASED
- customer pause -> recurring/future capacity RELEASED
- customer cancel/expiry -> recurring/future capacity RELEASED
- resume -> capacity must be reacquired before ACTIVE
- skip -> only the affected service date allocation is RELEASED; future recurring entitlement remains

A database trigger prevents projection/reconciliation from re-reserving a skipped service date.

## Permanent recurring entitlement plus date projection

A finite date projection alone cannot guarantee capacity for a long-running subscription. Therefore the design uses two layers:

1. `subscription_capacity_entitlement`: permanent recurring commitment pattern.
2. `subscription_capacity_allocation`: concrete dated allocations within the projection horizon.

The entitlement protects long-term admission. The allocation applies date overrides, closures and operational visibility.

## Weekly and monthly safety

Weekly schedules reserve the exact weekday + meal slot.

A monthly day-of-month can occur on different weekdays across months. To prevent a future collision, monthly admission is conservatively validated against every weekday that the monthly occurrence may land on. Existing weekly demand plus the maximum monthly demand must fit the chef's subscription capacity for each possible weekday.

This may be conservative, but it prevents a future calendar shift from creating overbooking.

## Overbooking behavior

New booking is allowed only when all required slot and optional item constraints have available capacity.

If any required slot fails, the enrollment fails as one operation; partial reservations are not retained.

Capacity operations serialize per chef through a PostgreSQL row lock. Database uniqueness protects entitlement/allocation idempotency.

Plans with missing base capacity, frozen sales or no room for one additional recurring commitment are not returned as generally bookable public plans. Enrollment performs the authoritative start-date-specific check again, including date overrides.

## Existing subscriber protection

If chef capacity is lowered below existing commitments:

- existing committed subscriptions remain protected;
- new subscription sales stop for the affected capacity;
- recurring/date/item deficit is recorded;
- support can inspect the incident;
- support/operations can freeze all new subscription sales for the chef if required;
- Craves does not automatically cancel customers or invent refund/credit treatment.

## Failure handling

### Paid event after hold expiry

A successful payment event may arrive after its capacity hold has expired. Before ACTIVE, Subscription Service tries to reacquire capacity.

If it cannot:

- transaction does not activate the subscription;
- Service Bus message is retried and eventually DLQ'd according to the existing payment-status consumer policy;
- a separate `REQUIRES_NEW` support transaction records `PAID_CAPACITY_CONFLICT` as a P2 incident so it remains visible even though the payment transaction rolls back.

No paid customer is silently placed into an overbooked meal slot.

### Projection failure

Projection failures create a support-visible incident rather than fabricating capacity.

### Existing commitment versus new closure

Existing commitment is projected and retained. The date/item becomes a deficit incident; new sales remain blocked.

## Support roles

Read capacity/incidents:

- PLATFORM_ADMIN
- SUBSCRIPTION_ADMIN
- SUPPORT_ADMIN
- OPERATIONS_ADMIN
- AUDIT_ADMIN

Freeze/unfreeze new subscription sales:

- PLATFORM_ADMIN
- SUBSCRIPTION_ADMIN
- SUPPORT_ADMIN
- OPERATIONS_ADMIN

Reconciliation:

- PLATFORM_ADMIN
- SUBSCRIPTION_ADMIN
- OPERATIONS_ADMIN

CHEF controls its own capacity values and date/item overrides.

PAYMENTS_ADMIN can continue payment operations but is not given authority to modify chef capacity.

## Database migrations

Production-completion migrations V8-V11 must exist first.

Capacity migrations:

- `V12__subscription_capacity_management.sql`
- `V13__capacity_occurrence_materialization.sql`
- `V14__capacity_respects_skip_requests.sql`
- `V15__paid_capacity_conflict_incident.sql`

Flyway is forward-only. Do not manually remove migration-history records to roll back.

## Backend code paths

- `services/subscription-service/src/main/java/in/craves/subscription/capacity/CapacityModels.java`
- `CapacityProperties.java`
- `CapacityRepository.java`
- `CapacityService.java`
- `CapacityController.java`
- `CapacitySchedulingConfiguration.java`
- `CapacityProjectionWorker.java`
- `CapacityFailureReporter.java`

Capacity is integrated into:

- `service/SubscriptionService.java`
- `lifecycle/SubscriptionLifecycleService.java`
- `payment/SubscriptionPaymentStatusService.java`
- `payment/SubscriptionPaymentStatusProcessor.java`
- `occurrence/OccurrenceRepository.java`
- `occurrence/OccurrenceGeneratorService.java`

## Chef web paths

Page:

`/chef/capacity`

Source:

- `apps/customer-web-next/src/app/chef/capacity/page.tsx`
- `apps/customer-web-next/src/components/chef-capacity-manager.tsx`
- `apps/customer-web-next/src/lib/chef-subscription-capacity-contract.ts`
- `apps/customer-web-next/src/app/api/chef/subscription-capacity/**`

Chef navigation contains Capacity.

## Admin/support web paths

Page:

`/admin/subscription-capacity`

Source:

- `apps/customer-web-next/src/app/admin/subscription-capacity/page.tsx`
- `apps/customer-web-next/src/components/admin-subscription-capacity-operator.tsx`
- `apps/customer-web-next/src/lib/admin-subscription-capacity-contract.ts`
- `apps/customer-web-next/src/app/api/admin/subscription-capacity/**`

Admin navigation contains Capacity.

## API surface

Chef:

- `GET /api/v1/chef/subscription-capacity`
- `PUT /api/v1/chef/subscription-capacity/rules/slots`
- `PUT /api/v1/chef/subscription-capacity/rules/menu-items`
- `PUT /api/v1/chef/subscription-capacity/overrides/slots`
- `PUT /api/v1/chef/subscription-capacity/overrides/menu-items`

Admin/support:

- `GET /api/v1/admin/subscription-capacity/chefs/{chefIdentityId}`
- `PATCH /api/v1/admin/subscription-capacity/chefs/{chefIdentityId}/freeze`
- `GET /api/v1/admin/subscription-capacity/incidents`
- `POST /api/v1/admin/subscription-capacity/subscriptions/{subscriptionId}/reconcile`

APIM configure/status/rollback scripts include all of these operations and require Bearer authentication.

## Runtime variables

No new secret is required.

Non-secret runtime settings:

- `CRAVES_SUBSCRIPTION_CAPACITY_HOLD_MINUTES=15`
- `CRAVES_SUBSCRIPTION_CAPACITY_PROJECTION_HORIZON_DAYS=180`
- `CRAVES_SUBSCRIPTION_CAPACITY_PROJECTION_ENABLED=false`
- `CRAVES_SUBSCRIPTION_CAPACITY_PROJECTION_BATCH_SIZE=50`
- `CRAVES_SUBSCRIPTION_CAPACITY_PROJECTION_FIXED_DELAY_MS=60000`

The first production deployment MUST leave projection disabled until the core deployment/APIM/chef configuration smoke tests pass.

The previously deferred workers must also remain false:

- `CRAVES_SUBSCRIPTION_OCCURRENCE_GENERATOR_ENABLED=false`
- `CRAVES_SUBSCRIPTION_BILLING_GENERATOR_ENABLED=false`
- `CRAVES_SUBSCRIPTION_BILLING_PUBLISHER_ENABLED=false`
- `CRAVES_SUBSCRIPTION_PAYMENT_STATUS_CONSUMER_ENABLED=false`
- `CRAVES_SUBSCRIPTION_ORDER_REQUEST_WORKER_ENABLED=false`
- `CRAVES_SUBSCRIPTION_ORDER_PUBLISHER_ENABLED=false`

## CI before merge

Build-only pipeline:

`azure-pipelines-subscription-capacity-ci.yml`

It performs:

1. Java 21 Maven `clean verify` for Subscription Service.
2. Node 24 Next.js install, lint, typecheck, tests and build.
3. APIM shell syntax + XML validation.
4. Fail-closed feature-flag checks.
5. basic credential-material scan.

It contains no AzureCLI deployment step and provisions no Azure resources.

## Merge order

1. Run and pass `azure-pipelines-subscription-capacity-ci.yml` from branch `feature/subscription-capacity-enforcement-v2`.
2. Fix any failures; re-run until green.
3. Merge PR #175 into `feature/subscription-production-completion`.
4. Re-run the Subscription production-completion checks/merge gate for PR #173.
5. Merge PR #173 to `main` only when green.
6. Deploy from `main`.

Do not deploy the current old `main` before PRs #175 and #173 are merged in this order.

## Production deployment order after merge to main

1. Run `azure-pipelines-subscription-service.yml` from `main` using `Craves-Dev-Service-Connection`.
2. Verify Container App `ca-craves-subscription-service-p` is Ready/Healthy.
3. Verify Flyway V8-V15 succeeded.
4. Verify all capacity/occurrence/payment/order worker flags remain false.
5. Run `azure-pipelines-subscription-backoffice-apim-ci.yml`.
6. Apply the guarded APIM configuration with `CONFIRM_APIM_WRITE=true` only after backend health is green.
7. Run the APIM status script.
8. Deploy customer/chef/admin web through `azure-pipelines-customer-web-next-delivery-tracking.yml` with its existing guarded replacement parameter.
9. Smoke chef capacity configuration and admin capacity visibility.
10. Smoke plan activation and enrollment fail-closed behavior.
11. Only then consider enabling `CRAVES_SUBSCRIPTION_CAPACITY_PROJECTION_ENABLED=true` as a separate controlled runtime change.
12. Occurrence/payment/order/provider activation remains a later separate stage.

## First smoke test

1. Approved chef opens `/chef/capacity`.
2. Configure capacity for every weekday/slot required by the test plan.
3. Admin creates/updates plan schedule and lifecycle policy.
4. Plan readiness and capacity readiness allow ACTIVE.
5. Customer can see/book the plan.
6. Set subscription allocation equal to existing commitments; next customer must no longer see/book capacity.
7. Direct enrollment attempt must return a capacity conflict even if the browser had stale availability.
8. Chef lowers a capacity rule below existing commitments. Existing subscriptions remain unchanged; open incident appears and new sale is blocked.
9. Support opens `/admin/subscription-capacity`, loads chef, reviews incident, and can freeze new sales with a reason.
10. Restore valid chef capacity; reconcile as appropriate with an operations/subscription admin and retain audit evidence.
11. Verify no external payment/order/delivery worker was accidentally enabled.

## Rollback

Application rollback is image-only through the runtime-preserving deployment pipeline.

APIM named operations can be removed with the guarded rollback script.

V12-V15 are forward-only schema migrations. If a migration defect is discovered, stop feature activation and ship a reviewed corrective forward migration; do not manually erase Flyway history.

## Remaining production gates outside this capacity module

- controlled capacity-projection worker activation after smoke
- Cashfree recurring payment provider activation
- recurring Order Service publisher activation
- delivery/provider activation
- Redis/distributed protections
- load/soak testing and observability
- final credential rotation
- one-million-concurrent-user architecture certification

These remain explicit later stages rather than hidden behavior in the capacity implementation.
