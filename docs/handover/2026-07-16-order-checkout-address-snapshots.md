# Order Checkout Address Snapshot Handover

Date: 2026-07-16  
Repository: `rmorampudi09-arch/Craves-Build-platform`  
Implementation branch: `feature/order-checkout-address-snapshots`  
Primary service: Order Service  
Supporting service contract: User-Chef Service internal customer-address API  
Stack: Java 21, Spring Boot 3, Maven, PostgreSQL, Azure Container Apps, Azure API Management

## 1. Executive summary

This module closes the checkout-address gap in Craves Order Service.

Before this change, checkout could create a parent checkout and one order per kitchen without identifying a final delivery address. That was sufficient for early cart and payment tests, but it was not safe for real delivery scheduling because temporary GPS browsing coordinates are not a verified delivery address and source profiles may change after order creation.

After this change:

1. `POST /api/v1/checkout` requires a saved `deliveryAddressId`.
2. Order Service verifies the address through User-Chef Service using an internal service credential.
3. User-Chef Service remains the owner of saved customer addresses.
4. Order Service validates that the address belongs to the authenticated customer and is active.
5. Order Service snapshots customer drop-off details.
6. Order Service snapshots each kitchen's pickup details.
7. Parent checkout, kitchen-specific orders, order items, package metadata, and both address snapshots commit in one database transaction.
8. Later edits or deletions in source profiles cannot alter existing orders.

No delivery serviceability radius, provider assignment, delivery price, commission, GST, tax, or compliance rule was added.

## 2. Architecture alignment

The approved HLD defines:

- User-Chef Service as the owner of customer profiles and addresses.
- Catalog Service as the owner of kitchen and menu information.
- Order Service as the owner of carts, checkout, orders, order items, and order state.
- A multi-chef cart as one parent checkout split into one chef-specific sub-order per kitchen.
- Integration Service as the owner of delivery-provider interaction.

This implementation keeps those boundaries.

Order Service does not query User-Chef tables directly. It calls the existing internal endpoint:

```http
GET /internal/v1/customer-addresses/{addressId}?identityId={identityId}
X-Craves-Internal-Secret: <internal service credential>
```

Order Service does not store a cross-service foreign key to the customer-address table. It stores the source UUID for traceability and stores immutable values required for fulfilment.

## 3. Approved product behavior

### 3.1 App opening and browsing

The application may browse using either:

```text
SAVED_ADDRESS
LIVE_GPS
```

Live GPS is temporary browsing context. It is not automatically saved.

### 3.2 Checkout

Checkout accepts only a saved delivery address.

Required request:

```json
{
  "deliveryAddressId": "11111111-2222-3333-4444-555555555555",
  "note": "Please call on arrival"
}
```

When no saved address is supplied:

```json
{
  "error": "DELIVERY_ADDRESS_REQUIRED",
  "message": "Save the current location or select a saved delivery address before placing the order."
}
```

The customer can still change the selected saved address before submitting checkout.

### 3.3 Multi-kitchen checkout

One delivery address is selected for the entire checkout:

```text
Checkout
    ├── Kitchen Order A: same customer drop-off + Kitchen A pickup
    ├── Kitchen Order B: same customer drop-off + Kitchen B pickup
    └── Kitchen Order C: same customer drop-off + Kitchen C pickup
```

This preserves the existing Craves model: one pickup and one delivery per chef-specific order.

## 4. End-to-end flow

```text
Customer submits checkout request
    ↓
Order Service verifies CUSTOMER role
    ↓
Order Service requires deliveryAddressId
    ↓
Order Service reads customer identityId from Craves JWT
    ↓
Order Service calls User-Chef internal address API
    ↓
User-Chef checks shared internal secret
    ↓
User-Chef returns only an active address owned by identityId
    ↓
Order Service validates required address fields and coordinates
    ↓
Order Service validates cart against active Catalog items
    ↓
Order Service groups cart lines by kitchen
    ↓
Order Service fetches each active kitchen profile
    ↓
Order Service validates pickup contact/address/coordinates
    ↓
Parent checkout is inserted with drop-off snapshot
    ↓
Each kitchen order is inserted with the same drop-off snapshot
    ↓
Each kitchen order receives its own pickup snapshot
    ↓
Order items and package metadata are inserted
    ↓
Cart is cleared
    ↓
Transaction commits
    ↓
Existing order-created notification flow runs after commit
```

