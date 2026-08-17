# Craves Delivery Provider Productionization — Engineering Handover

**Date:** 2026-08-16  
**Repository:** `rmorampudi09-arch/Craves-Build-platform`  
**Working branch:** `feature/delivery-provider-productionization-20260816`  
**Target branch:** `main`  
**Branch base commit:** `f0b4c27973bb987bccf68bb1f1a44e1250d94bd4`  
**Status at handover:** source implementation and safety hardening complete for review; CI/build/runtime validation still required before merge or deployment.  
**Live Azure/provider state changed by this work:** **No.**

---

## 1. Purpose of this handover

This document records the delivery-provider productionization work completed on the isolated feature branch. It is intentionally explicit about what is implemented in source, what has been proven previously in runtime, what still needs CI/deployment validation, and which items cannot be completed until Craves receives authoritative vendor credentials/API contracts or supplies an operations decision.

The objective of this work is not merely to add more environment variables. The objective is to create a delivery-provider platform where:

1. every provider is fail-closed by default;
2. a provider can participate in intelligent routing only if the corresponding Spring adapter is genuinely executable;
3. provider create/mutation permission is separated from read-only/configuration permission where necessary;
4. provider endpoints cannot be switched while jobs from the previous environment are still non-terminal;
5. database routing activation cannot get ahead of the deployed runtime;
6. Order Service event publishing and delivery-status consumption are ready before a newly activated provider can process orders;
7. provider credentials stay secret-backed;
8. webhook ingress is authenticated, deduplicated, normalized and processed through the existing Craves delivery-status architecture;
9. future production cutover can be performed through one coordinated preflight-first pipeline rather than a collection of unsafe independent toggles;
10. Shadowfax, Porter and Delhivery remain visibly blocked until the correct vendor contracts are available rather than being represented as working integrations without executable adapters.

This handover should be read together with:

- `services/integration-service/modules/delivery-provider-production/README.md`
- the Craves delivery-intelligence implementation handover already present in the repository;
- the prior Borzo sandbox E2E verification evidence;
- the architecture and functional specification sections governing delivery/order lifecycle.

---

## 2. Non-negotiable architecture decisions preserved

### 2.1 Craves selects a provider; the provider normally selects the rider

The delivery-intelligence engine ranks real provider candidates. Craves does not fabricate or randomly assign a rider merely to make a test look multi-provider.

The correct responsibility split is:

```text
Craves order accepted
        |
        v
Craves delivery command
        |
        v
active executable provider adapters quote
        |
        v
Craves intelligence ranks provider candidates
        |
        v
Craves selects provider
        |
        v
selected provider creates booking
        |
        v
external provider allocates courier/rider
```

A provider contract may someday expose a supported rider-level selection feature. Until an authoritative vendor contract proves that capability, Craves does not invent it.

### 2.2 Multi-provider intelligence is not proven by one provider

The intelligence engine can execute its scoring strategy when only one provider is available, but that proves the engine path—not genuine provider competition.

A real multi-provider test requires at least two simultaneously executable provider adapters to return valid candidates for the same new order. Only then can the candidate table, score audit and selected provider demonstrate actual provider competition.

### 2.3 Provider activation is a runtime + database decision

A provider is not considered routable simply because an environment variable is `true`.

For production-grade activation Craves needs, at minimum:

- an executable canonical Spring adapter;
- valid provider-specific runtime configuration;
- authoritative credentials/API product;
- required webhook and reconciliation support;
- provider catalog row active;
- healthy shared delivery command/status pipeline;
- required provider-specific prerequisites such as pickup mappings;
- a ready Integration Service revision;
- ready Order Service event publishing/status projection.

### 2.4 Migrations never activate providers

The provider migration registers providers but deliberately keeps them inactive. Existing `is_active` values are preserved on conflict. Provider activation is an operational action controlled by guarded pipelines, not a schema migration side effect.

---

## 3. Provider readiness state after this source work

| Provider | Canonical Spring command adapter | Safe validation mode | Create/mutation source path | Current blocker before live production use |
|---|---|---|---|---|
| Borzo | Existing and runtime-proven | Real vendor sandbox | Existing proven adapter | Production endpoint/credentials and controlled environment cutover |
| Shiprocket | Implemented on this branch | `READ_ONLY` authenticated validation | Implemented but heavily gated | CI/deploy validation, account credentials, webhook, package dimensions, attribution approval and verified kitchen pickup mapping |
| Shadowfax | Not implemented | `SIMPLE_SANDBOX` / disabled only | Blocked | `VENDOR_PRIVATE_API_CONTRACT_REQUIRED` |
| Porter | Not implemented | `SIMPLE_SANDBOX` / disabled only | Blocked | `ENTERPRISE_API_ONBOARDING_REQUIRED` |
| Delhivery | No verified intracity adapter | `SIMPLE_SANDBOX` / disabled only | Blocked | `INTRACITY_API_PRODUCT_NOT_VERIFIED` |

The words “implemented” and “operational” are intentionally not used interchangeably. Shiprocket is implemented in source but must not be called operational until the branch builds successfully, is deployed, credentials are bound, account-side configuration is completed and a controlled validation passes.

---

## 4. Why Shiprocket uses READ_ONLY instead of a fake sandbox

Current public Shiprocket API documentation describes authenticated API requests against the account. The productionization work therefore removes the dangerous assumption that a value called `SANDBOX` automatically means isolated fake shipment creation.

Craves now uses these semantics:

```text
READ_ONLY
  authenticated API/serviceability validation allowed
  shipment create disabled
  provider catalog inactive
  cannot enter intelligent routing

SANDBOX
  compatibility alias for READ_ONLY
  shipment create still disabled

PRODUCTION
  not enough by itself
  create requires every explicit mutation gate
```

The following runtime gates are required for Shiprocket production creation:

```text
SHIPROCKET_API_ENVIRONMENT=PRODUCTION
SHIPROCKET_API_ENABLED=true
SHIPROCKET_CREATE_ENABLED=true
SHIPROCKET_PRODUCTION_ACTIVATION_APPROVED=true
SHIPROCKET_ATTRIBUTION_APPROVED=true
```

