# Craves delivery-provider productionization

This module documents the delivery-provider runtime model, safety gates and Azure DevOps controls used to move Craves from a fail-closed provider configuration to sandbox/read-only validation and, later, a coordinated production cutover.

The key rule is simple:

> An environment variable never makes a delivery provider operational by itself.

A provider may participate in intelligent assignment only when its real Spring adapter exists, its provider account/API contract is authoritative, its runtime prerequisites are ready, its database catalog row is active, and the shared delivery pipeline is healthy.

No pipeline in this module should invent provider endpoints, rider identities, package dimensions, commercial rules, or API semantics.

## 1. Current provider status

| Provider | Spring command adapter | Safe test mode | Create state today | Primary blocker |
|---|---|---|---|---|
| Borzo | Yes, proven | Real Borzo sandbox | Executable in sandbox | Production credentials/endpoint and controlled cutover |
| Shiprocket | Yes, implemented on the productionization branch | Authenticated `READ_ONLY` validation | Production create remains gated | Manual credentials, webhook, attribution approval, package dimensions and verified chef pickup mapping |
| Shadowfax | Not yet | `SIMPLE_SANDBOX` only | Blocked | Vendor private API contract/credentials |
| Porter | Not yet | `SIMPLE_SANDBOX` only | Blocked | Porter Enterprise API onboarding |
| Delhivery | No verified intracity adapter | `SIMPLE_SANDBOX` only | Blocked | Correct intracity API product/contract is not verified |

Borzo remains the only provider proven by Craves sandbox E2E evidence. Shiprocket is now implemented in source, but it must not be described as operational until this branch is built, deployed, configured and validated against the account during the manual onboarding session.

Shadowfax, Porter and Delhivery remain deliberately fail-closed. Craves does not treat an unrelated parcel/B2B API as a substitute for an intracity food-delivery contract.

## 2. Intelligent assignment ownership

Craves chooses the **delivery provider**. The selected external provider chooses/allocates its own courier or rider unless that provider contract explicitly exposes a supported rider-level selection capability.

Therefore:

- Craves does not randomly assign a fake rider;
- the intelligence engine compares real available provider candidates;
- provider candidates are generated only by active, executable adapters;
- a single executable provider can still be scored/selected, but that does not prove multi-provider competition;
- genuine multi-provider proof requires at least two simultaneously executable providers returning valid quotes for the same fresh order.

The command router uses the canonical contract:

```text
services/integration-service/src/main/java/in/craves/integration/delivery/provider/DeliveryProviderAdapter.java
```

The delivery command path consumes that contract directly.

## 3. Runtime modes

### 3.1 FAIL_CLOSED / SIMPLE_SANDBOX

For provider-gated integrations such as Shadowfax, Porter and Delhivery:

```text
targetEnvironment=SANDBOX
enableProvider=false
```

The corresponding pipeline:

- records the intended environment as `SANDBOX`;
- sets the provider API enable flag to `false`;
- forces `delivery_schema.delivery_provider.is_active=false` when the provider row exists;
- requires no provider credential;
- does not create a provider request;
- preserves the global delivery-command worker if another provider such as Borzo remains active;
- disables the global delivery-command worker only when the provider catalog contains no active provider at all.

This is a configuration baseline, not a simulated provider.

### 3.2 Borzo FULL_SANDBOX

```text
targetEnvironment=SANDBOX
enableProvider=true
```

Borzo full sandbox uses its real vendor test endpoint and secret-backed credential. The delivery command worker, reconciliation, webhook processing, tracking reconciliation, status publisher and Order Service event flow are explicitly enabled when the provider is active.

Borzo remains the currently proven E2E sandbox provider.

### 3.3 Shiprocket READ_ONLY

Shiprocket is handled differently because the public API documentation states that authenticated API requests operate against the account rather than an isolated fake shipment sandbox.

The safe validation mode is:

