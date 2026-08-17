# Craves Customer Delivery Status APIM Handover

**Document date:** 2026-07-28  
**Repository:** `rmorampudi09-arch/Craves-Build-platform`  
**Feature branch:** `feature/delivery-status-apim-route`  
**Stacked base branch:** `feature/delivery-status-downstream-consumers`  
**Parent draft PR:** PR #25  
**Module status:** Code and operational assets complete; CI, merge, Azure rollout, and authenticated smoke validation pending  
**Azure environment:** Production-low-cost engineering environment  
**Confidentiality:** Internal Craves engineering and operations use

---

## 1. Executive summary

This module adds the public Azure API Management route for customers to read the provider-neutral delivery lifecycle of an owned chef-specific order.

The public route is:

```http
GET /api/v1/orders/{orderId}/delivery-status
Authorization: Bearer <Craves customer access token>
```

No Azure operation was executed while building this branch. The module currently exists only in GitHub source.

## 2. User instruction that initiated this module

The user explicitly asked not to run the pipelines yet and requested that the next module also be completed so all required pipelines can be executed later in a controlled one-by-one sequence.

The selected next module was the APIM exposure layer for the delivery-status endpoint implemented in parent PR #25.

## 3. Why this was the correct next module

Parent PR #25 implements the downstream delivery-status consumer and customer read endpoint inside Order Service. Without APIM configuration, web and mobile clients do not have a governed public gateway route for that endpoint.

The APIM layer is therefore the next dependency after:

```text
Integration delivery status publisher
  -> Service Bus
  -> Order delivery status consumer
  -> Order customer delivery status endpoint
```

## 4. Stacked branch decision

This branch starts from the exact head of parent PR #25:

```text
90c34ef617245ae812916c2b31226248577d8ce3
```

It is intentionally stacked instead of starting from `main` because the APIM module depends on files and behavior that are not yet merged into `main`.

## 5. Required merge order

The correct merge order is:

```text
PR #25 downstream consumer
  -> merge to main
  -> rebase/retarget APIM PR to main if required
  -> APIM module CI
  -> APIM module merge
```

The APIM rollout pipeline must not be run before the parent Order Service code is deployed.

## 6. Public request flow

```text
Customer web/mobile app
  -> existing Craves APIM gateway
  -> operation-level Authorization/Bearer syntax guard
  -> Order Container App delivery-status route
  -> Spring Security JWT verification
  -> CUSTOMER role check
  -> order ownership query
  -> current delivery projection and bounded history
  -> APIM no-cache response headers
```

## 7. Public URL

Current APIM hostname:

```text
https://api.craves.in
```

Final route:

```text
https://api.craves.in/api/v1/orders/{orderId}/delivery-status
```

## 8. Backend URL

The configuration script resolves the current Order Container App ingress FQDN dynamically.

The operation-level backend base URL is rendered as:

```text
https://<order-container-app-fqdn>/api/v1/orders
```

APIM appends:

```text
/{orderId}/delivery-status
```

## 9. Why the backend is overridden at operation level

The script may attach the operation to an existing API at `api/v1/orders`.

Changing the API-wide `serviceUrl` could break unrelated cart, checkout, customer-order, or chef-order operations. Therefore this module uses `set-backend-service` only inside the new delivery-status operation policy.

## 10. Authentication boundary

APIM checks that:

- the `Authorization` header exists;
- the value begins with `Bearer `, case-insensitively.

APIM does not perform the authoritative cryptographic JWT validation in this module.

## 11. Why JWT verification remains in Order Service

Order Service already owns the approved Craves token configuration:

```text
CRAVES_JWT_ISSUER
CRAVES_JWT_AUDIENCE
CRAVES_JWT_VERIFICATION_PEM_BASE64
```

Duplicating issuer, audience, key, or Firebase/Craves auth details in APIM would create configuration drift and an additional secret/key-management surface.

## 12. Backend security responsibilities

Order Service remains responsible for:

- token signature verification;
- expiry verification;
- issuer verification;
- audience verification;
- role extraction;
- CUSTOMER role enforcement;
- customer identity extraction;
- order ownership enforcement.

## 13. APIM early rejection response

A missing or malformed Bearer header receives:

```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json
```

```json
{
  "error": "AUTHENTICATION_REQUIRED",
  "message": "A Bearer access token is required."
}
```