In addition, credentials, webhook token, order email, package dimensions, APIM callback and a verified chef pickup mapping must be present.

This distinction is critical because a pipeline should never create a real shipment merely because someone selected a parameter labeled “sandbox.”

---

## 5. Provider-neutral delivery event enrichment

The previous event shape was sufficient for Borzo but not sufficiently structured for providers that require city/state/postal/package/order-item data. The change was made in the provider-neutral Order Service event rather than adding a Shiprocket-specific address parser.

### Files changed

- `services/order-service/src/main/java/in/craves/order/event/ChefAcceptedOrderEventData.java`
- `services/order-service/src/main/java/in/craves/order/event/ChefAcceptedOrderEventFactory.java`
- `services/order-service/src/main/java/in/craves/order/event/ChefAcceptedOrderEventSource.java`
- `services/order-service/src/main/java/in/craves/order/service/ChefAcceptanceService.java`

### Data now preserved for delivery providers

The event can carry:

- original free-form pickup/dropoff address;
- structured address lines;
- area/landmark;
- city;
- state;
- postal code;
- country;
- contact information;
- coordinates;
- immutable item snapshots;
- item quantity and price context;
- declared food/goods subtotal;
- current verified-payment collection mode (`PREPAID`);
- stable `kitchen_id` as the provider-neutral pickup-location reference.

The original free-form address is retained for backward compatibility with Borzo and any adapter that does not need fully structured locality data.

No package dimensions were invented in this event work. Physical packaging dimensions remain an explicit Craves operations/product input.

---

## 6. Canonical delivery adapter and selected-quote propagation

The active delivery-command path uses:

`services/integration-service/src/main/java/in/craves/integration/delivery/provider/DeliveryProviderAdapter.java`

The command router is:

`services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryProviderRouter.java`

The router was updated so the exact winning quote/candidate can be propagated into provider create rather than discarded after ranking.

This matters for Shiprocket because the ranked quote can contain the selected courier/service identifier, price and ETA. If create were allowed to silently choose a different service after Craves ranked one candidate, the candidate audit and actual external booking could diverge.

The source tree still contains an older root-level interface under `in.craves.integration.delivery`. That interface is not the current command-router contract. It was deliberately not deleted in this productionization pass because unrelated intelligence code may still depend on it. Removing/merging that legacy abstraction should be treated as a separate refactor after current delivery productionization is validated.

---

## 7. Multi-provider provider catalog and pickup mapping migration

### Exact migration

`services/integration-service/src/main/resources/db/migration/V110__multi_provider_catalog_and_pickup_mapping.sql`

### What it does

The migration registers:

- `shiprocket`
- `shadowfax`
- `porter`
- `delhivery`

Every newly inserted provider is created with:

```text
is_active = FALSE
```

On provider conflict the migration updates descriptive/capability metadata but **does not update `is_active`**. This prevents a migration from activating or deactivating an operational provider unexpectedly.

### Provider pickup table

The migration also creates:

`delivery_schema.delivery_provider_pickup_location`

Primary conceptual mapping:

```text
provider_id
+ pickup_location_reference (Craves kitchen_id)
        |
        v
external_location_code
+ is_verified
+ verified_at
+ metadata
```

A database constraint requires verified mappings to have a verification timestamp. Blank external location codes are rejected.

### Repository implementation

`services/integration-service/src/main/java/in/craves/integration/delivery/provider/DeliveryProviderPickupLocationRepository.java`

This repository resolves verified provider pickup codes per kitchen and reports verified counts for the readiness matrix.

This replaces the unsafe marketplace assumption that one global pickup location can represent every home chef.

---

## 8. Shiprocket configuration model

### File

`services/integration-service/src/main/java/in/craves/integration/config/ShiprocketProperties.java`

### Configuration principles

- default environment is `READ_ONLY`;
- provider API disabled by default;
- create disabled by default;
- production approval disabled by default;
- attribution approval disabled by default;
- credentials are separate email/password values rather than the obsolete single-token pipeline assumption;
- package dimensions are validated as an all-or-nothing set;
- `SANDBOX` normalizes to read-only execution semantics;
- production create readiness is calculated only when all gates are satisfied.

### Application bindings

`services/integration-service/src/main/resources/application.yml`

The new Shiprocket bindings are environment-driven and fail-closed. No secret value is committed in Git.

---

## 9. Shiprocket API runtime implementation

### Source directory

`services/integration-service/src/main/java/in/craves/integration/delivery/shiprocket/`

### Files

- `ShiprocketApiClient.java`
- `ShiprocketAuthClient.java`
- `ShiprocketStatusMapper.java`
- `ShiprocketTransport.java`
- `ShiprocketWebhookController.java`
- `ShiprocketWebhookInboxRepository.java`
- `ShiprocketWebhookNormalizer.java`
- `ShiprocketWebhookService.java`

### Implemented behavior

The source supports:

- API-user email/password authentication;
- bearer-token caching;
- bounded retry behavior for appropriate read calls;
- hyperlocal/serviceability filtering;
- deterministic provider/courier candidate selection from quote response information;
- Craves delivery ETA guard;
- exact selected quote propagation into create;
- guarded forward shipment creation;
- deterministic Craves source order/client-reference use;
- uncertain-create reconciliation before duplicate/fallback behavior;
- cancellation;
- tracking;
- canonical status mapping;
- authenticated webhook ingestion;
- webhook deduplication and normalization.

### Mutation design

Create is deliberately not implemented as a blind “retry until HTTP 200” wrapper. A provider create call can succeed externally while its HTTP response is lost. Blindly retrying a create in that situation can create duplicate bookings.

The design therefore follows:

```text
create request sent
     |
     +-- definite success -> persist provider booking
     |
     +-- definite non-ambiguous failure -> failure handling
     |
     +-- ambiguous result -> reconciliation by deterministic Craves reference
                                  |
                                  +-- external booking found -> persist it
                                  |
                                  +-- proven absent -> safe subsequent handling/fallback
```

This matches the existing Craves reconciliation-first architecture.

---

## 10. Shiprocket status mapping correction

