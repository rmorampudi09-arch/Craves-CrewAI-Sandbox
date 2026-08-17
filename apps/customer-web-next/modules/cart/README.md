# Customer Web Cart

Adds authenticated cart list, add, update, remove, clear and validation flows to the Next.js customer application.

## Routes

- `/cart`
- `GET|DELETE /api/cart`
- `POST /api/cart/items`
- `PUT|DELETE /api/cart/items/{cartItemId}`
- `POST /api/cart/validate`

## Backend ownership

Order Service owns item snapshots, quantity validation, item availability, currency and food subtotal. The browser never recomputes prices.

## Security

- HTTP-only Craves session cookie.
- Same-origin checks on all mutations.
- UUID and quantity validation.
- Customer identity IDs and provider payloads are removed.
- All responses are no-store.

## APIM

`scripts/apim/configure-customer-cart-apim.sh` configures the existing or dedicated `api/v1/cart` API. It fails on multiple path owners, inherited `backend-id` or a subscription-key policy that would need relaxation.

## Pipelines

- `azure-pipelines-customer-web-next-cart-ci.yml`
- `azure-pipelines-customer-cart-apim.yml`

APIM configuration remains disabled unless `confirmConfigureCustomerCart=true`.

## Manual steps later

Run CI, merge parents in order, configure APIM, add one known active menu item, update quantity, validate, remove it and confirm exact cleanup.
