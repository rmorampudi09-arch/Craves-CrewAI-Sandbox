# Craves Complete Chef Workspace UI/UX

Date: 2026-08-05  
Branch: `feat/chef-complete-uiux`  
Design source: `CRV-UIUX-BUILD-001 v1.0`  
Architecture and business rules: existing Craves backend contracts

## Purpose

This module completes the code-owned chef web workspace after the shared customer/chef design foundation, customer discovery, cart/checkout/payment and customer order tracking modules. It covers onboarding, kitchen configuration, menu/media, chef-owned orders and workflow actions, finance ledger visibility, operational availability and supported proof/compliance status.

No pricing policy, commission rate, payout date, delivery-radius rule, weekly opening-hour model or FSSAI eligibility rule is invented in this implementation.

## Completed capabilities

### Shared chef workspace shell

- Added one responsive Craves-branded chef header, navigation and footer.
- Added active navigation for overview, application, kitchen, menu, orders, earnings and operations.
- Added customer-mode switching without creating a second identity session.
- Replaced old page-level purple/gold headings with the canonical red, cream, espresso and gold design system.
- Added reusable page headers and consistent responsive content widths.

### Chef onboarding and proof evidence

- Existing backend-backed application submission, pending update and rejected resubmission flows remain intact.
- Existing customer profile and saved address data can prefill the application where the application contract supports it.
- Supported proof-document types remain exactly:
  - `AADHAAR_CARD`
  - `PAN_CARD`
- Document metadata/status comes from the chef-application backend.
- Approved applications remain locked from applicant edits.
- Admin approval remains the only source of the `CHEF` role.

### Kitchen profile and operational state

- Existing backend-backed kitchen create/update form remains intact under the new workspace shell.
- Kitchen ownership is enforced by Catalog Service.
- Kitchen status is treated as the current backend-supported operational switch:
  - `DRAFT`
  - `ACTIVE`
  - `INACTIVE`
  - `SUSPENDED`
- Valid latitude/longitude remain required for geographic discovery.
- No delivery radius is calculated or configured in the browser.

### Menu, media and item availability

- Existing Catalog-backed dish creation and editing remain intact.
- Existing per-item availability and image-upload operations remain intact.
- Public image URLs are displayed; storage keys and credentials remain server-side.
- Operations readiness counts ACTIVE items, currently available items and active items with public images.
- Active dishes without a public image show the standard Craves placeholder in the customer experience.

### Chef order inbox and order detail

- Rebuilt the chef order inbox with:
  - loading skeletons
  - retryable backend errors
  - empty state
  - action-required view
  - in-progress view
  - completed view
  - all-orders view
  - manual refresh and timestamp
- Rebuilt order detail with backend totals, item snapshots, recipient fulfilment address and privacy guidance.
- Chef workflow actions remain limited to the Order Service transitions:
  - accept with preparation time and optional note
  - reject with reason
  - mark complete order ready for pickup
- Accept and reject requests continue to use correlation and idempotency keys.
- Customer charges are explicitly separated from chef earnings.

### `INVALID_CHEF_ORDERS_RESPONSE` compatibility fix

The strict public chef-order record allow-list remains in place. The BFF now accepts the known deployed transport envelopes without accepting invalid order records:

```json
[ { "id": "..." } ]
```

```json
{ "content": [ { "id": "..." } ] }
```

```json
{ "orders": [ { "id": "..." } ] }
```

```json
{ "data": [ { "id": "..." } ] }
```

```json
{ "data": { "orders": [ { "id": "..." } ] } }
```

Order detail/action responses accept direct records or named `order` / `data` envelopes. Every order, item, UUID, amount, status and timestamp is still validated. Unknown fields including customer identity ID, checkout ID, kitchen ID and pickup-address snapshot are not returned to the browser.

Legacy rows without `updatedAt` use `createdAt` only as a display/sorting fallback; no workflow event is fabricated.

### Chef earnings ledger

- Added an authenticated BFF for the existing Integration Service endpoint:

```text
GET /api/v1/chef/earnings?limit=200
```

- Added strict ledger parsing for:
  - `DRAFT`
  - `APPROVED`
  - `SETTLEMENT_PENDING`
  - `SETTLED`
  - `REVERSED`
- Validates gross, commission, tax withholding, adjustment and net-payable arithmetic.
- Removes chef identity IDs from the browser model.
- Added loading, unavailable, empty, filtered-empty and populated ledger states.
- Added summaries for approved net payable, settlement pending and recorded settled values.
- The browser does not calculate commission/tax or initiate payouts.
- Finance/admin remains responsible for creating, approving and reversing allocations.
- A settlement/payout provider is not invented or provisioned.

### Operations, availability and compliance readiness

Added a combined live operations workspace that loads:

```text
GET /api/chef/application
GET /api/chef/kitchen
GET /api/chef/menu
```

The customer-discovery readiness state is derived only from:

1. chef application is `APPROVED`
2. kitchen status is `ACTIVE`
3. kitchen has valid coordinates
4. at least one menu item is `ACTIVE` and available

The page also shows supported proof status and active-item image coverage.

#### Schedule boundary

The reviewed backend currently supports kitchen status and per-item availability. It does not expose a reviewed weekly opening-hours contract. Therefore this implementation does not store or display fabricated weekly schedules. Operational opening/closing is represented using the real kitchen and menu availability controls. A weekly schedule requires an approved functional/API contract before code is added.

#### Compliance boundary

The current application contract supports Aadhaar and PAN proof evidence. This implementation does not claim that either document is sufficient for FSSAI or other legal compliance, and it does not invent document expiry/renewal rules. New compliance types must be approved in the product/legal and architecture documents first.