A material correctness issue was found during the official documentation review: Shiprocket can expose multiple status-ID families, including `current_status_id` and `shipment_status_id`. The same integer may have a different meaning depending on the table/context.

Craves therefore no longer blindly treats any numeric status ID as a shipment status code.

### File

`services/integration-service/src/main/java/in/craves/integration/delivery/shiprocket/ShiprocketStatusMapper.java`

### Safety behavior

- a recognized explicit status label takes precedence over an ambiguous numeric ID;
- only true delivery evidence is mapped to `DELIVERED`;
- `FULFILLED` does not mean customer-delivered and remains unknown/non-terminal;
- `SELF FULFILLED` and fulfillment-centre-only states do not fabricate last-mile progress;
- undelivered/exceptions map conservatively;
- return and failure states are kept distinct where possible.

An example that motivated this guard is the possibility of a response containing a current-status ID that collides numerically with a different shipment-status code while the text clearly says `IN TRANSIT`.

---

## 11. Shiprocket webhook architecture

### Public backend route

```text
POST /api/v1/webhooks/delivery/p4
```

The provider-neutral path is intentional. Provider documentation has restrictions around callback URLs containing provider-identifying strings; the neutral route also avoids leaking an unnecessary provider name in the public path.

### Authentication

The webhook expects the configured security token in:

```text
x-api-key
```

The service compares the supplied token using constant-time comparison before persisting the callback.

### Files

- `services/integration-service/src/main/java/in/craves/integration/delivery/shiprocket/ShiprocketWebhookController.java`
- `.../ShiprocketWebhookService.java`
- `.../ShiprocketWebhookInboxRepository.java`
- `.../ShiprocketWebhookNormalizer.java`

### Ingestion flow

```text
POST /p4
   |
   v
x-api-key verification
   |
   v
JSON validation
   |
   v
require AWB + shipment status
   |
   v
add Craves receipt timestamp
   |
   v
derive stable event fingerprint
   |
   v
delivery_webhook_inbox (provider=shiprocket)
   |
   v
existing generic webhook processor
   |
   v
provider normalizer
   |
   v
locked delivery_job update
   |
   v
DELIVERY_STATUS_CHANGED publication
```

The authentication token itself is not stored in the inbox. A SHA-256 fingerprint is stored instead.

### Timestamp decision

A Craves receipt timestamp is recorded because a provider timestamp without an unambiguous timezone must not silently be interpreted in a guessed timezone. Provider payload timestamps can remain in raw metadata for audit, but canonical ordering uses a trustworthy Craves receipt instant unless the provider contract supplies an unambiguous absolute timestamp.

---

## 12. Existing webhook/status architecture reused

Shiprocket does not introduce a parallel delivery-status database or separate event model.

It reuses the same generic delivery webhook processor/status update pipeline already used by provider implementations such as Borzo.

This is important for:

- inbox deduplication;
- locked job updates;
- stale/equal event protection;
- canonical status transitions;
- delivery status outbox publication;
- Order Service projection.

The source continues to preserve separation between commercial order status and delivery projection status.

---

## 13. Runtime readiness matrix

### Service

`services/integration-service/src/main/java/in/craves/integration/delivery/production/DeliveryProviderReadinessService.java`

The service was extended from a Borzo-focused readiness check to a provider matrix.

### Admin endpoint

`services/integration-service/src/main/java/in/craves/integration/admin/AdminDeliveryProviderReadinessController.java`

Endpoint:

```text
GET /api/v1/admin/operations/delivery-providers/readiness
```

The path is covered by the existing `/api/v1/admin/**` security policy. The response is no-store and does not expose credential values.

### Readiness data includes

Per provider:

- requested/configured environment;
- canonical command adapter registration;
- provider API/read-only availability;
- provider create availability;
- production-ready state;
- database catalog activation;
- verified pickup count;
- explicit blocker list.

Shared blockers include:

- Service Bus configuration;
- create reconciliation;
- webhook processing;
- tracking reconciliation;
- delivery-status publication;
- delivery command worker.

### Explicit current vendor blockers

Shadowfax:

```text
VENDOR_PRIVATE_API_CONTRACT_REQUIRED
```

Porter:

```text
ENTERPRISE_API_ONBOARDING_REQUIRED
```

Delhivery:

```text
INTRACITY_API_PRODUCT_NOT_VERIFIED
```

These codes are intentional. They prevent a UI/pipeline toggle from being confused with a functioning integration.

---

## 14. APIM webhook pipeline

### File

`azure-pipelines-delivery-provider-webhooks-apim.yml`

### Supported implemented routes

```text
POST /api/v1/webhooks/delivery/borzo
POST /api/v1/webhooks/delivery/p4
```

Borzo remains enabled by default in the APIM route pipeline because it is the existing proven webhook route.

Shiprocket `/p4` remains disabled by default until the manual account-side webhook setup is complete.

Before exposing a route the pipeline verifies the corresponding Spring backend route exists. This prevents APIM from publishing a dead path.

The APIM policy does not transform the raw webhook body.

The correct APIM resource name is:

```text
apim-craves-prodlow-l3ing6
```

A transient typo discovered during this source pass was corrected, and CI now rejects the bad form.

---

## 15. Borzo standalone activation pipeline hardening

### File

`azure-pipelines-delivery-provider-production-activation.yml`

### Supported modes

Simple sandbox:

```text
targetEnvironment=SANDBOX
enableProvider=false
```

Full sandbox:

```text
targetEnvironment=SANDBOX
enableProvider=true
```

Production downstream staging and provider-create activation remain explicit separate modes.

### Defects fixed

#### 15.1 Global worker ownership

The earlier standalone provider logic could disable the global delivery-command worker when Borzo was disabled, even if another provider later became active. That is incorrect in a multi-provider system.

The pipeline now disables the global worker only when no provider catalog row remains active.

#### 15.2 Environment transition with open jobs

The pipeline now blocks changing Borzo environment/endpoint while a Borzo `delivery_job` is non-terminal. This prevents a sandbox provider-delivery ID from becoming untrackable after the runtime suddenly points to the production endpoint.

#### 15.3 Routing/runtime race