## 5. Files changed

### 5.1 API contracts

```text
services/order-service/src/main/java/in/craves/order/web/ApiDtos.java
```

Added/changed:

```text
CheckoutRequest
CustomerAddressSnapshotResponse
KitchenPickupSnapshotResponse
CheckoutResponse
OrderResponse
ApiErrorResponse
```

`CheckoutRequest` changed from:

```java
CheckoutRequest(String note)
```

To:

```java
CheckoutRequest(UUID deliveryAddressId, String note)
```

### 5.2 Checkout controller

```text
services/order-service/src/main/java/in/craves/order/web/CheckoutController.java
```

A missing request body is converted to an empty request object so the service returns the structured `DELIVERY_ADDRESS_REQUIRED` error instead of a generic deserialization failure.

### 5.3 Internal customer-address client

```text
services/order-service/src/main/java/in/craves/order/service/CustomerAddressClient.java
```

Responsibilities:

- calls User-Chef internal API;
- sends `X-Craves-Internal-Secret`;
- passes authenticated `identityId` and submitted `addressId`;
- rejects null/incomplete responses;
- verifies returned identity and address identifiers;
- verifies active status;
- maps downstream errors into stable Order API errors;
- never logs or returns the internal credential.

Environment bindings:

```text
CRAVES_USER_CHEF_INTERNAL_BASE_URL
CRAVES_INTERNAL_SERVICE_SECRET
```

### 5.4 Snapshot factory

```text
services/order-service/src/main/java/in/craves/order/service/CheckoutSnapshotFactory.java
```

Customer drop-off validation requires:

```text
source address UUID
active status
recipient name
recipient phone
address line 1
area
city
state
postal code
latitude
longitude
```

Kitchen pickup validation requires:

```text
active kitchen
kitchen UUID
kitchen name/display name
phone number
address line 1
area
city
state
postal code
latitude
longitude
```

Optional fields remain optional:

```text
address line 2
landmark
kitchen email
```

Coordinates are validated against:

```text
latitude  -90..90
longitude -180..180
```

### 5.5 Structured domain errors

```text
services/order-service/src/main/java/in/craves/order/exception/OrderApiException.java
services/order-service/src/main/java/in/craves/order/web/OrderApiExceptionHandler.java
```

These support stable error codes for checkout-address failures without changing existing generic Order Service errors.

### 5.6 Order transaction and mapping

```text
services/order-service/src/main/java/in/craves/order/service/OrderService.java
```

Changes:

- requires saved address before creating checkout;
- calls User-Chef internal API;
- builds one drop-off snapshot;
- validates one pickup snapshot per kitchen;
- writes snapshots on checkout/order creation;
- returns snapshots in checkout/order responses;
- preserves existing package calculations;
- preserves existing status history;
- preserves existing post-commit notification behavior.

### 5.7 Flyway migration

```text
services/order-service/src/main/resources/db/migration/V4__checkout_address_snapshots.sql
```

### 5.8 Tests

```text
services/order-service/src/test/java/in/craves/order/service/CheckoutSnapshotFactoryTest.java
```

Coverage:

- valid active customer address;
- inactive customer address rejection;
- valid active kitchen pickup profile;
- missing pickup coordinates rejection.

### 5.9 Documentation

```text
services/order-service/README.md
docs/handover/2026-07-16-order-checkout-address-snapshots.md
```

### 5.10 Deployment hardening

```text
azure-pipelines-order-service.yml
```

The old pipeline used:

```text
az containerapp update --no-wait
```

That could finish successfully before Flyway and Spring Boot startup completed.

The updated pipeline:

1. deploys the image;
2. polls latest and latest-ready revisions;
3. checks revision health/running state;
4. fails on unhealthy/failed state;
5. prints console logs for startup failure;
6. times out instead of reporting a false success.

## 6. Database design

### 6.1 Parent checkout columns

