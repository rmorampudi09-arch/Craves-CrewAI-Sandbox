# Customer Delivery Status APIM Module

## Purpose

This module exposes the Order Service customer delivery read model through the existing Craves Azure API Management instance.

Public route:

```http
GET /api/v1/orders/{orderId}/delivery-status
Authorization: Bearer <Craves access token>
```

The endpoint belongs to the authenticated customer who owns the chef-specific order. Order Service remains authoritative for JWT validation, customer-role enforcement, order ownership, and response construction.

## Architecture

```text
Customer web/mobile client
  -> existing Azure API Management gateway
  -> operation-level Bearer-header guard
  -> exact Order Container App backend route
  -> Order Service JWT validation
  -> customer-role and ownership check
  -> order delivery projection + bounded history
  -> no-cache APIM response headers
```

## Why APIM checks only Bearer syntax

Craves currently validates its access token inside each Spring Boot service using the configured issuer, audience, and verification key. This module does not duplicate that cryptographic policy in APIM because doing so would create a second authentication configuration that could drift from the backend.

APIM rejects requests that have no `Authorization` header or do not begin with `Bearer `. The unchanged header is then forwarded to Order Service, which performs the authoritative JWT checks.

This means:

- APIM blocks obviously unauthenticated traffic early;
- Order Service still rejects expired, forged, wrong-audience, wrong-issuer, and wrong-role tokens;
- ownership checks remain next to Order-owned data;
- no Firebase project identifier, JWT key, internal secret, or access token is committed to source.

## Public response

An owned order returns HTTP 200 even before the first delivery projection exists. In that state, delivery fields are null and `history` is empty.

```json
{
  "orderId": "11111111-2222-3333-4444-555555555555",
  "deliveryJobId": null,
  "providerId": null,
  "status": null,
  "trackingUrl": null,
  "observedAt": null,
  "history": []
}
```

After delivery updates are accepted, the same response contains the current provider-neutral status and up to 100 chronological history entries.

The response intentionally excludes:

- raw provider webhook payloads;
- provider credential material;
- provider delivery transaction details that are not part of the customer contract;
- pickup address and chef-private contact data;
- internal inbox/outbox identifiers;
- worker retry and dead-letter metadata.

## Files

```text
contracts/openapi/order-delivery-status-v1.openapi.json
infra/apim/order-delivery-status/order-delivery-status-policy.xml
scripts/apim/configure-order-delivery-status-apim.sh
scripts/apim/verify-order-delivery-status-apim.sh
scripts/apim/rollback-order-delivery-status-apim.sh
azure-pipelines-order-delivery-status-apim-ci.yml
azure-pipelines-order-delivery-status-apim.yml
azure-pipelines-order-delivery-status-apim-status.yml
azure-pipelines-order-delivery-status-apim-rollback.yml
```

## Fail-closed behavior

The configuration script refuses to continue when:

- the Order Container App is not running, ready, and healthy;
- the Order deployment does not contain `CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED`;
- more than one APIM API already owns `api/v1/orders`;
- a requested API ID conflicts with the API already owning the public path;
- an existing API uses a different path;
- an existing shared API requires an APIM subscription key;
- the operation policy template is missing;
- the policy backend placeholder is not rendered;
- the configured operation or policy cannot be read back exactly.

The script does not relax an existing API-wide subscription setting. That is important because changing `subscriptionRequired` on a shared API would affect every existing operation, not only delivery tracking.

## Existing API versus dedicated API

The script first searches APIM for an API whose path is exactly:

```text
api/v1/orders
```

If exactly one exists, the new operation is attached to it.

If none exists, the script creates:

```text
API ID: craves-order-customer-v1
Path: api/v1/orders
Subscription required: false
Protocol: HTTPS
```

If multiple APIs use the path, the script fails without changing APIM.

## Operation-level backend override

The operation policy targets:

```text
https://<current-order-container-app-fqdn>/api/v1/orders
```

APIM appends:

```text
/{orderId}/delivery-status
```

This operation-level backend mapping avoids changing the service URL of an existing shared Order API.

## Response safety headers

APIM adds:

```text
Cache-Control: no-store, no-cache, must-revalidate
Pragma: no-cache
X-Content-Type-Options: nosniff
```

Live delivery state must not be stored by shared proxies or browser caches.

## No CORS change

This module does not add an API-level or operation-level CORS policy. CORS is a gateway-wide/web-client decision and must remain aligned with the approved Craves domains. The module will not introduce wildcard origins or credentialed wildcard CORS.

## No billable resource creation

The rollout configures an API and one operation inside the already provisioned APIM instance. It does not create:

- a new APIM instance;
- a new Container App;
- a new PostgreSQL server;
- Service Bus entities;
- Key Vault secrets;
- DNS records;
- certificates;
- delivery-provider resources.

The existing APIM instance continues to incur its normal configured cost. This module does not change its SKU or capacity.

## Build-only validation

Pipeline:

```text
azure-pipelines-order-delivery-status-apim-ci.yml
```

The pipeline performs no Azure write. It verifies:

- Bash syntax for configure, verify, and rollback scripts;
- OpenAPI JSON parsing and expected route/security contract;
- APIM policy XML parsing;
- required Bearer/header/backend/no-cache controls;
- absence of wildcard CORS;
- conflict and rollback guards;
- absence of credential literals;
- absence of Borzo or delivery-execution activation.

## Controlled APIM rollout

Pipeline:

```text
azure-pipelines-order-delivery-status-apim.yml
```

Default parameters:

```text
resourceGroupName: rg-craves-prodlow-centralindia
apimName: apim-craves-prodlow-l3ing6
orderContainerAppName: ca-craves-order-service-prodlow
apiId: empty, auto-resolve by path
runAuthenticatedSmoke: false
```