Borzo is removed from the database routing catalog before runtime reconfiguration. If no other provider remains active, command consumption is paused. After the new Integration revision is ready, Order Service event/status flow is prepared, then the Borzo DB row becomes active, then command processing resumes.

#### 15.4 Order Service projection completeness

When activating Borzo, the pipeline explicitly enables:

```text
CRAVES_DOMAIN_EVENT_OUTBOX_ENABLED=true
CRAVES_DOMAIN_EVENT_SERVICE_BUS_ENABLED=true
CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED=true
```

This covers both directions:

- chef acceptance event publication toward Integration Service;
- delivery status consumption back into Order Service.

#### 15.5 Simple sandbox credential dependency

Disabling Borzo in simple sandbox no longer requires provider credentials merely to remain disabled.

---

## 16. Shiprocket standalone activation pipeline

### File

`azure-pipelines-shiprocket-production-activation.yml`

### User-facing run parameters

The pipeline uses Azure DevOps run parameters instead of relying on hidden pipeline variables for operational decisions. Important parameters include:

- `targetEnvironment`
- `enableProvider`
- `enableCreate`
- `confirmProductionActivation`
- `attributionApproved`
- provider base URL
- callback URL
- expected secret names
- order email
- package dimensions

### Supported states

Fail closed:

```text
enableProvider=false
enableCreate=false
```

Read-only provider validation:

```text
targetEnvironment=READ_ONLY
enableProvider=true
enableCreate=false
```

Production create:

```text
targetEnvironment=PRODUCTION
enableProvider=true
enableCreate=true
confirmProductionActivation=true
attributionApproved=true
```

The final state additionally requires the secret references, order email, dimensions, APIM callback and at least one verified pickup mapping.

### Routing race prevention

Before any Shiprocket runtime reconfiguration:

1. Shiprocket is forced inactive in `delivery_provider`;
2. if no other provider is active, the global command worker is paused;
3. runtime settings are updated;
4. the new Integration revision must become ready;
5. for create activation, Order Service event/status flow is enabled and must become ready;
6. Shiprocket is then activated in the DB;
7. command processing is enabled/resumed.

Read-only mode always leaves the Shiprocket DB row inactive, so it cannot accidentally enter intelligent routing.

---

## 17. Shadowfax pipeline

### File

`azure-pipelines-shadowfax-environment.yml`

### Current allowed practical state

```text
SANDBOX
enableProvider=false
```

The pipeline explicitly:

- sets the runtime provider flag disabled;
- forces `delivery_provider.is_active=false` for Shadowfax;
- preserves the global delivery worker if another provider remains active;
- disables the global worker only if no providers are active.

Attempting to enable the provider or move to production requires a real adapter and runtime configuration and currently fails with:

```text
VENDOR_PRIVATE_API_CONTRACT_REQUIRED
```

This is intentional until Craves receives the private merchant API contract from Shadowfax.

---

## 18. Porter pipeline

### File

`azure-pipelines-porter-environment.yml`

Default/current safe state:

```text
SANDBOX
enableProvider=false
```

It also enforces DB inactive state and global-worker preservation.

Current enable blocker:

```text
ENTERPRISE_API_ONBOARDING_REQUIRED
```

Porter must not be represented as executable until the Enterprise API onboarding supplies the authoritative endpoint/authentication/quote/create/cancel/track/webhook contract.

---

## 19. Delhivery pipeline

### File

`azure-pipelines-delhivery-environment.yml`

Default/current safe state:

```text
SANDBOX
enableProvider=false
```

The current account/API evidence is not treated as proof of a correct intracity food-delivery API product.

Current blocker:

```text
INTRACITY_API_PRODUCT_NOT_VERIFIED
```

The pipeline forces the provider DB row inactive while gated.

Craves should obtain the exact Delhivery product intended for intracity food delivery before implementing or activating an adapter. A parcel/B2B Surface contract must not be silently repurposed as a last-mile food-delivery integration.

---

## 20. Coordinated one-go production activation pipeline

### File

`azure-pipelines-delivery-provider-coordinated-activation.yml`

This is the preferred final production cutover mechanism once every selected provider is genuinely eligible.

### Current selectable executable providers

- Borzo, subject to production prerequisites;
- Shiprocket, subject to all production create gates after deployment/account validation.

The pipeline deliberately rejects selecting Shadowfax, Porter or Delhivery today with their explicit blocker codes.

### Preflight-before-write philosophy

The pipeline performs selected-provider validation before it changes runtime configuration.

Checks include:

- Integration Service latest revision ready;
- expected `delivery-command` queue active count;
- expected scheduled count;
- expected DLQ count;
- expected Order Service delivery-status subscription active/DLQ counts;
- selected secret references exist;
- selected production endpoints are not test/sandbox placeholders;
- provider catalog rows exist;
- required APIM operations exist;
- Shiprocket attribution approval;
- Shiprocket package dimensions;
- Shiprocket order email;
- Shiprocket verified pickup mapping;
- Borzo no non-terminal old-environment jobs.

If preflight fails, the pipeline stops before provider activation.

### Cutover sequence

```text
all selected providers pass preflight
          |
          v
pause delivery-command worker
          |
          v
stage selected production runtime values
          |
          v
wait Integration Service revision ready
          |
          v
activate selected provider DB rows
          |
          v
enable Order Service CHEF_ACCEPTED_ORDER publishing
          |
          v
enable Order Service DELIVERY_STATUS_CHANGED consumer
          |
          v
resume delivery-command worker
          |
          v
verify runtime + DB + Order Service state
```

Because command consumption is paused across this coordinated sequence, provider catalog updates cannot be consumed prematurely during the cutover.

---

## 21. Delivery-provider CI pipeline

### File

`azure-pipelines-delivery-provider-production-ci.yml`

### Purpose

This is the mandatory source validation gate before merge/deployment.

### Build/test scope

It runs Java 21 Maven verification for:

- `services/integration-service`
- `services/order-service`

This matters because this branch changes both the provider runtime and the Order Service event contract.

### Static safety checks

The CI pipeline verifies:

- all required delivery YAMLs exist;
- Borzo/Shiprocket/shared delivery flags remain fail-closed in application defaults;
- Order Service delivery-status consumer/outbox/publisher remain fail-closed by default;
- exact V110 provider catalog/pickup migration exists;
- V110 contains all four provider rows;
- V110 creates the pickup mapping table;
- V110 keeps pickup mappings unverified by default;
- V110 conflict update does not assign `is_active`;
- Shiprocket source files exist;
- all new Shiprocket/readiness tests exist;
- neutral `/p4` route exists;
- webhook uses `x-api-key`;
- shipment status fields are covered;
- no public `/shiprocket` callback route appears;
- no obsolete Shiprocket single auth-token model reappears;
- correct APIM resource name is used;
- the known typo is rejected;
- Borzo and Shiprocket standalone pipelines include environment transition guards;
- Order Service delivery-status consumer is explicitly enabled on activation;
- coordinated pipeline includes all vendor blockers and pause/resume behavior;
- gated providers force their DB catalog rows inactive;
- Azure service connection is pinned;
- provider credentials are secret references rather than obvious plaintext values.

### Source hygiene

The CI records the productionization branch base:

```text
f0b4c27973bb987bccf68bb1f1a44e1250d94bd4
```

With full checkout history it runs:

```text
git diff --check <base> HEAD
```

The previous suppressed `|| true` has been removed. A whitespace error now fails the CI gate.

It also fails if validation steps mutate tracked source files.

### Current validation status

**This CI has not yet been executed against the final branch head as part of this handover.**

Therefore this handover must not claim:

- Maven build passed;
- tests passed;
- Azure DevOps pipeline passed;
- branch is merge-ready;
- Shiprocket runtime is operational.

The next validation action is to run this pipeline on the feature branch and inspect every job result.

---

## 22. Tests added

The following tests were added but must still be executed by the CI pipeline:

### Shiprocket properties

`services/integration-service/src/test/java/in/craves/integration/config/ShiprocketPropertiesTest.java`

Covers:

- fail-closed defaults;
- `SANDBOX` alias behaves read-only;
- create cannot be authorized by legacy sandbox naming;
- dimensions must be supplied together;
- attribution approval required;
- all create gates required for production readiness.

### Shiprocket status mapper

`services/integration-service/src/test/java/in/craves/integration/delivery/shiprocket/ShiprocketStatusMapperTest.java`

Covers:

- documented shipment lifecycle mapping;
- `FULFILLED` is not treated as customer delivered;
- self-fulfilled/fulfillment-only states remain unknown;
- text fallback handling;
- undelivered is not misread as delivered.

### Status ID collision

`services/integration-service/src/test/java/in/craves/integration/delivery/shiprocket/ShiprocketStatusIdSpaceTest.java`

Covers the collision case where an explicit `IN TRANSIT` label must win over an ambiguous numeric current-status ID.

### Webhook normalizer

`services/integration-service/src/test/java/in/craves/integration/delivery/shiprocket/ShiprocketWebhookNormalizerTest.java`

Covers:

- shipment status ID normalization;
- AWB provider identifiers;
- string numeric status IDs;
- Craves receipt timestamp requirement.

### Webhook security/deduplication

`services/integration-service/src/test/java/in/craves/integration/delivery/shiprocket/ShiprocketWebhookServiceTest.java`

Covers:

- missing API key rejected;
- wrong API key rejected;
- inbox not touched on failed auth;
- credential fingerprint stored instead of plaintext token;
- receipt timestamp added;
- duplicate inbox result returned as duplicate.

### Provider readiness

`services/integration-service/src/test/java/in/craves/integration/delivery/production/DeliveryProviderReadinessServiceTest.java`

Covers:

- Shadowfax vendor blocker remains explicit;
- Porter Enterprise blocker remains explicit;
- Delhivery intracity product blocker remains explicit;
- Shiprocket does not report production ready without create gates and verified pickup mapping.

---

## 23. Safety defects discovered and corrected during this pass

This section is included because these issues are easy to reintroduce during future maintenance.

### 23.1 Old Shiprocket credential model mismatch

An earlier activation pipeline expected one `SHIPROCKET_API_AUTH_TOKEN`, while the implemented API client requires API-user email/password and generates/caches its bearer token.

Corrected to secret-backed:

```text
shiprocket-api-email
shiprocket-api-password
shiprocket-webhook-token
```

### 23.2 Fake sandbox assumption

Treating `SHIPROCKET_API_ENVIRONMENT=SANDBOX` as permission for mutation was unsafe. It is now a read-only alias.

### 23.3 Provider-named Shiprocket callback path

The public path was changed to neutral `/p4`, with APIM updated accordingly.

### 23.4 Status code table collision

`current_status_id` and `shipment_status_id` can be different status namespaces. Mapper hardened to avoid wrong delivery state.

### 23.5 `FULFILLED` incorrectly implying delivered

Corrected. Craves only marks delivered on delivery evidence.

### 23.6 One global Shiprocket pickup location

Replaced with per-`kitchen_id` provider pickup mappings.

### 23.7 Winning quote discarded before create

Canonical create request now carries the selected quote so provider booking cannot silently diverge from Craves ranking.

### 23.8 Global delivery worker disabled by one provider toggle

Standalone provider pipelines now respect other active providers.

### 23.9 Provider DB row active during runtime reconfiguration

Standalone Borzo/Shiprocket now remove themselves from routing before reconfiguration.

### 23.10 Order Service status consumer not guaranteed on activation

Activation explicitly enables `CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED=true` along with outbox/service-bus publication.

### 23.11 Environment switch while old jobs remain open

Borzo and Shiprocket environment transitions are blocked while provider-specific jobs are non-terminal.

### 23.12 APIM resource-name typo

Corrected to `apim-craves-prodlow-l3ing6`; CI rejects the typo.

### 23.13 Gated provider stale DB state

Shadowfax/Porter/Delhivery default/off pipelines now force their catalog rows inactive.

### 23.14 Suppressed source hygiene check

`git diff --check ... || true` was replaced with a real failing branch-wide diff check.

---

## 24. Files changed by the productionization branch

The branch comparison to `main` at the time of this handover contains the following delivery-productionization files.

### Azure DevOps

