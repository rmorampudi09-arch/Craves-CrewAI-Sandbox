# Order Checkout Snapshot Privacy Amendment

Date: 2026-07-16  
Applies to: `docs/handover/2026-07-16-order-checkout-address-snapshots.md`

## Purpose

This amendment records a final security review decision made before merging the Order checkout address-snapshot module.

The original handover describes the kitchen pickup snapshot as part of an `OrderResponse` example and includes a smoke-test expectation for `order.pickupAddress`. That representation is superseded by this amendment.

## Final behavior

Order Service still stores the complete immutable kitchen pickup snapshot in:

```text
order_schema.customer_order
```

Stored fields include:

```text
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

However, `pickupAddress` is marked with Jackson `@JsonIgnore` and is not serialized in public customer or chef order JSON responses.

## Reason

Craves kitchens may be operated from chefs' homes. Exposing a chef's complete home address, personal pickup phone, and email through customer-facing order APIs is not necessary for customer order tracking and creates avoidable privacy risk.

The information is retained internally because it is required later for:

- delivery quote generation;
- delivery command creation;
- provider pickup instructions;
- operational support and audit;
- immutable fulfilment history.

## Public response

Customer drop-off remains serialized as `deliveryAddress` because:

- it is the customer's own selected saved address;
- the customer must be able to verify the delivery destination;
- the assigned chef may need the destination through an authorized chef-order flow.

Kitchen pickup details are not serialized.

Expected customer/order JSON therefore includes:

```json
{
  "deliveryAddress": {
    "sourceAddressId": "saved-address-uuid",
    "recipientName": "Customer",
    "areaName": "Madhapur",
    "city": "Hyderabad"
  }
}
```

It must not include:

```json
{
  "pickupAddress": {
    "contactPhoneNumber": "...",
    "email": "...",
    "addressLine1": "..."
  }
}
```

## Verification test

Added:

```text
services/order-service/src/test/java/in/craves/order/web/OrderResponsePrivacyTest.java
```

The test serializes an `OrderResponse` containing a fully populated internal pickup snapshot and verifies that JSON does not contain:

```text
pickupAddress
private kitchen address
private kitchen email
private kitchen phone
```

## Runtime smoke-test correction

The valid checkout smoke test should verify:

```text
checkout.deliveryAddressId is populated
checkout.deliveryAddress is populated
every order.deliveryAddress is populated
```

It should not expect `order.pickupAddress` in the HTTP response.

Pickup persistence should be verified through controlled database inspection, future internal fulfilment APIs, or the future `CHEF_ACCEPTED_ORDER` outbox payload—not through the public customer response.

## Future integration rule

When the delivery outbox module is implemented, the publisher may read the internal pickup snapshot and place only the required fields into the protected service-to-service domain event.

That event must not be exposed through customer-facing APIM operations.

## Precedence

Where this amendment conflicts with the earlier handover's response examples or smoke-test expectation, this amendment is authoritative.