```text
targetEnvironment=READ_ONLY
enableProvider=true
enableCreate=false
```

For backward compatibility, selecting:

```text
targetEnvironment=SANDBOX
```

is normalized by the Craves configuration/pipeline to `READ_ONLY`. It **must not** authorize shipment creation.

READ_ONLY may authenticate and perform non-mutating/serviceability checks. It does not activate the Shiprocket provider row for intelligent routing and does not create shipments.

### 3.4 PRODUCTION

Production is explicit and fail-closed.

Borzo requires a production endpoint, production approval, secret-backed credentials, APIM callback readiness, clean queue/subscription expectations and no non-terminal Borzo job from the previous environment.

Shiprocket production create additionally requires:

```text
SHIPROCKET_API_ENVIRONMENT=PRODUCTION
SHIPROCKET_API_ENABLED=true
SHIPROCKET_CREATE_ENABLED=true
SHIPROCKET_PRODUCTION_ACTIVATION_APPROVED=true
SHIPROCKET_ATTRIBUTION_APPROVED=true
```

plus:

- API email secret;
- API password secret;
- webhook token secret;
- neutral webhook route configured in Shiprocket;
- Craves order/billing email;
- package length, breadth and height supplied from an approved Craves packaging decision;
- at least one verified `kitchen_id -> Shiprocket pickup-location code` mapping;
- healthy shared delivery processors;
- active provider catalog row only after the runtime revision is ready.

Craves does not guess package dimensions.

## 4. Provider-neutral order data

The delivery request now preserves business data that Order Service already owns instead of forcing a provider adapter to parse a display string.

Relevant provider-neutral data includes:

- original free-form pickup/drop-off address;
- address line 1 and line 2;
- area/landmark;
- city;
- state;
- postal code;
- country;
- contact details;
- coordinates;
- immutable order-item snapshots;
- quantity/unit-price values;
- declared goods subtotal/value;
- current verified-payment collection mode (`PREPAID`);
- stable Craves `kitchen_id` as the pickup-location reference.

Existing providers that accept a free-form address continue to receive it. Structured fields are additive.

## 5. Per-chef provider pickup mappings

Craves is a marketplace. A single global Shiprocket pickup name is not sufficient because different chefs have different pickup locations.

The Integration database therefore stores provider pickup mappings using the stable Craves kitchen ID:

```text
Craves kitchen_id
        |
        v
delivery_schema.delivery_provider_pickup_location
        |
        +-- provider_id
        +-- external_location_code
        +-- is_verified
```

Shiprocket create is not eligible for a chef whose kitchen does not have a verified Shiprocket pickup mapping. That provider simply produces no usable create candidate for that kitchen.

## 6. Shiprocket adapter behavior

Source area:

```text
services/integration-service/src/main/java/in/craves/integration/delivery/shiprocket/
```

The adapter implements:

- authenticated API access using API-user email/password;
- token caching;
- bounded read retries;
- hyperlocal serviceability filtering;
- deterministic courier/service selection from the provider quote;
- Craves maximum-ETA gate;
- exact selected-quote propagation into create;
- forward shipment creation only behind production mutation gates;
- deterministic source-order reconciliation after an uncertain create result;
- cancellation;
- tracking;
- canonical status mapping;
- webhook ingestion/deduplication/normalization.

Provider create is deliberately one-shot. If the HTTP result is ambiguous, Craves reconciles the deterministic client reference before any retry or provider fallback. This prevents duplicate external bookings.

## 7. Shiprocket webhook

The backend callback is intentionally provider-neutral:

```text
POST /api/v1/webhooks/delivery/p4
```

Shiprocket sends its configured security token in:

```text
x-api-key
```

The callback service:

1. rejects missing/incorrect credentials before persistence;
2. parses the JSON payload;
3. requires AWB and shipment state information;
4. fingerprints the authentication value rather than storing the plaintext token;
5. derives a stable provider-event deduplication ID;
6. writes to the shared `delivery_webhook_inbox`;
7. records a Craves receipt timestamp;
8. lets the existing generic delivery webhook processor normalize/update the locked delivery job and publish `DELIVERY_STATUS_CHANGED`.

The Craves receipt timestamp is used because a timezone-less provider timestamp must not be silently assigned an assumed timezone.

### Status-code safety

Shiprocket can expose different ID spaces such as `current_status_id` and `shipment_status_id`. Craves therefore prefers an explicit recognized status label and otherwise maps the documented shipment-status code conservatively.

For example, fulfillment-only states such as `FULFILLED` and `SELF FULFILLED` do not prove customer delivery and remain non-terminal/unknown in Craves unless a genuine last-mile status proves otherwise.

## 8. APIM webhook exposure

Pipeline:

```text
azure-pipelines-delivery-provider-webhooks-apim.yml
```

Implemented public routes:

```text
POST /api/v1/webhooks/delivery/borzo
POST /api/v1/webhooks/delivery/p4
```

The Shiprocket route defaults to disabled in the APIM pipeline until the manual account setup is complete.

APIM validates that the Spring backend route exists before exposure. The webhook policy does not rewrite the raw request body.

Shadowfax/Porter/Delhivery public webhook routes must remain disabled until their actual controllers and vendor authentication contracts exist.

## 9. Runtime readiness API

Admin-only endpoint:

```text
GET /api/v1/admin/operations/delivery-providers/readiness
```

The endpoint is protected by the existing `/api/v1/admin/**` security rules and returns no credential values.

It reports, per provider:

- execution environment;
- whether the canonical command adapter is registered;
- provider API/read-only state;
- provider create state;
- production-ready boolean;
- database catalog active state;
- verified pickup mapping count;
- explicit blocker codes.

Shared blockers include Service Bus, create reconciliation, webhook processing, tracking reconciliation, status publication and the delivery-command worker.

Expected vendor blocker codes today include:

```text
VENDOR_PRIVATE_API_CONTRACT_REQUIRED
ENTERPRISE_API_ONBOARDING_REQUIRED
INTRACITY_API_PRODUCT_NOT_VERIFIED
```

## 10. Azure DevOps pipeline chain

The source/deployment controls are:

```text
1. azure-pipelines-delivery-provider-production-ci.yml
2. azure-pipelines-integration-service.yml
3. azure-pipelines-delivery-provider-webhooks-apim.yml
4. azure-pipelines-delivery-provider-production-activation.yml          # Borzo standalone
5. azure-pipelines-shadowfax-environment.yml
6. azure-pipelines-porter-environment.yml
7. azure-pipelines-shiprocket-production-activation.yml
8. azure-pipelines-delhivery-environment.yml
9. azure-pipelines-delivery-provider-coordinated-activation.yml         # preferred final production cutover
```

Approved Azure service connection:

```text
Craves-Dev-Service-Connection
```

### 10.1 CI gate

`azure-pipelines-delivery-provider-production-ci.yml` builds/tests both Integration Service and Order Service and statically verifies:

- provider flags default fail-closed;
- Shiprocket READ_ONLY/create separation;
- neutral `/p4` webhook;
- no legacy Shiprocket single-token configuration;
- delivery status consumer wiring;
- provider environment-switch safety;
- vendor blocker controls;
- database fail-closed behavior for gated providers;
- expected APIM resource name;
- secret-reference usage;
- coordinated cutover pause/resume behavior.

Do not merge/deploy this productionization branch merely because source review looks correct. The CI pipeline must run successfully first.

## 11. Preferred coordinated production cutover

Pipeline:

```text
azure-pipelines-delivery-provider-coordinated-activation.yml
```

This is the preferred final one-go switch after all selected providers are actually eligible.

### Preflight phase

The pipeline validates **all selected providers before any runtime write**. It checks, among other things:

- Integration Service latest revision ready;
- expected delivery-command queue active/scheduled/DLQ counts;
- expected Order Service delivery-status subscription active/DLQ counts;
- selected provider secret references exist;
- production endpoints are not sandbox/test endpoints;
- required APIM webhook operations exist;
- provider catalog rows exist;
- Shiprocket verified pickup mapping exists when selected;
- Shiprocket package/attribution/order-email gates are satisfied;
- Borzo has no non-terminal jobs from the old sandbox environment before its endpoint is changed.

If any selected provider fails preflight, the cutover stops before activation.

### Cutover phase

The coordinated sequence is:

```text
preflight all selected providers
        |
        v
pause CRAVES_DELIVERY_COMMAND_ENABLED
        |
        v
stage selected production provider runtime
        |
        v
wait until new Integration revision is ready
        |
        v
activate selected provider catalog rows
        |
        v
enable Order Service CHEF_ACCEPTED_ORDER publishing
        |
        v
enable Order Service DELIVERY_STATUS_CHANGED consumer
        |
        v
resume CRAVES_DELIVERY_COMMAND_ENABLED
        |
        v
verify runtime + DB + Order Service flags
```

This ordering prevents a provider from becoming routable before its runtime adapter is ready.

The pipeline currently refuses Shadowfax, Porter and Delhivery selection with their explicit vendor blocker codes. Those blockers must only be removed when their authoritative adapters/contracts are actually implemented.

## 12. Standalone provider switches

### Borzo

```text
azure-pipelines-delivery-provider-production-activation.yml
```

Supported modes:

- simple sandbox: `SANDBOX + enableProvider=false`;
- full sandbox: `SANDBOX + enableProvider=true`;
- production downstream staging;
- production provider-create activation.

Safety characteristics:

- environment transition is blocked while any Borzo delivery job is non-terminal;
- provider runtime is made ready before the DB row becomes active;
- Order Service event publishing and delivery-status consumption are explicitly enabled;
- the global command worker is resumed only after provider activation is ready;
- disabling Borzo does not disable the global command worker if another provider remains active.

### Shiprocket

```text
azure-pipelines-shiprocket-production-activation.yml
```

Supported states:

```text
FAIL_CLOSED
READ_ONLY_PROVIDER_VALIDATION
PRODUCTION_CREATE
```

`SANDBOX` is a compatibility alias for `READ_ONLY`, not a shipment sandbox.

Provider routing activation occurs only after the new runtime revision is ready. Deactivation removes Shiprocket from the DB routing catalog before the adapter is disabled.

### Shadowfax

```text
azure-pipelines-shadowfax-environment.yml
```

Default:

```text
SANDBOX / enableProvider=false
```

The default run forces the provider DB row inactive. Enabling is blocked until both the provider-specific Java adapter and runtime configuration exist. Current explicit blocker:

```text
VENDOR_PRIVATE_API_CONTRACT_REQUIRED
```

### Porter

```text
azure-pipelines-porter-environment.yml
```

Default:

```text
SANDBOX / enableProvider=false
```

The default run forces the provider DB row inactive. Current explicit blocker:

```text
ENTERPRISE_API_ONBOARDING_REQUIRED
```

### Delhivery

```text
azure-pipelines-delhivery-environment.yml
```

Default:

```text
SANDBOX / enableProvider=false
```

The default run forces the provider DB row inactive. The pipeline requires an actual Delhivery intracity adapter before execution can be enabled. Current explicit blocker:

```text
INTRACITY_API_PRODUCT_NOT_VERIFIED
```

## 13. Secret names

No credential value belongs in Git, YAML parameters, documentation or chat.

Current/default secret names expected by the pipelines are:

```text
borzo-api-auth-token
borzo-callback-token

shiprocket-api-email
shiprocket-api-password
shiprocket-webhook-token

shadowfax-api-token        # placeholder name only until vendor auth contract is authoritative
porter-api-token           # placeholder name only until Enterprise auth contract is authoritative
delhivery-api-token        # placeholder name only until intracity auth contract is authoritative
```