- `azure-pipelines-delhivery-environment.yml`
- `azure-pipelines-delivery-provider-coordinated-activation.yml`
- `azure-pipelines-delivery-provider-production-activation.yml`
- `azure-pipelines-delivery-provider-production-ci.yml`
- `azure-pipelines-delivery-provider-webhooks-apim.yml`
- `azure-pipelines-porter-environment.yml`
- `azure-pipelines-shadowfax-environment.yml`
- `azure-pipelines-shiprocket-production-activation.yml`

### Module documentation

- `services/integration-service/modules/delivery-provider-production/README.md`

### Admin/readiness

- `services/integration-service/src/main/java/in/craves/integration/admin/AdminDeliveryProviderReadinessController.java`
- `services/integration-service/src/main/java/in/craves/integration/delivery/production/DeliveryProviderReadinessService.java`

### Configuration

- `services/integration-service/src/main/java/in/craves/integration/config/ShiprocketProperties.java`
- `services/integration-service/src/main/resources/application.yml`

### Provider-neutral router/contracts

- `services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryProviderRouter.java`
- `services/integration-service/src/main/java/in/craves/integration/delivery/provider/DeliveryProviderAdapter.java`
- `services/integration-service/src/main/java/in/craves/integration/delivery/provider/DeliveryProviderPickupLocationRepository.java`

### Shiprocket implementation

- `services/integration-service/src/main/java/in/craves/integration/delivery/shiprocket/ShiprocketApiClient.java`
- `services/integration-service/src/main/java/in/craves/integration/delivery/shiprocket/ShiprocketAuthClient.java`
- `services/integration-service/src/main/java/in/craves/integration/delivery/shiprocket/ShiprocketStatusMapper.java`
- `services/integration-service/src/main/java/in/craves/integration/delivery/shiprocket/ShiprocketTransport.java`
- `services/integration-service/src/main/java/in/craves/integration/delivery/shiprocket/ShiprocketWebhookController.java`
- `services/integration-service/src/main/java/in/craves/integration/delivery/shiprocket/ShiprocketWebhookInboxRepository.java`
- `services/integration-service/src/main/java/in/craves/integration/delivery/shiprocket/ShiprocketWebhookNormalizer.java`
- `services/integration-service/src/main/java/in/craves/integration/delivery/shiprocket/ShiprocketWebhookService.java`

### Database

- `services/integration-service/src/main/resources/db/migration/V110__multi_provider_catalog_and_pickup_mapping.sql`

### Tests

- `services/integration-service/src/test/java/in/craves/integration/config/ShiprocketPropertiesTest.java`
- `services/integration-service/src/test/java/in/craves/integration/delivery/production/DeliveryProviderReadinessServiceTest.java`
- `services/integration-service/src/test/java/in/craves/integration/delivery/shiprocket/ShiprocketStatusIdSpaceTest.java`
- `services/integration-service/src/test/java/in/craves/integration/delivery/shiprocket/ShiprocketStatusMapperTest.java`
- `services/integration-service/src/test/java/in/craves/integration/delivery/shiprocket/ShiprocketWebhookNormalizerTest.java`
- `services/integration-service/src/test/java/in/craves/integration/delivery/shiprocket/ShiprocketWebhookServiceTest.java`

### Order Service event contract

- `services/order-service/src/main/java/in/craves/order/event/ChefAcceptedOrderEventData.java`
- `services/order-service/src/main/java/in/craves/order/event/ChefAcceptedOrderEventFactory.java`
- `services/order-service/src/main/java/in/craves/order/event/ChefAcceptedOrderEventSource.java`
- `services/order-service/src/main/java/in/craves/order/service/ChefAcceptanceService.java`

### This handover

- `docs/handover/2026-08-16-delivery-provider-productionization.md`

---

## 25. Secret names expected later

No secret value should be pasted into chat, committed to Git or stored in a plain pipeline parameter.

Current names expected by the source/pipelines:

### Borzo

```text
borzo-api-auth-token
borzo-callback-token
```

### Shiprocket

```text
shiprocket-api-email
shiprocket-api-password
shiprocket-webhook-token
```

### Gated provider placeholder names

```text
shadowfax-api-token
porter-api-token
delhivery-api-token
```

The last three names are only current pipeline placeholders. Their final credential model must follow the authoritative vendor contract. If a vendor uses OAuth client ID/secret, API key pairs, signatures, certificate authentication or another scheme, the source/pipeline must be changed accordingly rather than forcing the vendor into a single-token model.

---

## 26. Manual prerequisites intentionally not completed in source

### 26.1 Shiprocket

Requires the final manual session to:

- identify/create the Shiprocket API user;
- store email/password securely in Azure under the expected secret references;
- create a strong webhook security token;
- store the token securely;
- expose APIM `/p4` after the new backend revision is deployed;
- configure the neutral callback in Shiprocket;
- configure the matching `x-api-key` security token provider-side;
- provide the operational order/billing email;
- provide approved physical package length/breadth/height;
- create/verify provider pickup locations for the chef kitchens that will be tested/activated;
- persist each verified `kitchen_id -> external_location_code` mapping;
- validate the account's hyperlocal courier attribution behavior;
- explicitly approve that attribution behavior before create activation.

### 26.2 Shadowfax

Requires:

- private merchant API contract;
- sandbox endpoint;
- sandbox credentials;
- quote/serviceability contract;
- create contract;
- cancel contract;
- track contract;
- webhook contract and authentication/signature rules;
- production endpoint/credential process.

Only after those are received should a Spring adapter be implemented.

### 26.3 Porter

Requires Enterprise API onboarding and authoritative API documentation/credentials.

### 26.4 Delhivery

Requires confirmation of the exact intracity delivery API/product suitable for Craves food delivery. Current B2B/parcel evidence is not sufficient.

---

## 27. Billing-sensitive/manual Azure actions

No billable Azure resource was provisioned in this source pass.

The later manual session may involve configuration writes to existing resources:

- Azure Key Vault / Container App secret references;
- Container App environment values;
- APIM route configuration;
- database pickup mapping rows;
- Azure DevOps pipeline creation/update if the new coordinated YAML has not yet been registered as a pipeline.

