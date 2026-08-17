# Craves Customer Cart, Checkout and Cashfree Payment UI/UX

Date: 2026-08-05  
Branch: `feat/customer-cart-checkout-payment-uiux`  
Design source: `CRV-UIUX-BUILD-001 v1.0`

## Scope completed

This module completes the customer cart, delivery-address checkout and Cashfree hosted-payment journey using backend-owned cart and pricing data.

### Cart

- Removed the development/local cart fallback. The Cart Service is now the only mutation and total source.
- Cart API failures clear stale browser state and surface a retryable error instead of showing fabricated success.
- Added complete loading, backend-error, empty and populated states.
- Added per-item busy states, bounded quantity controls and removal feedback.
- Missing menu images use the Craves brand asset with an explicit image-unavailable indicator.
- Food subtotal and currency come from the validated backend cart.
- Cart validation runs before the customer can proceed to address selection.

### Delivery-address checkout

- Loads and validates the saved-address response through the existing customer-address contract.
- Displays only active saved addresses and selects the default address when available.
- Added accessible radio-card selection, address-empty state and address-management actions.
- The kitchen note is optional, limited to 500 characters and warns against sensitive data.
- Checkout creation sends only the saved delivery-address ID and optional note.
- The returned checkout is validated before navigation to payment.
- Platform fee, tax, delivery fee and grand total are never calculated in the browser.

### Cashfree payment

- Loads and validates the authoritative checkout before rendering payment actions.
- Creates payment orders only through the Craves backend.
- Loads Cashfree JavaScript SDK v3 from its HTTPS host and opens hosted checkout.
- Payment session, status and verification responses are parsed through strict allow-listed contracts.
- Payment success is shown only after backend verification returns `PAID`.
- Added retry, verify, refresh, paid, failed/cancelled and unavailable states.
- Craves does not collect card number, CVV, banking credentials or UPI PIN.

## Main code paths

```text
apps/customer-web-next/src/services/api/cravesCart.ts
apps/customer-web-next/src/screens/Cart/Cart.tsx
apps/customer-web-next/src/components/cart/CartHeader.tsx
apps/customer-web-next/src/components/cart/CartItemList.tsx
apps/customer-web-next/src/components/cart/CartItemRow.tsx
apps/customer-web-next/src/components/cart/BillSummaryCard.tsx
apps/customer-web-next/src/components/cart/CartCheckoutBar.tsx
apps/customer-web-next/src/components/cart/EmptyCartState.tsx
apps/customer-web-next/src/screens/Checkout/Checkout.tsx
apps/customer-web-next/src/components/checkout/CheckoutHeader.tsx
apps/customer-web-next/src/components/checkout/CashfreePayment.tsx
apps/customer-web-next/src/lib/cart-checkout-integration.test.ts
```

## Manual steps required

### Cashfree

The merchant account, KYC, API credentials and webhook registration remain operator-owned. Do not place secrets in frontend environment variables.

The deployment must provide the non-secret frontend setting:

```text
NEXT_PUBLIC_CASHFREE_MODE=sandbox
```

Use `production` only after Cashfree production credentials, webhook URLs and domain readiness have been verified.

### APIM/runtime verification

Confirm the deployed web application can reach the configured APIM routes for:

```text
GET    /api/v1/cart
POST   /api/v1/cart/items
PUT    /api/v1/cart/items/{cartItemId}
DELETE /api/v1/cart/items/{cartItemId}
POST   /api/v1/cart/validate
GET    /api/v1/customer/addresses
POST   /api/v1/checkout
GET    /api/v1/checkout/{checkoutId}
POST   /api/v1/payments/orders
GET    /api/v1/payments/orders/{paymentOrderId}
POST   /api/v1/payments/orders/{paymentOrderId}/verify
```

No Azure resources, secrets, DNS records or billable infrastructure are provisioned by this module.

## Verification

Run from `apps/customer-web-next`:

```bash
npm ci --ignore-scripts --no-audit --no-fund
npm run lint
npm run typecheck
npm run test
npm run build
```

Manual acceptance:

1. Backend cart loading, empty and failure states.
2. Add, increase, decrease and remove a live catalog item.
3. Invalid/unavailable item validation blocks checkout.
4. Default and non-default address selection.
5. No-address flow links to address management.
6. Checkout creation displays only backend totals.
7. Cashfree sandbox modal opens with the backend payment session ID.
8. Closing the modal without payment does not show success.
9. Backend verification controls the paid state.
10. Refreshing a paid checkout remains paid.