## 14. No token modification

The policy does not replace, decode, log, or transform the incoming Authorization header.

APIM forwards it unchanged to Order Service.

## 15. Public response contract

The OpenAPI contract is stored at:

```text
contracts/openapi/order-delivery-status-v1.openapi.json
```

## 16. Response before a delivery projection exists

An owned order can return HTTP 200 with null delivery fields:

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

This matches the actual parent PR #25 DTO and query service.

## 17. Response after delivery updates exist

The response can contain:

```text
orderId
deliveryJobId
providerId
status
trackingUrl
observedAt
history
```

The history is chronological and bounded to 100 entries by Order Service.

## 18. Data intentionally not exposed

The API does not expose:

- raw provider webhook JSON;
- provider API credentials;
- provider secret material;
- provider delivery ID;
- delivery inbox payload;
- Integration outbox rows;
- worker lease information;
- retry counters;
- dead-letter metadata;
- customer drop-off private details;
- chef pickup address or private contact details.

## 19. Provider ID exposure

The current parent DTO exposes a provider-neutral/provider identifier such as the selected provider ID.

It does not expose the provider delivery transaction ID.

Any future expansion of provider details requires privacy and product review.

## 20. No-cache decision

Delivery state is live operational data. APIM adds:

```text
Cache-Control: no-store, no-cache, must-revalidate
Pragma: no-cache
X-Content-Type-Options: nosniff
```

## 21. CORS decision

This module does not add CORS.

The reason is that allowed web origins are an environment and domain decision. Adding wildcard CORS here could weaken existing gateway controls.

## 22. Rate-limit decision

This module does not introduce a new operation-specific rate limit.

Rate limiting should be coordinated with the broader APIM/Front Door policy so customer polling, mobile refresh behavior, and scale targets are handled consistently.

## 23. APIM resource decision

No new APIM instance is created.

The existing APIM instance is reused:

```text
apim-craves-prodlow-l3ing6
```

## 24. Billing impact

The module does not change APIM SKU, units, regions, networking, or capacity.

It adds one logical API operation to the existing instance. No new billable Azure resource is provisioned by the scripts.

## 25. Main policy file

```text
infra/apim/order-delivery-status/order-delivery-status-policy.xml
```

## 26. Policy backend placeholder

The committed policy contains:

```text
__ORDER_DELIVERY_STATUS_BACKEND_URL__
```

The configure script replaces the placeholder with the current Order Container App backend URL in a temporary file.

## 27. Placeholder safety check

The configure script fails if the placeholder remains after rendering.

This prevents publishing an invalid policy that points to a literal placeholder.

## 28. Configure script

```text
scripts/apim/configure-order-delivery-status-apim.sh
```

## 29. Configure script defaults

```text
RG=rg-craves-prodlow-centralindia
APIM=apim-craves-prodlow-l3ing6
ORDER_APP=ca-craves-order-service-prodlow
API_PATH=api/v1/orders
OPERATION_ID=get-order-delivery-status
DEFAULT_API_ID=craves-order-customer-v1
```

## 30. Configure script prerequisites

The script requires:

```text
az
curl
jq
sed
```

It also requires an authenticated Azure identity with permission to read Container Apps and create/update APIM APIs, operations, and policies.

## 31. Order deployment precondition

The script verifies the Order Container App contains:

```text
CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED
```

The normal Order pipeline introduced in PR #25 writes this flag as false after deployment.

Its presence therefore acts as a deployment marker for the new Order image.

## 32. Order health preconditions

The script requires:

```text
latestRevisionName == latestReadyRevisionName
runningStatus == Running
revision healthState == Healthy
GET /actuator/health succeeds
```

## 33. API path resolution

The intended APIM path is:

```text
api/v1/orders
```

The script queries all APIM APIs using exactly that path.

## 34. Multiple-path conflict behavior

If more than one API owns `api/v1/orders`, the script prints the conflicting API IDs and stops.

No APIM write occurs after this conflict is detected.

## 35. Explicit API ID behavior

An operator can supply `API_ID`.

If the public path is already owned by a different API ID, the script fails rather than creating a duplicate or moving the route.

## 36. Existing API behavior

If exactly one API owns the path, the operation is attached to that API.