```text
order_schema.checkout.delivery_address_id
order_schema.checkout.dropoff_recipient_name
order_schema.checkout.dropoff_contact_phone
order_schema.checkout.dropoff_address_line1
order_schema.checkout.dropoff_address_line2
order_schema.checkout.dropoff_landmark
order_schema.checkout.dropoff_area_name
order_schema.checkout.dropoff_city
order_schema.checkout.dropoff_state
order_schema.checkout.dropoff_postal_code
order_schema.checkout.dropoff_latitude
order_schema.checkout.dropoff_longitude
```

### 6.2 Kitchen-specific order columns

Customer drop-off:

```text
order_schema.customer_order.delivery_address_id
order_schema.customer_order.dropoff_recipient_name
order_schema.customer_order.dropoff_contact_phone
order_schema.customer_order.dropoff_address_line1
order_schema.customer_order.dropoff_address_line2
order_schema.customer_order.dropoff_landmark
order_schema.customer_order.dropoff_area_name
order_schema.customer_order.dropoff_city
order_schema.customer_order.dropoff_state
order_schema.customer_order.dropoff_postal_code
order_schema.customer_order.dropoff_latitude
order_schema.customer_order.dropoff_longitude
```

Kitchen pickup:

```text
order_schema.customer_order.pickup_phone_number
order_schema.customer_order.pickup_email
order_schema.customer_order.pickup_address_line1
order_schema.customer_order.pickup_address_line2
order_schema.customer_order.pickup_landmark
order_schema.customer_order.pickup_area_name
order_schema.customer_order.pickup_city
order_schema.customer_order.pickup_state
order_schema.customer_order.pickup_postal_code
order_schema.customer_order.pickup_latitude
order_schema.customer_order.pickup_longitude
```

Existing fields remain snapshot sources for pickup identity/name:

```text
kitchen_id
kitchen_name_snapshot
```

### 6.3 Migration compatibility

All new columns are nullable so pre-existing test orders remain readable.

The migration does not:

- guess old customer addresses;
- guess old kitchen coordinates;
- query another service's schema;
- create a cross-service database foreign key;
- change existing order totals or statuses.

`NOT VALID` check constraints allow historical rows while enforcing completeness for new or updated rows that carry snapshots.

Partial indexes support lookup by source saved-address UUID without indexing null legacy rows.

## 7. API response examples

### 7.1 Checkout response fragment

```json
{
  "id": "checkout-uuid",
  "customerIdentityId": "customer-identity-uuid",
  "deliveryAddressId": "saved-address-uuid",
  "deliveryAddress": {
    "sourceAddressId": "saved-address-uuid",
    "recipientName": "Raviteja",
    "contactPhoneNumber": "+918019166645",
    "addressLine1": "Plot 10, Road 2",
    "addressLine2": "Fourth floor",
    "landmark": "Near Metro",
    "areaName": "Madhapur",
    "city": "Hyderabad",
    "state": "Telangana",
    "postalCode": "500081",
    "latitude": 17.4483,
    "longitude": 78.3915
  }
}
```

### 7.2 Kitchen-specific order response fragment

```json
{
  "id": "order-uuid",
  "kitchenId": "kitchen-uuid",
  "deliveryAddress": {
    "sourceAddressId": "saved-address-uuid"
  },
  "pickupAddress": {
    "kitchenId": "kitchen-uuid",
    "kitchenName": "Lakshmi's Kitchen",
    "contactPhoneNumber": "+919876543210",
    "addressLine1": "House 21, Street 5",
    "areaName": "Kondapur",
    "city": "Hyderabad",
    "state": "Telangana",
    "postalCode": "500084",
    "latitude": 17.4698,
    "longitude": 78.3651
  }
}
```

## 8. Error contract

### 8.1 Missing address

HTTP 400:

```json
{
  "error": "DELIVERY_ADDRESS_REQUIRED",
  "message": "Save the current location or select a saved delivery address before placing the order."
}
```

### 8.2 Address missing/inactive/not owned

HTTP 404:

```json
{
  "error": "DELIVERY_ADDRESS_NOT_AVAILABLE",
  "message": "The selected delivery address is inactive or does not belong to the customer."
}
```

### 8.3 Incomplete customer address

HTTP 409:

```json
{
  "error": "DELIVERY_ADDRESS_INCOMPLETE",
  "message": "The selected delivery address is incomplete. Update it before placing the order."
}
```

### 8.4 Incomplete kitchen pickup profile