Before any new Azure resource is provisioned, billing impact must be called out separately.

---

## 28. Required validation sequence before merge/deployment

The next sequence is intentionally strict.

### Step 1 — Run CI on the feature branch

Run:

```text
azure-pipelines-delivery-provider-production-ci.yml
```

against:

```text
feature/delivery-provider-productionization-20260816
```

Required result:

```text
ALL JOBS PASSED
```

If Maven compile/test fails, fix source first. Do not bypass the CI gate.

### Step 2 — Review the draft PR

Inspect:

- changed files;
- CI result;
- no unexpected unrelated changes;
- no secret values;
- migration behavior;
- provider blockers;
- pipeline default states.

### Step 3 — Merge only after CI/review

Do not merge merely because the branch is ahead and clean in GitHub compare.

### Step 4 — Deploy Integration Service and Order Service code/migrations

Use the normal approved deployment pipelines.

The new code must be deployed before any new provider/APIM activation.

### Step 5 — Verify migrations

Confirm:

- V110 applied;
- all provider rows exist;
- Shiprocket/Shadowfax/Porter/Delhivery are inactive initially unless an explicit activation follows;
- pickup mapping table exists.

### Step 6 — Keep Borzo sandbox path stable while onboarding Shiprocket

Do not switch Borzo to production merely to test Shiprocket.

### Step 7 — Shiprocket READ_ONLY credentials/account validation

Bind credentials and enable only read-only/API validation. Keep:

```text
SHIPROCKET_CREATE_ENABLED=false
```

and DB Shiprocket inactive.

### Step 8 — Configure APIM `/p4`

After backend deployment, expose the neutral callback route and validate unauthorized requests are rejected.

### Step 9 — Configure provider-side webhook

Set the same secret token provider-side without exposing it in chat/logs.

### Step 10 — Configure one controlled chef pickup mapping

Create/verify the provider pickup location and map the corresponding Craves `kitchen_id`.

### Step 11 — Supply actual package dimensions

Do not use guessed values.

### Step 12 — Controlled Shiprocket production-create validation

Only after all above prerequisites pass should one controlled order be used to validate the create/tracking/webhook lifecycle.

### Step 13 — Multi-provider intelligent-routing proof

Once Borzo plus Shiprocket are both genuinely executable for the same geography/order, use a fresh order with no manual provider override and capture both candidates/ranking/selection.

### Step 14 — Only then consider the coordinated production cutover

The coordinated activation pipeline should be the preferred final cutover after account/runtime evidence is complete.

---

## 29. Runtime evidence required for real multi-provider completion

For a controlled fresh order capture:

1. customer order/chef sub-order identifiers;
2. chef acceptance event;
3. `CHEF_ACCEPTED_ORDER` outbox row and publication;
4. delivery command row;
5. command consumption timestamp;
6. candidate rows from at least two executable providers;
7. provider quote IDs/cost/ETA where available;
8. intelligence strategy and scoring version;
9. rank/final score for each candidate;
10. selected provider;
11. selected quote propagated to create;
12. exactly one external booking;
13. provider delivery ID/AWB;
14. delivery job row;
15. webhook inbox or tracking reconciliation evidence;
16. canonical delivery-state change;
17. `DELIVERY_STATUS_CHANGED` event publication;
18. Order Service projection update;
19. no duplicate provider booking;
20. no unexpected delivery-command DLQ;
21. no unexpected delivery-status subscription DLQ.

Without two real candidate rows, do not label the test “multi-provider intelligent assignment.”

---

## 30. Rollback principles

### Provider-level kill switch

The first provider-specific routing kill switch is:

```text
delivery_schema.delivery_provider.is_active=false
```

This stops the intelligence/router from selecting that provider for new commands.

Then disable provider create/API execution as required.

### Global worker

Do not disable `CRAVES_DELIVERY_COMMAND_ENABLED` merely because one provider is being disabled if another healthy provider remains active.

### Endpoint environment rollback

Do not change provider endpoint environment while non-terminal jobs from that environment still require tracking/reconciliation.

### Existing jobs

Stopping a provider for new selection does not automatically cancel external bookings already created. Existing jobs must continue to be tracked/reconciled or be cancelled through the provider contract according to the operational incident plan.

### Database migration

V110 is additive and activation-neutral. Normal rollback should not drop the pickup mapping table merely to disable a provider. Disable routing/configuration rather than destructively removing schema/audit data.

---

## 31. Known items deliberately left out of this source pass

These are not accidental omissions:

- Shadowfax adapter implementation without the private contract;
- Porter adapter implementation without Enterprise API onboarding;
- Delhivery intracity adapter without the verified API product;
- guessed package dimensions;
- fake sandbox Shiprocket creates;
- random/fake courier assignment;
- fabricated provider endpoint URLs;
- provider credentials in Git/YAML/chat;
- automatic merge to `main` before CI;
- live Azure activation during source implementation;
- real customer/provider booking during source implementation.

---

## 32. Scale considerations

The provider adapter model is designed so quote fan-out can occur across multiple providers while the intelligence layer ranks candidates centrally.

For future high scale, continue to enforce:

- bounded provider timeouts;
- circuit breakers/provider isolation;
- idempotent delivery commands;
- reconciliation for ambiguous provider mutation results;
- webhook inbox deduplication;
- provider-specific rate-limit handling;
- asynchronous status processing;
- no N+1 delivery-status calls from customer order lists;
- database indexes around provider/job/reference lookup;
- provider metrics and degradation state;
- global/provider concurrency limits;
- observability by provider and endpoint.

A provider with high latency or outage must not consume the entire Integration Service request/thread pool and prevent healthy provider quotes.

The current productionization does not claim one Integration Service revision alone proves the target one-million-concurrent-user platform capacity. Full capacity planning/load testing is a separate production-readiness workstream.

---

## 33. Security considerations

The productionization branch enforces the following security posture:

- fail-closed provider flags;
- secrets referenced by name rather than embedded values;
- no secret values returned by readiness endpoint;
- admin-only readiness API;
- no-store response;
- constant-time webhook token comparison;
- webhook auth before persistence;
- webhook credential fingerprint rather than plaintext credential storage;
- APIM backend route validation;
- no raw webhook body transformation;
- explicit HTTPS endpoint validation;
- test/sandbox hostname rejection in production switches;
- no provider create from Shiprocket read-only mode.

