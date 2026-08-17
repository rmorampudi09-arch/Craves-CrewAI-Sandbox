# Customer Order History and Details

## Routes

```text
/orders
/orders/{orderId}
```

## Flow

```text
HTTP-only customer session
  -> Next.js order BFF
  -> APIM customer order read operation
  -> Order Service ownership check
  -> response allow-list
  -> customer order UI
```

## Public data

The UI exposes order ID, kitchen name, commercial order status, amounts, customer delivery-address snapshot, customer item list, timestamps and chef response note.

The BFF deliberately excludes:

- `customerIdentityId`;
- kitchen pickup address and chef-private contact data;
- internal outbox/inbox identifiers;
- provider payloads and retry data;
- database metadata not present in the public contract.

## Files

```text
src/lib/order-contract.ts
src/lib/order-contract.test.ts
src/lib/server-api.ts
src/app/api/orders/route.ts
src/app/api/orders/[orderId]/route.ts
src/components/customer-orders.tsx
src/app/orders/page.tsx
src/app/orders/[orderId]/page.tsx
```

## APIM

```text
scripts/apim/configure-order-customer-read-apim.sh
infra/apim/order-customer-read/order-customer-read-policy.xml
azure-pipelines-order-customer-read-apim.yml
```

The APIM script requires exactly one API at `api/v1/orders`; it expects the delivery-status APIM module to have established that path. It adds only:

```text
GET /
GET /{orderId}
```

It does not change API-wide subscription-key settings or provider/delivery flags.

## CI

```text
azure-pipelines-customer-web-next-orders-ci.yml
```

## Manual later

1. Run parent PR pipelines and merge in order.
2. Run order-read APIM rollout after the Order API path exists.
3. Use one real customer session to verify order list, owned detail, unowned 404 and logout 401.
4. Do not paste access tokens into pipeline parameters.

## No business-rule changes

This module does not define pricing, commission, delivery radius, GST, refund entitlement, chef payout or provider selection.