The script verifies the API path but does not overwrite the existing API-wide backend URL.

## 37. Existing subscription-key safeguard

If an existing API has:

```text
subscriptionRequired=true
```

this module fails.

It does not set the property to false because that would affect all operations on the shared API.

## 38. New dedicated API behavior

If no API owns the path, the script creates:

```text
API ID: craves-order-customer-v1
Display name: Craves Customer Orders API
Path: api/v1/orders
Protocol: HTTPS
Subscription required: false
```

## 39. Operation definition

```text
Operation ID: get-order-delivery-status
Method: GET
Template: /{orderId}/delivery-status
Required parameter: orderId
```

## 40. Operation responses documented in APIM

```text
200 Current provider-neutral delivery status
401 Authentication required
403 Customer-role/authorization failure
404 Owned order not found
```

## 41. Read-back verification

After writing, the configure script reads back:

- operation method;
- URL template;
- operation policy;
- Bearer guard;
- backend mapping;
- no-cache policy.

## 42. Verification script

```text
scripts/apim/verify-order-delivery-status-apim.sh
```

## 43. Verification script Azure behavior

The verification script is read-only for Azure configuration.

It reads Order Container App, APIM API, operation, policy, and gateway URL.

## 44. Unauthenticated gateway test

The verification script calls:

```text
GET /api/v1/orders/00000000-0000-0000-0000-000000000000/delivery-status
```

without an Authorization header.

Expected result:

```text
HTTP 401
body contains AUTHENTICATION_REQUIRED
```

## 45. APIM propagation handling

APIM changes can take time to propagate to the gateway.

The verification script retries the unauthenticated request for a bounded number of attempts and then fails with the last HTTP status and response snippet.

## 46. Optional authenticated smoke test

The same verification script supports:

```text
CRAVES_ACCESS_TOKEN
TEST_ORDER_ID
```

Both must be supplied together.

## 47. Authenticated smoke expected result

The test expects HTTP 200 and verifies:

- response `orderId` equals `TEST_ORDER_ID`;
- `history` is an array;
- nullable delivery fields are either null or correctly typed.

## 48. Secret handling

The access token must be stored in Azure DevOps as:

```text
CRAVES_CUSTOMER_TEST_TOKEN
```

It must be marked secret.

## 49. Secret non-disclosure

The scripts use:

```text
set +x
```

They do not echo the token.

The token must never be committed, pasted into documentation, entered as a visible pipeline parameter, or pasted into chat.

## 50. Rollback script

```text
scripts/apim/rollback-order-delivery-status-apim.sh
```

## 51. Rollback confirmation

Rollback requires:

```text
CONFIRM_OPERATION_ROLLBACK=true
```

## 52. Default rollback scope

Default rollback removes only:

```text
get-order-delivery-status
```

The containing API is preserved.

## 53. Optional API deletion

Deleting an API additionally requires:

```text
DELETE_EMPTY_DEDICATED_API=true
```

## 54. API deletion safeguards

The API can be deleted only when:

- its ID equals `craves-order-customer-v1`;
- no operations remain.

A shared or non-empty API cannot be deleted.

## 55. Rollback isolation

Rollback does not change:

- Order Container App image;
- Order feature flags;
- Integration feature flags;
- Service Bus entities;
- PostgreSQL data;
- delivery history;
- notifications;
- Borzo/provider configuration.

## 56. OpenAPI contract file

```text
contracts/openapi/order-delivery-status-v1.openapi.json
```

The contract is documentation and validation input. The script deliberately does not import it over an existing shared API because an API import could replace unrelated operations.

## 57. Build-only CI pipeline

```text
azure-pipelines-order-delivery-status-apim-ci.yml
```

## 58. CI pipeline Azure effect

The CI pipeline performs no Azure login and no Azure write.

## 59. CI Bash checks

It runs:

```text
bash -n configure script
bash -n verify script
bash -n rollback script
```

## 60. CI contract checks

It verifies:

- OpenAPI 3.0.3;
- exact public path;
- operation ID;
- Bearer security scheme;
- 200/401/403/404 responses;
- XML parseability;
- backend placeholder;
- Authorization/Bearer checks;
- no-cache controls.

## 61. CI safety checks

It verifies:

- conflict guards exist;
- subscription-setting guard exists;
- Order deployment marker check exists;
- rollback confirmations exist;
- no provider activation is added;
- no likely credential literal is committed.

## 62. APIM rollout pipeline

```text
azure-pipelines-order-delivery-status-apim.yml
```

## 63. Rollout pipeline default mode

Default mode:

```text
runAuthenticatedSmoke=false
```

This performs static validation, APIM configuration, read-back verification, and unauthenticated 401 validation.

## 64. Authenticated rollout mode

Set:

```text
runAuthenticatedSmoke=true
testOrderId=<real owned order UUID>
```

and configure the secret variable:

```text
CRAVES_CUSTOMER_TEST_TOKEN
```

## 65. Placeholder test order safeguard

Authenticated mode fails if `testOrderId` remains:

```text
00000000-0000-0000-0000-000000000000
```

## 66. Status pipeline

```text
azure-pipelines-order-delivery-status-apim-status.yml
```

This runs the read-only verification without an authenticated token.

## 67. Rollback pipeline

```text
azure-pipelines-order-delivery-status-apim-rollback.yml
```

Default confirmations are false.

## 68. Pipeline service connection

All Azure pipelines use the existing variable:

```text
AZURE_SERVICE_CONNECTION
```

No new credential is committed.

## 69. Parent PR dependency

The APIM configuration script expects the Order deployment marker introduced in PR #25.

Therefore APIM rollout before Order deployment fails by design.

## 70. Required full pipeline sequence

Later, execute in this order:

```text
1. azure-pipelines-delivery-status-downstream-ci.yml on PR #25 branch
2. merge PR #25
3. azure-pipelines-order-service.yml from main
4. azure-pipelines-delivery-status-rollout-status.yml
5. azure-pipelines-order-delivery-status-consumer-enable.yml
6. controlled synthetic consumer validation
7. azure-pipelines-integration-delivery-status-publisher-enable.yml
8. azure-pipelines-delivery-status-rollout-status.yml
9. azure-pipelines-order-delivery-status-apim-ci.yml on APIM branch
10. merge APIM PR
11. azure-pipelines-order-delivery-status-apim.yml from main
12. azure-pipelines-order-delivery-status-apim-status.yml
13. optional authenticated APIM smoke test
```

## 71. No provider activation in this sequence

Even after APIM rollout, keep:

```text
CRAVES_DELIVERY_COMMAND_ENABLED=false
CRAVES_DELIVERY_RECONCILIATION_ENABLED=false
CRAVES_DELIVERY_WEBHOOK_PROCESSING_ENABLED=false
CRAVES_DELIVERY_TRACKING_RECONCILIATION_ENABLED=false
BORZO_API_ENABLED=false
```

The Integration status publisher is enabled only after a controlled synthetic downstream validation.

## 72. Current Azure state unchanged by this branch

No Azure CLI pipeline was run for this module.

No APIM API or operation was created by this development session.

## 73. Current Git state

The APIM branch contains the complete source module and is stacked on the exact PR #25 head.

A draft stacked pull request should be opened only after final static review confirms the head and changed files.

## 74. Manual Azure DevOps steps pending

- Register the APIM CI pipeline.
- Run APIM CI after the parent PR is stable.
- Merge in the correct order.
- Register rollout, status, and rollback pipelines after merge.
- Add the optional secret token only for authenticated validation.

## 75. Azure Portal steps pending

No portal action is expected for the normal path.

Portal inspection is required only if:

- multiple APIs use `api/v1/orders`;
- the existing API requires a subscription key;
- the Azure DevOps service principal lacks APIM write permission;
- gateway propagation or policy compilation fails.

## 76. DNS steps pending

No DNS change is required for the current `azure-api.net` hostname.

A future `api.craves.in` custom domain is a separate DNS/certificate module.

## 77. Certificate steps pending

No certificate is created or bound by this module.

## 78. Local validation commands

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

## 79. Expected unauthenticated test

```bash
curl -i \
  "https://api.craves.in/api/v1/orders/00000000-0000-0000-0000-000000000000/delivery-status"
```

Expected:

```text
HTTP 401
AUTHENTICATION_REQUIRED
```

## 80. Expected authenticated test

```bash
curl -i \
  -H "Authorization: Bearer $CRAVES_TOKEN" \
  "https://api.craves.in/api/v1/orders/$ORDER_ID/delivery-status"
```

