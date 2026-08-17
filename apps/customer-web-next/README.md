# Craves customer web

The uploaded Craves interface has been migrated from its temporary Vite/TanStack shell to the approved Next.js customer-web stack and connected to the current Spring Boot backend contracts. The original orange/cream design tokens, logo, food imagery and Lucide icons are retained.

Backend baseline used for this integration: `rmorampudi09-arch/Craves-Build-platform`, `main` merge commit `a78e676e9a06ed43d6478e645cc22a038ee827b6` (2 August 2026).

## Connected customer flows

| Area | Browser route | Backend contract |
|---|---|---|
| Firebase phone sign-in | `/` | `POST /auth/firebase/exchange`, refresh, logout, `/auth/me` |
| Profile | `/profile` | `GET/PUT /customer/profile` |
| Addresses | `/addresses` | customer address CRUD and location recommendation |
| Nearby menu | `/home` | `/discovery/kitchens`, `/discovery/menu-items` |
| Cart | `/cart` | cart read, add, quantity, remove, clear and validate |
| Checkout | `/payment` | `POST /checkout`, `GET /checkout/{id}` |
| Payment | `/checkout/{id}/payment` | Razorpay payment create, status and backend verify |
| Orders | `/orders` | order list and detail |
| Delivery | `/tracking?id={orderId}` | provider-neutral delivery status and history |
| Notifications | `/notifications` | inbox and mark-read |
| Chef account choice | login/sign-up | same Firebase/Craves identity, customer or chef continuation |
| Chef registration | `/chef/application` | application status, submission and KYC proof upload |
| Chef mode | `/chef` | approved role, kitchen, menu and chef-owned orders |

The browser never stores access or refresh tokens. The Next.js BFF keeps them in secure, HTTP-only cookies and forwards them to API Management server-side. Mutating BFF routes enforce same-origin requests and validate identifiers and response shapes.

Pricing is authoritative only when returned by Cart Service and Order Service. The frontend does not invent delivery fees, platform fees, taxes, discounts or eligibility rules. Payment details are collected only by Razorpay Checkout; Craves does not render card, CVV, UPI PIN or banking fields.

Wishlist remains explicitly browser-local because the current backend has no customer wishlist contract. It is isolated from checkout and never determines backend pricing.

## Local setup

Requirements: Node.js 24 and npm.

1. Copy `.env.example` to `.env.local`.
2. Keep the API base as:

   ```text
   https://api.craves.in/api/v1
   ```

3. In Firebase Console, open the Craves web app and copy only the public Web SDK configuration into the `NEXT_PUBLIC_FIREBASE_*` variables. Do not use or expose a Firebase Admin private key.
4. Use local mode for visual and contract testing. Test real Firebase phone OTP only from the deployed HTTPS Container App hostname; use a configured Firebase test number for repeated tests.
5. Keep `NEXT_PUBLIC_RAZORPAY_MODE=sandbox` until controlled sandbox payment and webhook tests pass.
6. Install, verify and start:

   ```bash
   npm ci
   npm run verify
   npm run dev
   ```

7. Open `http://localhost:3000` and sign in with a Firebase test customer.

`NEXT_PUBLIC_CRAVES_ALLOW_CATALOG_FALLBACK` must be `false` in production. It exists only to preview the uploaded visual catalogue in development when Catalog/APIM has no seeded records.

## Manual runtime checks

Use a test customer and confirm, in this order:

1. Phone OTP sign-in succeeds and refresh keeps the session without browser-visible tokens.
2. Profile and saved-address changes survive a reload; Home must use the active default address coordinates.
3. Select Home Chef during login/sign-up and confirm the user continues to the application or approved chef dashboard.
4. Nearby menu responses use the active saved-address coordinates and a 5 km request radius. An empty result is valid until an approved kitchen is active, geocoded and has a sellable menu item.
5. Add, change and remove cart items; confirm displayed amounts equal backend responses.
6. Create checkout using a saved address; confirm all fee/tax/grand-total values come from Order Service.
7. Open Razorpay test checkout. Complete a test payment and verify the result through the backend.
8. Confirm the paid checkout creates customer orders and delivery tracking appears only after a delivery job exists.
9. Open notifications, mark one unread item read, reload and confirm `readAt` persists.
10. Sign out and confirm protected routes return to sign-in.

## Azure deployment handoff

The Dockerfile produces a non-root Next.js standalone image. Firebase and Razorpay public variables are build-time values. `CRAVES_API_BASE_URL` is a server-only runtime value and must be set on the deployed container/app.

Before production:

- rotate any Storage, PostgreSQL, provider or other credentials that have appeared in chat, logs or configuration;
- configure the final HTTPS web domain in Firebase authorized domains;
- keep Razorpay secrets only in Integration Service/Key Vault, never in this web app;
- confirm the APIM customer operations above route to the deployed backend versions;
- confirm Razorpay test checkout and webhook verification before switching the guarded pipelines to `production` mode;
- verify delivery-provider activation separately; the UI deliberately shows a waiting state when no delivery job exists;
- keep provider execution fail-closed when required runtime configuration is absent.

`azure-pipelines.yml` runs build-only verification for a standalone checkout. In the Craves monorepo, use `azure-pipelines-razorpay-customer-web.yml`; it builds the selected sandbox or production mode, updates the existing Container App, verifies readiness, and restores the previous image automatically if verification fails.

See `SECURITY.md` for the current upstream Next.js/PostCSS audit exception and its mitigation.

## Commands

```bash
npm run dev        # local Next.js server
npm run lint       # ESLint
npm run typecheck  # strict TypeScript
npm run test       # Vitest contract tests
npm run build      # optimized standalone build
npm run start      # stage static assets and run the standalone production build
```
