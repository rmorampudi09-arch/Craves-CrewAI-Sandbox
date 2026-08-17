# Craves Order Service

Order Service owns customer carts, checkout, chef-specific orders, order items, immutable checkout snapshots, order status transitions, delivery-package metadata, and admin-managed charge policies.

The service follows the approved Craves HLD boundary: User-Chef Service remains the source of truth for saved customer addresses, Catalog Service remains the source of truth for active kitchen profiles and menu items, and Order Service stores immutable snapshots needed to fulfil an order even when those source records change later.

## Current V1 scope

- Customer cart CRUD.
- Cart validation against active Catalog menu-item APIs.
- Checkout that groups cart items by kitchen and creates one chef-specific order per kitchen.
- Mandatory saved customer delivery address at checkout.
- Ownership and active-status validation through the User-Chef internal API.
- Immutable customer drop-off snapshot on the parent checkout and every kitchen-specific order.
- Immutable kitchen pickup snapshot on every kitchen-specific order.
- Package metadata copied from Catalog Service at checkout.
- Dynamic chef-specific package-weight calculation.
- Thermobox requirement aggregation per chef-specific order.
- Chef acceptance calculates and persists `ready_at` from the submitted preparation time.
- V1 zero-fee charge policy seeded by default.
- Admin-managed charge policies.
- Chef order listing, access, acceptance, rejection, and ready-for-pickup transitions.

## Checkout address contract

Checkout accepts only a saved address identifier:

```http
POST /api/v1/checkout
Authorization: Bearer <Craves access token>
Content-Type: application/json
```

```json
{
  "deliveryAddressId": "11111111-2222-3333-4444-555555555555",
  "note": "Please call on arrival"
}
```

`deliveryAddressId` must identify an active address owned by the authenticated customer.

A temporary live GPS coordinate is valid for browsing and discovery, but it cannot be used directly to place an order. The customer must save the current location or select an existing saved address first.

When the field is missing, checkout returns:

```json
{
  "error": "DELIVERY_ADDRESS_REQUIRED",
  "message": "Save the current location or select a saved delivery address before placing the order."
}
```

Other address-specific errors include:

```text
DELIVERY_ADDRESS_NOT_AVAILABLE
DELIVERY_ADDRESS_INCOMPLETE
DELIVERY_ADDRESS_LOOKUP_UNAVAILABLE
KITCHEN_PICKUP_ADDRESS_INCOMPLETE
```

No serviceability radius is evaluated by this module. The radius used for nearby browsing is not treated as an order-delivery rule.

## Address verification flow

```text
Customer submits deliveryAddressId
    ↓
Order Service reads authenticated customer identityId from the JWT
    ↓
Order Service calls User-Chef internal address endpoint
    ↓
User-Chef verifies identity ownership and active status
    ↓
Order Service validates required delivery fields and coordinates
    ↓
Order Service refreshes active menu and kitchen data from Catalog
    ↓
One parent checkout and one order per kitchen are written transactionally
```

Internal User-Chef request:

```http
GET /internal/v1/customer-addresses/{addressId}?identityId={identityId}
X-Craves-Internal-Secret: <shared internal secret>
```

The internal secret is service-to-service only. It must never be sent by the mobile app, web app, or API test dashboard.

## Immutable address snapshots

The saved-address UUID is retained for traceability, but delivery execution must use the snapshot stored with the order.

Customer drop-off snapshot fields:

```text
delivery_address_id
dropoff_recipient_name
dropoff_contact_phone
dropoff_address_line1
dropoff_address_line2
dropoff_landmark
dropoff_area_name
dropoff_city
dropoff_state
dropoff_postal_code
dropoff_latitude
dropoff_longitude
```

Kitchen pickup snapshot fields:

```text
kitchen_id
kitchen_name_snapshot
pickup_phone_number
pickup_email
pickup_address_line1
pickup_address_line2
pickup_landmark
pickup_area_name
pickup_city
pickup_state
pickup_postal_code
pickup_latitude
pickup_longitude
```

For a multi-kitchen checkout:

```text
One customer-selected delivery address
    ├── copied to Kitchen Order A
    ├── copied to Kitchen Order B
    └── copied to Kitchen Order C

Each kitchen order also stores its own kitchen pickup snapshot.
```

Changing or deleting the original saved customer address later does not change an existing order. Changing the kitchen profile later also does not change an existing order's pickup details.

Legacy orders created before Flyway V4 retain null address snapshots. The migration does not invent or backfill historical address data.

## Dynamic package calculation

Catalog Service supplies the packaged weight and thermobox decision for each menu item. Order Service snapshots those values so later menu edits cannot change an existing order.

```text
order_item.unit_package_weight_grams_snapshot
order_item.thermobox_required_snapshot
```

For each chef-specific order:

```text
total_package_weight_grams =
    sum(unit_package_weight_grams_snapshot x quantity)

thermobox_required =
    true when any order item requires a thermobox
```

Weights remain in grams inside Craves. Each external delivery-provider adapter converts grams into that provider's required unit.

## Chef acceptance and ready time

The acceptance request must include a positive preparation time:

```json
{
  "prepTimeMinutes": 35,
  "note": "Order confirmed"
}
```

Order Service persists:

```text
ready_at = database current time + prepTimeMinutes
```

Integration Service will schedule delivery close to `ready_at`; delivery must not be created immediately after payment.

## V1 charge model

Default seeded policy:

```text
food_subtotal = sum(item price x quantity)
platform_fee = 0
tax_amount = 0
delivery_fee = 0
grand_total = food_subtotal
```

Checkout always reads the currently active row from `order_schema.charge_policy`. Final finance, tax, commission, and legal rules remain Product/Finance/Legal decisions.

Admin endpoints:

```http
GET  /api/v1/admin/charge-policy/current
POST /api/v1/admin/charge-policy
```

## Main endpoints

### Customer cart

```http
GET    /api/v1/cart
POST   /api/v1/cart/items
PUT    /api/v1/cart/items/{cartItemId}
DELETE /api/v1/cart/items/{cartItemId}
DELETE /api/v1/cart
POST   /api/v1/cart/validate
```

### Checkout and orders

```http
POST /api/v1/checkout
GET  /api/v1/checkout/{checkoutId}
GET  /api/v1/orders
GET  /api/v1/orders/{orderId}
```

### Chef orders

```http
GET  /api/v1/chef/orders
GET  /api/v1/chef/orders/{orderId}
POST /api/v1/chef/orders/{orderId}/accept
POST /api/v1/chef/orders/{orderId}/reject
POST /api/v1/chef/orders/{orderId}/ready-for-pickup
```

## Database migration

This module adds:

```text
src/main/resources/db/migration/V4__checkout_address_snapshots.sql
```

The migration:

- adds nullable snapshot columns so historical rows remain readable;
- adds `NOT VALID` completeness and coordinate constraints for new/updated data;
- adds partial indexes on `delivery_address_id`;
- does not create a cross-service database foreign key;
- does not backfill guessed address values.

## Environment variables

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
CRAVES_JWT_VERIFICATION_PEM_BASE64
CRAVES_JWT_ISSUER
CRAVES_JWT_AUDIENCE
CRAVES_CATALOG_BASE_URL
CRAVES_USER_CHEF_INTERNAL_BASE_URL
CRAVES_INTERNAL_SERVICE_SECRET
CRAVES_NOTIFICATION_INTERNAL_BASE_URL
CRAVES_NOTIFICATION_INTERNAL_KEY
CRAVES_NOTIFICATION_DIRECT_DISPATCH_ENABLED
CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED
```

Local example:

```text
CRAVES_USER_CHEF_INTERNAL_BASE_URL=http://localhost:8081
```

Azure uses the existing User-Chef Container App FQDN. The value must point to the service root, not `/api/v1` and not the public APIM address.

`CRAVES_INTERNAL_SERVICE_SECRET` must match the value configured on User-Chef Service. Store it in Azure Container Apps secrets or Azure Key Vault-backed deployment configuration; never commit or paste the value into documentation or chat.

## Local run

Start User-Chef Service first, then Order Service:

```bash
cd services/user-chef-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

```bash
cd services/order-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Run Order tests:

```bash
cd services/order-service
mvn -B clean test
```

## Deployment

Use the existing Azure DevOps pipeline:

```text
azure-pipelines-order-service.yml
```

Before testing checkout, confirm the Order Container App has both internal-address settings and that the new revision is healthy. The pipeline currently uses `az containerapp update --no-wait`, so pipeline completion can occur before the revision finishes starting.

## Manual steps required

- Confirm `CRAVES_USER_CHEF_INTERNAL_BASE_URL` on the Order Container App.
- Confirm the existing shared internal service secret is exposed to Order Service as `CRAVES_INTERNAL_SERVICE_SECRET`.
- Do not paste the secret into chat or source control.
- Run the Order Service pipeline after merge.
- Confirm Flyway V4 succeeded and the newest revision is healthy.
- Use a real active saved-address UUID belonging to the Firebase test customer for the checkout test.

No new Azure resource or paid SKU is required.

## Important production notes

- Cashfree payment intent creation remains outside this checkout snapshot module.
- Delivery creation remains outside Order Service.
- The transactional `CHEF_ACCEPTED_ORDER` domain outbox and managed-identity Service Bus publisher are the next Order/Integration step.
- Browsing radius, delivery serviceability, delivery pricing, commissions, GST, and compliance rules are intentionally not defined here.