Future Shadowfax/Porter/Delhivery authentication must follow vendor-specific signature/certificate/OAuth requirements rather than the current placeholder secret name.

---

## 34. Operational interpretation of provider flags

A common source of confusion is the difference between API access and routing activation.

### Shiprocket example

`SHIPROCKET_API_ENABLED=true` can mean authenticated read-only/serviceability access is available.

It **does not** mean Craves is allowed to create a shipment.

`SHIPROCKET_CREATE_ENABLED=true` is the mutation permission, but even that does not make the provider routable unless its production prerequisites and DB row are active.

Conceptually:

```text
API enabled
    != create enabled
    != DB provider active
    != command worker enabled
    != production ready
```

Production readiness requires all of them in the correct combination.

This distinction is deliberate and should be preserved for future providers where read operations are safer than write/create operations.

---

## 35. Manual session checklist for the next phase

The manual session should be performed with the user using Azure/Shiprocket consoles while keeping credentials out of chat.

### Source/CI

- [ ] Run delivery production CI on feature branch.
- [ ] Fix all failures, if any.
- [ ] Re-run until fully green.
- [ ] Review draft PR.
- [ ] Merge only after validation.

### Deployment

- [ ] Deploy Order Service event-contract changes.
- [ ] Deploy Integration Service provider changes.
- [ ] Verify V110 migration.
- [ ] Verify services healthy.
- [ ] Verify existing Borzo sandbox path still healthy.

### Shiprocket secrets

- [ ] Store API-user email under expected secret reference.
- [ ] Store API-user password under expected secret reference.
- [ ] Generate/store webhook token securely.
- [ ] Never paste secret values into chat.

### Shiprocket read-only

- [ ] Run standalone Shiprocket pipeline in READ_ONLY.
- [ ] Confirm provider DB row inactive.
- [ ] Confirm API auth/serviceability works.
- [ ] Confirm no shipment created.

### Webhook

- [ ] Enable APIM `/p4` route after backend deployment.
- [ ] Verify unauthenticated/wrong-token callback fails.
- [ ] Register callback in Shiprocket.
- [ ] Configure matching security token.

### Pickup mapping

- [ ] Identify controlled chef/kitchen.
- [ ] Create/verify provider pickup location.
- [ ] Persist external pickup code mapped to Craves `kitchen_id`.
- [ ] Set verified timestamp/state.

### Package/operations

- [ ] Confirm real package dimensions.
- [ ] Confirm order/billing email.
- [ ] Confirm courier attribution behavior.
- [ ] Set attribution approval only after confirmation.

### Controlled create

- [ ] Recheck queues/DLQ.
- [ ] Ensure no incompatible old Shiprocket jobs.
- [ ] Enable one controlled production create.
- [ ] Verify booking ID/AWB.
- [ ] Verify tracking.
- [ ] Verify webhook.
- [ ] Verify Order Service delivery projection.
- [ ] Verify no duplicate.

### Multi-provider proof

- [ ] Ensure Borzo and Shiprocket both executable for same order/geography.
- [ ] Place fresh test order.
- [ ] Verify two real candidates.
- [ ] Verify intelligence ranking/selection.
- [ ] Verify selected provider books once.

### Production cutover

- [ ] Drain/close old-environment provider jobs.
- [ ] Record expected queue/subscription counts.
- [ ] Run coordinated preflight.
- [ ] Run coordinated activation only if every selected provider passes.
- [ ] Capture post-cutover runtime/DB evidence.

---

## 36. Definition of done for this productionization module

The module should be considered fully productionized only when all of the following are true:

### Source

- canonical adapters implemented for every provider that is claimed operational;
- tests pass;
- CI passes;
- code reviewed and merged;
- migrations reviewed/applied.

### Configuration

- all credentials secret-backed;
- all provider endpoints authoritative;
- webhook contracts configured;
- pickup mappings verified;
- provider-specific product/account onboarding complete.

### Runtime

- Integration Service healthy;
- Order Service healthy;
- Service Bus queues/subscriptions healthy;
- no unexpected DLQs;
- status projection operational;
- create reconciliation operational;
- webhook deduplication operational.

### Provider evidence

- Borzo production lifecycle verified before production traffic relies on it;
- Shiprocket controlled lifecycle verified before broad use;
- Shadowfax/Porter/Delhivery only marked operational after their actual contracts/adapters are implemented;
- multi-provider selection proven with at least two real candidates.

### Operations

- rollback runbook understood;
- provider-level disable works without stopping healthy providers;
- environment transition does not orphan old jobs;
- monitoring/alerts prepared.

At the time this handover was written, **source implementation/hardening is ready for CI review, but the full definition of done is not yet satisfied** because CI, deployment, vendor/manual account setup and controlled runtime evidence remain.

---

## 37. Immediate next action

The immediate next action is not a production switch.

It is:

```text
Run azure-pipelines-delivery-provider-production-ci.yml
against feature/delivery-provider-productionization-20260816
```

Then review/fix any build or test failure before merge.

No provider should be moved to production until that validation is complete.

---

## 38. Handover statement

The source-side productionization pass has intentionally converted the delivery integration from a collection of provider flags into a guarded provider platform:

- Borzo's proven sandbox path is preserved and its standalone switch is safer;
- Shiprocket has a real canonical adapter and authenticated webhook path in source, with read-only vs create separated;
- provider pickup locations are modeled per Craves kitchen;
- delivery events carry provider-neutral structured data;
- status mapping is conservative and avoids Shiprocket ID-space mistakes;
- readiness is observable without secrets;
- Shadowfax, Porter and Delhivery are explicitly fail-closed pending authoritative vendor contracts;
- standalone provider switches no longer own the global worker incorrectly;
- a coordinated preflight-first production activation pipeline exists for the eventual one-go cutover;
- database migrations never activate providers;
- source CI is designed to prevent the most important safety regressions discovered during this work.

The remaining work is validation and operational onboarding—not permission to guess missing vendor or product information.
