# Craves Customer Orders and Delivery Tracking UI/UX

Date: 2026-08-05  
Branch: `feat/customer-orders-tracking-uiux`  
Design source: `CRV-UIUX-BUILD-001 v1.0`

## Scope completed

### Order history

- Rebuilt the customer order-history page using the Craves red, cream and espresso design system.
- Validates the `/api/orders` response using the strict customer-order contract before rendering.
- Sorts orders by backend creation time.
- Separates active, past and all views using the supported Order Service status enumeration.
- Shows the backend kitchen name, item count, grand total, status and timestamp.
- Treats each kitchen-specific order as independently trackable even when it came from the same checkout.
- Added complete loading, backend-error, no-orders and filtered-empty states.
- Added retry and manual refresh with a visible last-updated timestamp.

### Order and delivery tracking

- Validates the order ID from the route before any request.
- Loads and validates both the authoritative Order Service record and the delivery-status projection.
- Rejects mismatched order IDs across the route, order response and delivery response.
- Shows the current order status even when no delivery job exists yet.
- Builds the delivery timeline only from backend history rows.
- Uses the existing provider-neutral delivery presentation map for all supported states.
- Auto-refreshes active delivery states every 30 seconds only while the page is visible.
- Stops auto-refreshing terminal delivery states.
- Exposes a delivery-provider tracking link only after the contract has accepted an HTTPS URL.
- Added complete loading, retry, unavailable, no-delivery-job and populated states.

## Main code paths

```text
apps/customer-web-next/src/screens/OrderHistory/OrderHistory.tsx
apps/customer-web-next/src/screens/OrderTracking/OrderTracking.tsx
apps/customer-web-next/src/components/tracking/TrackingHeader.tsx
apps/customer-web-next/src/app/tracking/page.tsx
apps/customer-web-next/src/lib/customer-orders-tracking-integration.test.ts
```

## Manual steps required

No source-code secret or Azure resource change is required.

Runtime acceptance requires APIM routes for:

```text
GET /api/v1/orders
GET /api/v1/orders/{orderId}
GET /api/v1/orders/{orderId}/delivery-status
```

The delivery-provider integration must populate the existing provider-neutral delivery projection. If no delivery job exists, the customer UI deliberately shows the Order Service status rather than fabricated courier information.

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

1. No-orders, active-orders and past-orders views.
2. A checkout that produced more than one kitchen-specific order.
3. Direct tracking refresh with a valid order UUID.
4. Invalid and unauthorized order IDs.
5. Order with no delivery job.
6. Active delivery with backend history.
7. Delayed, cancelled, returned and failed delivery states.
8. HTTPS provider tracking link.
9. Polling pauses when the browser tab is hidden.
10. Polling stops after a terminal state.