HTTP 409:

```json
{
  "error": "KITCHEN_PICKUP_ADDRESS_INCOMPLETE",
  "message": "The kitchen pickup profile is incomplete and cannot be used for checkout."
}
```

### 8.5 Internal service unavailable

HTTP 503 with one of:

```text
DELIVERY_ADDRESS_LOOKUP_UNAVAILABLE
DELIVERY_ADDRESS_LOOKUP_UNAUTHORIZED
DELIVERY_ADDRESS_LOOKUP_INVALID_RESPONSE
```

The client should not silently proceed when address verification is unavailable.

## 9. Security model

### 9.1 Customer authentication

The public checkout endpoint still requires a valid Craves access token with `CUSTOMER` role.

The customer identity used for ownership verification comes from the verified JWT principal. It is not accepted from the request body.

### 9.2 Internal authentication

Order Service sends:

```text
X-Craves-Internal-Secret
```

The value must match User-Chef Service's configured internal service secret.

The value must not appear in:

- Git;
- README examples;
- API request bodies;
- mobile/web applications;
- APIM public operations;
- logs;
- chat messages.

### 9.3 Privacy

The order API returns address information only through authenticated order/checkout endpoints with existing customer/chef access checks.

The implementation does not add a new public API for saved customer addresses.

## 10. Environment configuration

Order Container App requires:

```text
CRAVES_USER_CHEF_INTERNAL_BASE_URL
CRAVES_INTERNAL_SERVICE_SECRET
```

Example base URL shape:

```text
https://<user-chef-container-app-fqdn>
```

Do not append:

```text
/api/v1
/internal/v1/customer-addresses
```

The Java client appends the internal endpoint path itself.

## 11. Manual Azure steps

No new Azure resource is required.

Before deployment testing:

1. Resolve the existing User-Chef Container App FQDN.
2. Inspect the Order Container App environment-variable names.
3. Add/update the User-Chef internal base URL.
4. Reference the existing shared internal secret from the Order Container App.
5. Do not expose or echo the secret value.
6. Run the Order Service pipeline.
7. Confirm newest revision is healthy.
8. Confirm Flyway V4 appears in startup logs or schema history.

Recommended FQDN lookup:

```bash
az containerapp show \
  --name ca-craves-user-chef-service-prod \
  --resource-group rg-craves-prodlow-centralindia \
  --query properties.configuration.ingress.fqdn \
  -o tsv
```

Environment inspection without secret values:

```bash
az containerapp show \
  --name ca-craves-order-service-prodlow \
  --resource-group rg-craves-prodlow-centralindia \
  --query "properties.template.containers[0].env[].{name:name,secretRef:secretRef}" \
  -o table
```

The exact secure secret-setting command should be executed only from a secure administrator shell where the existing value is available. Do not paste the secret into chat.

## 12. Deployment order

For this module:

```text
1. Confirm User-Chef Service is healthy and its internal address endpoint is deployed.
2. Configure Order Service internal URL/secret if missing.
3. Run azure-pipelines-order-service.yml from main after merge.
4. Confirm Flyway V4 succeeds.
5. Confirm the latest Order revision is healthy.
6. Test checkout through APIM with a fresh Craves token and real saved address UUID.
```

Catalog does not require another deployment for this module because the existing active kitchen response already contains the pickup fields consumed by Order Service.

## 13. Local verification

```bash
cd services/order-service
mvn -B clean test
```

Expected test class:

```text
CheckoutSnapshotFactoryTest
```

Local service order:

```text
PostgreSQL
User-Chef Service
Catalog Service
Order Service
```

Suggested local environment:

```text
CRAVES_USER_CHEF_INTERNAL_BASE_URL=http://localhost:8081
CRAVES_INTERNAL_SERVICE_SECRET=<local-only matching value>
```

## 14. Deployed smoke-test plan

### 14.1 Obtain token

Use the existing Firebase test page:

```text
Phone OTP
    ↓
Firebase ID token
    ↓
Auth exchange
    ↓
Craves access token
```

Do not paste the token into chat.

### 14.2 Obtain saved address ID

Call the authenticated User-Chef address-list endpoint through APIM and select an active address UUID owned by the test customer.

### 14.3 Missing-address test