The last three names describe the current pipeline guard only; their final credential model must follow the actual provider-issued contract rather than assuming a single bearer token.

## 14. Manual prerequisites before Shiprocket production create

The final manual session must complete these items without pasting secret values into chat:

- create/identify the Shiprocket API user;
- store API-user email/password in Azure secret storage using the expected secret references;
- create a strong Shiprocket webhook security token and store it as `shiprocket-webhook-token`;
- expose/verify APIM `POST /api/v1/webhooks/delivery/p4`;
- register the neutral callback URL in the Shiprocket dashboard;
- configure the same webhook token in the provider dashboard so it is sent as `x-api-key`;
- provide the Craves operational order/billing email;
- approve actual package length/breadth/height from Craves packaging operations;
- create/verify Shiprocket pickup locations for the chef kitchens used in production;
- insert/verify each `kitchen_id -> external_location_code` mapping;
- explicitly approve the courier-attribution behavior after account validation;
- run authenticated READ_ONLY/serviceability validation before create activation.

## 15. Manual prerequisites before other providers

### Shadowfax

- obtain the current private merchant API contract;
- obtain sandbox/test credentials and endpoint from the vendor;
- confirm quote/create/cancel/track/webhook contracts;
- implement Spring adapter and authentication exactly from that contract;
- validate real sandbox lifecycle before production.

### Porter

- complete Enterprise API onboarding;
- obtain authoritative credentials, endpoint and callback documentation;
- implement and validate the canonical Spring adapter before removing the blocker.

### Delhivery

- confirm the exact Delhivery product/API intended for intracity food delivery;
- do not treat a B2B Surface/parcel contract as proof of same-day food-delivery capability;
- implement only after the correct API contract and credentials are issued.

## 16. Production cutover prerequisites

Before any provider endpoint changes environment:

- deploy the exact reviewed Integration Service revision first;
- run the delivery-provider CI pipeline successfully;
- confirm migrations are applied;
- check Service Bus delivery-command queue counts;
- check delivery-status subscription counts/DLQ;
- resolve or cancel provider jobs that would become untrackable after an endpoint/environment change;
- confirm APIM webhook route and backend health;
- confirm Order Service event outbox publisher and delivery-status consumer;
- capture the pre-cutover provider catalog state;
- keep rollback values/evidence ready.

## 17. Rollback principles

The first provider-level kill switch is to remove that provider from intelligent routing:

```text
delivery_schema.delivery_provider.is_active=false
```

Then disable its create/API execution flag as appropriate.

The global delivery-command worker must **not** be disabled when another healthy provider remains active.

A provider endpoint must not be switched from sandbox to production, or vice versa, while non-terminal jobs from that provider/environment still require tracking/reconciliation.

Borzo retains its dedicated rollback pipeline where applicable. New provider-specific rollback automation should be finalized together with each authoritative production adapter.

## 18. Validation evidence required before calling multi-provider routing complete

At minimum, use a fresh controlled order and capture:

1. `CHEF_ACCEPTED_ORDER` emitted by Order Service;
2. delivery command scheduled and consumed;
3. at least two active executable adapters return valid candidate results;
4. `delivery_assignment` records strategy/scoring version and ranked candidates;
5. selected provider/candidate is persisted;
6. exactly one external create succeeds or uncertain-create reconciliation resolves safely;
7. `delivery_job` stores provider ID/provider delivery ID;
8. webhook or tracking reconciliation advances canonical delivery state;
9. `DELIVERY_STATUS_CHANGED` is published;
10. Order Service delivery projection consumes the event;
11. no unexpected Service Bus dead letters exist;
12. no duplicate external delivery is created.

Until step 3 is possible with at least two real providers, Craves has intelligent-routing code but not production evidence of multi-provider competition.