Do not print or commit `$CRAVES_TOKEN`.

## 81. Ownership privacy behavior

Order Service queries by both:

```text
order ID
customer identity ID from token
```

An order owned by another customer is therefore returned as not found rather than revealing its existence.

## 82. Customer role behavior

A valid token without the CUSTOMER role receives HTTP 403.

## 83. Invalid token behavior

A syntactically Bearer-formatted but expired, forged, wrong-issuer, or wrong-audience token passes the APIM syntax check and is rejected by Spring Security.

This is intentional defense in depth, not an authentication gap.

## 84. Operational monitoring after rollout

Monitor:

- APIM 401 rate;
- backend 401/403/404 rate;
- backend 5xx rate;
- APIM backend latency;
- Order Container App health;
- Order delivery-status consumer DLQ;
- Integration status outbox lag;
- notification outbox lag.

## 85. Scaling note

The current endpoint performs one current-order query and one bounded history query.

For the current 50–100 concurrent-user deployment target, this is acceptable with the configured PostgreSQL connection pool and low polling frequency.

At much higher scale, delivery status should be cached carefully or pushed through notifications/websocket-like mechanisms, but live ownership and no-store rules must remain intact.

## 86. Polling guidance

No polling interval is hardcoded in APIM.

Web/mobile clients should avoid aggressive polling. The future UI module should define foreground/background refresh behavior and use push notifications where appropriate.

## 87. Tracking URL safety

The provider tracking URL is customer-visible.

Clients should:

- allow only HTTP/HTTPS;
- show the destination host before opening externally where appropriate;
- avoid injecting the URL into HTML;
- never append customer tokens to it.

## 88. Error-body consistency risk

The APIM-generated missing-Bearer response uses the Craves error shape.

Backend Spring Security responses may use a different exact body unless globally standardized. HTTP status remains authoritative.

A future gateway error-contract module can standardize all authentication errors.

## 89. Existing API conflict risk

APIM may already contain an Order API created manually or by earlier work.

The script handles a single exact-path API safely but deliberately stops for ambiguous state.

## 90. Subscription-key risk

If the existing API requires a subscription key, the module does not remove that requirement.

The architecture decision must determine whether clients should send both a subscription key and Bearer token or whether Order customer APIs should be reorganized into a separate non-subscription API.

## 91. CORS pending item

Approved web origins must be supplied before adding CORS.

Do not add `*` with credentials.

## 92. Custom domain pending item

Potential future route:

```text
https://api.craves.in/api/v1/orders/{orderId}/delivery-status
```

This requires DNS, certificate, APIM hostname configuration, and client environment updates.

## 93. Frontend pending item

Next client work should include:

- current delivery status label;
- chronological timeline;
- courier tracking link;
- refresh behavior;
- terminal-state presentation;
- accessibility labels;
- safe empty state before delivery creation.

## 94. Mobile pending item

React Native should consume the same OpenAPI contract and avoid storing access tokens or delivery responses in insecure persistent logs.

## 95. Chef UI pending item

The customer route is not a chef route.

Chef courier status may require a separate endpoint with chef/kitchen ownership checks.

## 96. Notification pending item

Parent PR #25 already writes customer delivery notifications into the existing Order notification outbox for selected status transitions.

The notification dispatcher/Notification Service rollout remains governed by the existing notification feature flags.

## 97. Provider pending item

Borzo/provider webhook and tracking workers remain disabled until downstream status publication, Order consumption, notifications, and customer read path are validated.

## 98. Product decisions not invented

This module does not define:

- delivery fee;
- serviceability radius;
- cancellation compensation;
- failed-delivery refund;
- return policy;
- SLA;
- provider priority;
- commission;
- GST;
- FSSAI consequences.

## 99. Security conclusion

The module provides a safe public gateway operation without duplicating keys or weakening existing API-wide policies.

Authentication and ownership remain in the service that owns the data.

## 100. Final implementation conclusion

The customer delivery-status APIM module is complete in source control.

Remaining work is operational only:

```text
CI
stacked PR review and merge order
Order deployment and downstream activation
APIM rollout
read-only status verification
authenticated customer smoke test
```

No Azure state, provider state, secret, DNS, certificate, or billable resource was changed during implementation.
