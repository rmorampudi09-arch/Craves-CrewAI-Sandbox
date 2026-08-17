# Customer Web Checkout

Adds saved-address selection, backend cart validation, checkout creation and checkout detail views.

## Routes

- `/checkout`
- `/checkout/{checkoutId}`
- `POST /api/checkout`
- `GET /api/checkout/{checkoutId}`

## Business boundary

The browser sends only a saved `deliveryAddressId` and optional note. Order Service validates cart availability, snapshots the selected address and returns food subtotal, platform fee, tax, delivery fee and grand total. The browser displays those values without recalculation.

## Security

- HTTP-only session cookie.
- Same-origin checkout mutation.
- UUID and note-length validation.
- Customer and pickup-private fields removed.
- No-store responses.

## Pipelines

- `azure-pipelines-customer-web-next-checkout-ci.yml`
- `azure-pipelines-customer-checkout-apim.yml`

APIM remains disabled unless `confirmConfigureCustomerCheckout=true`.

## Manual test later

Use a test customer with one valid cart and saved address. Create one checkout, verify the exact backend totals, then continue to the payment module. Do not create repeated paid checkouts during smoke testing.