```bash
curl -sS -i \
  "$APIM_URL/api/v1/checkout" \
  -H "Authorization: Bearer $CRAVES_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}'
```

Expected:

```text
HTTP 400
DELIVERY_ADDRESS_REQUIRED
```

### 14.4 Valid-address test

```bash
curl -sS -i \
  "$APIM_URL/api/v1/checkout" \
  -H "Authorization: Bearer $CRAVES_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"deliveryAddressId\":\"$DELIVERY_ADDRESS_ID\",\"note\":\"Checkout address snapshot test\"}"
```

Expected:

```text
HTTP 200
checkout.deliveryAddressId is populated
checkout.deliveryAddress is populated
every order.deliveryAddress is populated
every order.pickupAddress is populated
```

This test requires a non-empty valid cart and active menu items with package metadata.

## 15. Risks and operational notes

### 15.1 Source API availability

Checkout now depends on User-Chef Service for ownership verification. This is intentional fail-closed behavior: Craves must not create an order with an unverified delivery address.

Future resilience options may include a narrowly scoped retry/circuit-breaker policy, but checkout must still not proceed with stale or unverified ownership.

### 15.2 Existing Catalog public response

Order Service currently consumes the existing active Catalog kitchen response, which includes pickup contact and address data. This module does not add a new public endpoint or broaden Catalog exposure.

A future privacy hardening review may split public kitchen summary data from internal fulfilment details. That is separate from this Order module.

### 15.3 Legacy rows

Orders created before V4 have null snapshots. They must not be selected for new delivery automation without manual operational review.

### 15.4 Transaction duration

Existing checkout already calls Catalog Service while inside a transactional service method. This module adds one User-Chef lookup. For early load this is acceptable, but high-scale hardening should separate external validation from the shortest possible database transaction while retaining consistency and idempotency.

### 15.5 Pipeline timeout

The pipeline waits approximately six minutes for a healthy revision. A legitimate startup taking longer will fail the pipeline and require log review rather than producing a false green deployment.

## 16. Not included

The following remain intentionally pending:

- delivery serviceability decision;
- delivery provider selection;
- delivery fee calculation;
- Cashfree payment intent creation changes;
- transactional `CHEF_ACCEPTED_ORDER` domain outbox;
- managed-identity Service Bus publisher;
- Integration Service command consumption for this new event;
- automatic delivery booking;
- mobile/web checkout screen changes;
- test-dashboard address payload correction;
- pricing, commissions, GST, FSSAI, or legal logic.

## 17. Next recommended module

After deployment and checkout snapshot verification:

```text
Chef accepts order
    ↓
Order status, ready_at, and CHEF_ACCEPTED_ORDER outbox record commit atomically
    ↓
Managed-identity dispatcher publishes to craves-domain-events
    ↓
Integration Service consumes idempotently
    ↓
Delivery Intelligence selects provider
    ↓
Delivery command is scheduled near ready_at
```

Keep during initial test:

```text
CRAVES_DELIVERY_COMMAND_ENABLED=false
CRAVES_DELIVERY_INTELLIGENCE_ENABLED=true
BORZO_API_ENABLED=false
```

Only enable automatic provider commands during a controlled end-to-end sandbox test window.

## 18. Completion checklist

Code:

- [x] Checkout requires `deliveryAddressId`.
- [x] User-Chef internal address client added.
- [x] Ownership and active status checked.
- [x] Customer drop-off validation added.
- [x] Kitchen pickup validation added.
- [x] Parent checkout snapshot persistence added.
- [x] Per-kitchen order snapshot persistence added.
- [x] Response DTOs expose snapshots.
- [x] Flyway V4 added.
- [x] Unit tests added.
- [x] README updated.
- [x] Order pipeline health gate added.

Manual/deployment:

- [ ] Confirm Order internal base URL.
- [ ] Confirm Order internal secret reference.
- [ ] Merge implementation PR.
- [ ] Run Order Service pipeline.
- [ ] Confirm Flyway V4.
- [ ] Confirm healthy revision.
- [ ] Test missing address.
- [ ] Test valid saved address.
- [ ] Verify snapshots in response.

## 19. Confidentiality

This document deliberately contains no secret values, tokens, passwords, private keys, database passwords, or provider credentials.