The pipeline:

1. validates repository inputs;
2. verifies Order Service is healthy and contains the delivery-status deployment marker;
3. safely resolves or creates the APIM API;
4. creates or updates only `get-order-delivery-status`;
5. applies the operation-level policy;
6. reads back the API, operation, and policy;
7. verifies the public unauthenticated request returns the APIM-generated HTTP 401 response.

## Optional authenticated smoke test

Set:

```text
runAuthenticatedSmoke: true
testOrderId: <owned chef-specific order UUID>
```

Create an Azure DevOps secret variable:

```text
CRAVES_CUSTOMER_TEST_TOKEN
```

Paste a short-lived Craves customer access token into that secret variable. Do not paste it into source, pipeline parameters, documentation, or chat.

The smoke step expects HTTP 200 and validates:

- `orderId` equals the supplied order;
- `history` is an array;
- nullable delivery fields have the correct types when present.

## Read-only status pipeline

```text
azure-pipelines-order-delivery-status-apim-status.yml
```

This performs no APIM write. It validates the current API, operation, policy, backend mapping, Order revision health, and unauthenticated HTTP 401 guard.

## Guarded rollback

```text
azure-pipelines-order-delivery-status-apim-rollback.yml
```

Required parameter:

```text
confirmOperationRollback: true
```

Default rollback behavior removes only:

```text
get-order-delivery-status
```

It preserves the API itself.

Deleting the API additionally requires:

```text
deleteEmptyDedicatedApi: true
```

The script then deletes the API only when:

- its ID is exactly `craves-order-customer-v1`;
- it contains zero remaining operations.

A shared API or non-empty API cannot be deleted by the rollback script.

## Required deployment order

This is a stacked module. The safe order is:

1. Run PR #25 downstream Java 21 CI.
2. Merge PR #25.
3. Deploy Order Service from `main`.
4. Verify Flyway V9 and `CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED=false`.
5. Enable the Order delivery-status consumer.
6. Run controlled synthetic delivery-status validation.
7. Enable the Integration delivery-status publisher only after the consumer and DLQ checks pass.
8. Merge this APIM module after its build-only CI passes.
9. Run the APIM rollout pipeline.
10. Run the APIM status pipeline.
11. Run the optional authenticated customer smoke test using an owned order.

The APIM pipeline intentionally fails if step 3 has not happened because the Order deployment marker will be absent.

## Provider state remains disabled

This module never sets any of the following to true:

```text
CRAVES_DELIVERY_COMMAND_ENABLED
CRAVES_DELIVERY_RECONCILIATION_ENABLED
CRAVES_DELIVERY_WEBHOOK_PROCESSING_ENABLED
CRAVES_DELIVERY_TRACKING_RECONCILIATION_ENABLED
CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED
BORZO_API_ENABLED
```

Provider webhook, tracking, booking, and Borzo activation remain separate controlled stages.

## Local validation

From the repository root:

```bash
bash -n scripts/apim/configure-order-delivery-status-apim.sh
bash -n scripts/apim/verify-order-delivery-status-apim.sh
bash -n scripts/apim/rollback-order-delivery-status-apim.sh
python3 -m json.tool contracts/openapi/order-delivery-status-v1.openapi.json >/dev/null
python3 - <<'PY'
import xml.etree.ElementTree as ET
ET.parse('infra/apim/order-delivery-status/order-delivery-status-policy.xml')
print('Policy XML is valid.')
PY
```

The configure and verify scripts require an authenticated Azure CLI session and are intended for Azure DevOps or an authorized operator shell.

## Manual steps required

### Azure DevOps

- Register the three APIM pipelines after merge.
- Use the existing `AZURE_SERVICE_CONNECTION` variable.
- Add `CRAVES_CUSTOMER_TEST_TOKEN` only when running the optional authenticated smoke test.
- Mark the token variable secret.
- Delete or rotate the short-lived test token after validation.

### Azure Portal

No manual APIM resource creation is required. If the script reports multiple APIs at the same path or an existing subscription-key requirement, inspect the current APIM API design before deciding how to consolidate it.

### DNS and certificates

No DNS or certificate change is required because the route uses the existing APIM gateway hostname. Custom-domain work remains a separate task.

## Operational checks

After rollout, verify:

```text
GET without Authorization -> 401 AUTHENTICATION_REQUIRED
GET with malformed Authorization -> 401 AUTHENTICATION_REQUIRED
GET with invalid/expired Bearer token -> backend 401
GET with non-customer role -> 403
GET with another customer's order ID -> 404
GET with owned order before delivery -> 200 with null projection and empty history
GET with owned active delivery -> 200 with current status and history
Cache-Control -> no-store, no-cache, must-revalidate
```

## Risks

- APIM configuration propagation is not instantaneous; verification retries the unauthenticated request for a bounded period.
- A shared API with `subscriptionRequired=true` needs an explicit architecture decision; this module refuses to change it automatically.
- The APIM Bearer check is syntactic, not cryptographic. Order Service remains the source of truth for token validation.
- The customer tracking URL originates from a provider. Integration and Order validate and persist it; clients must still open external tracking links using safe browser controls.
- The public response currently exposes provider ID but not provider delivery ID or raw provider payload. Changing that contract requires privacy review.

## Deferred work

- Custom APIM domain such as `api.craves.in` if not already configured.
- Approved web-origin CORS policy.
- WAF/Front Door rate-limiting strategy for higher scale.
- Customer mobile/web delivery timeline UI.
- Chef-facing courier status UI.
- Push notifications driven by the existing notification outbox.
- Production provider activation.
- Delivery serviceability, pricing, compensation, refund, and SLA product rules.