## Main files created

```text
apps/customer-web-next/src/components/chef-workspace-navigation.tsx
apps/customer-web-next/src/components/chef-page-header.tsx
apps/customer-web-next/src/components/chef-earnings-ledger.tsx
apps/customer-web-next/src/components/chef-operations-workspace.tsx
apps/customer-web-next/src/lib/chef-earnings-contract.ts
apps/customer-web-next/src/lib/chef-earnings-contract.test.ts
apps/customer-web-next/src/lib/chef-workspace-integration.test.ts
apps/customer-web-next/src/app/api/chef/earnings/route.ts
apps/customer-web-next/src/app/chef/earnings/page.tsx
apps/customer-web-next/src/app/chef/operations/page.tsx
```

## Main files updated

```text
apps/customer-web-next/src/app/chef/layout.tsx
apps/customer-web-next/src/app/chef/page.tsx
apps/customer-web-next/src/app/chef/application/page.tsx
apps/customer-web-next/src/app/chef/kitchen/page.tsx
apps/customer-web-next/src/app/chef/menu/page.tsx
apps/customer-web-next/src/app/chef/menu/media/page.tsx
apps/customer-web-next/src/app/chef/orders/page.tsx
apps/customer-web-next/src/app/chef/orders/[orderId]/page.tsx
apps/customer-web-next/src/components/chef-mode-dashboard.tsx
apps/customer-web-next/src/components/chef-order-inbox.tsx
apps/customer-web-next/src/components/chef-order-details.tsx
apps/customer-web-next/src/components/chef-order-actions.tsx
apps/customer-web-next/src/lib/chef-order-contract.ts
apps/customer-web-next/src/lib/chef-order-contract.test.ts
apps/customer-web-next/src/app/api/chef/orders/route.ts
apps/customer-web-next/src/app/api/chef/orders/[orderId]/route.ts
apps/customer-web-next/src/app/api/chef/orders/[orderId]/accept/route.ts
apps/customer-web-next/src/app/api/chef/orders/[orderId]/reject/route.ts
apps/customer-web-next/src/app/api/chef/orders/[orderId]/ready-for-pickup/route.ts
```

## Required runtime/APIM routes

Ensure the deployed web application can reach:

```text
GET    /api/v1/auth/me
POST   /api/v1/auth/refresh
GET    /api/v1/chef/applications/me
PUT    /api/v1/chef/applications/me
POST   /api/v1/chef/applications/me/proof-files
GET    /api/v1/kitchens/me
PUT    /api/v1/kitchens/me
GET    /api/v1/menu-items
POST   /api/v1/menu-items
PUT    /api/v1/menu-items/{menuItemId}
PUT    /api/v1/menu-items/{menuItemId}/availability
POST   /api/v1/menu-items/{menuItemId}/images
GET    /api/v1/chef/orders
GET    /api/v1/chef/orders/{orderId}
POST   /api/v1/chef/orders/{orderId}/accept
POST   /api/v1/chef/orders/{orderId}/reject
POST   /api/v1/chef/orders/{orderId}/ready-for-pickup
GET    /api/v1/chef/earnings?limit=200
```

Exact route naming must match the currently deployed APIM mappings already used by the existing BFF routes.

## Manual steps required

### Azure/APIM

- Verify the chef earnings route is exposed through APIM to Integration Service.
- Verify the deployed chef orders endpoint returns one of the supported envelopes documented above.
- No new Azure resource is required by this branch.
- No billable resource is provisioned.

### Secrets and credentials

- No new browser secret is introduced.
- Existing backend service credentials, Blob Storage access and authentication secrets remain in Azure/Key Vault.
- Do not paste any secret into the frontend environment.

### Finance operations

- Authorized finance/admin users must create and approve earning allocations.
- A payout/settlement provider still requires a separately approved product and integration contract before automation.

### Legal/compliance

- Product/legal owners must define any FSSAI workflow, supported document types, expiry and renewal rules before engineering adds them.

## Verification

From `apps/customer-web-next`:

```bash
npm ci --ignore-scripts --no-audit --no-fund
npm run lint
npm run typecheck
npm run test
npm run build
```

Relevant backend checks already covered by existing service tests should remain green, especially chef role/ownership and Order Service chef transitions.

## Manual acceptance matrix

1. Signed-out chef landing links to secure sign-in.
2. Customer without CHEF role sees application state only.
3. PENDING and REJECTED application update rules.
4. APPROVED application role refresh and chef boundary.
5. Kitchen create/update and each supported kitchen status.
6. Active kitchen without coordinates is not marked discovery-ready.
7. Menu create/update/status/availability.
8. Media upload and image visibility.
9. Chef order list direct array envelope.
10. Chef order list Spring Page and named envelopes.
11. Order accept, duplicate accept conflict and expired acceptance window.
12. Order reject with required reason.
13. Ready-for-pickup transition and conflict state.
14. Delivery/refund/terminal orders expose no unsupported chef action.
15. Empty and populated finance ledger.
16. Arithmetic-invalid finance response is rejected.
17. Operations readiness matches application/kitchen/menu states.
18. Mobile, tablet and desktop navigation and keyboard focus.

## Completion statement

All currently contracted code-owned customer and chef web modules requested in the UI/UX programme have implementation branches and verification gates. Items requiring a new business contract—weekly operating schedules, expanded legal/FSSAI compliance and automated payouts—are not software defects or hidden pending code; they are deliberately excluded because inventing those rules would violate the product and architecture constraints.
