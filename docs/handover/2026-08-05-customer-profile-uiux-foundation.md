# Craves Customer Profile UI/UX Foundation — Handover

**Date:** 5 August 2026  
**Branch:** `feat/customer-chef-uiux-foundation`  
**Canonical design reference:** `CRV-UIUX-BUILD-001 v1.0`  
**Application:** `apps/customer-web-next`

## Scope completed

This module establishes the shared Craves web design foundation and fixes the customer identity/profile flow before the remaining customer and chef screens are redesigned. It deliberately does not redesign discovery, cart, checkout, order tracking, menu management or chef orders in the same change set.

### Functional changes

- Customer registration now captures `firstName`, `lastName` and optional `email` as separate fields.
- The old full-name split and fabricated `lastName = "Customer"` fallback were removed.
- Profile creation is no longer best-effort. A registration is not presented as complete if `PUT /api/customer/profile` fails or returns an invalid contract.
- Normal sign-in hydrates the in-memory session from the authoritative customer profile endpoint.
- Editing a profile updates the backend and the current in-memory identity together.
- The customer profile screen uses API-derived profile, address, order and chef-application data.
- The browser-only wishlist row and technical placeholder copy were removed from the profile screen.

### UI/UX changes

- Added the canonical Flame Red, Contrast Red, Espresso, Cream, neutral, semantic, radius, shadow and motion tokens.
- Added Plus Jakarta Sans for display typography and Inter for body/UI typography through `next/font`.
- Added the exact transparent Craves rounded-square logo asset and a reusable `CravesLogo` component.
- Rebuilt sign-in/sign-up and profile components with semantic labels, visible focus, Escape handling, adequate touch targets, loading states and error states.
- Applied the same Craves editorial token system to the existing chef-console compatibility theme so later chef modules no longer inherit the previous purple/gold gradient identity.

## Files added

- `apps/customer-web-next/public/brand/craves-logo.svg`
- `apps/customer-web-next/src/components/brand/CravesLogo.tsx`
- `apps/customer-web-next/src/craves-theme.css`
- `apps/customer-web-next/src/lib/profile-contract.test.ts`
- `docs/handover/2026-08-05-customer-profile-uiux-foundation.md`

## Files changed

- `apps/customer-web-next/src/app/layout.tsx`
- `apps/customer-web-next/src/components/auth/AuthModal.tsx`
- `apps/customer-web-next/src/components/profile/AccountCard.tsx`
- `apps/customer-web-next/src/components/profile/AddressCard.tsx`
- `apps/customer-web-next/src/components/profile/EditProfileModal.tsx`
- `apps/customer-web-next/src/components/profile/ProfileHeader.tsx`
- `apps/customer-web-next/src/components/profile/ProfileLinkCard.tsx`
- `apps/customer-web-next/src/screens/Profile/Profile.tsx`
- `apps/customer-web-next/src/services/auth/cravesAuth.ts`

## API contracts used

Browser components call local Next.js BFF routes only:

- `POST /api/auth/session`
- `GET /api/auth/me`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/customer/profile`
- `PUT /api/customer/profile`
- `GET /api/customer/addresses`
- `GET /api/orders`
- `GET /api/chef/application`

The BFF continues to call APIM using server-side environment configuration. No backend hostname or secret was added to client code.

## Local verification

From the repository root:

```bash
cd apps/customer-web-next
npm ci
npm run lint
npm run typecheck
npm run test
CRAVES_API_BASE_URL=https://example.invalid/api/v1 \
NEXT_PUBLIC_CASHFREE_MODE=sandbox \
npm run build
```

Manual browser verification:

1. Open the landing page and choose **Sign Up**.
2. Confirm separate First name, Last name and optional Email fields appear before OTP.
3. Complete Firebase OTP with an authorised test number.
4. Confirm the profile request succeeds and the actual first and last name appear on `/profile`.
5. Refresh the browser and confirm the profile remains hydrated from the backend rather than reverting to a static name.
6. Edit the profile, save, refresh and confirm the backend values remain visible.
7. Test at 375px, 768px, 1024px and 1440px widths.
8. Test keyboard Tab order, visible focus and Escape-to-close on both dialogs.

## Manual steps required

- **Azure Portal:** none for this module.
- **Billing-sensitive actions:** none.
- **GitHub secrets:** none added.
- **Firebase Console:** no new configuration. Existing authorised domains and phone-auth settings must remain valid.
- **Cashfree:** none.
- **DNS/domains:** none.
- **Mobile stores/signing:** none.

## Deployment

Do not deploy directly from this branch. Merge only after the pull-request CI passes. The existing customer-web deployment pipeline should then be run using the already established Azure service connection and environment variables.

## Risks and follow-up

- The old application contains many components with legacy theme classes. The new compatibility layer makes them visually safer, but each screen still needs an explicit component-by-component redesign.
- The exact logo is stored as an SVG wrapper around the approved transparent raster asset to preserve the source appearance through the GitHub text-file API. A normal PNG can replace it later when binary asset upload is performed from a local checkout.
- Customer discovery, food cards, cart, checkout, orders and chef-console screens are intentionally pending.
- `GET /api/v1/chef/orders` still needs a deployed response-contract smoke test and parser correction in the chef-orders module; it is not masked with static data here.

## Recommended next module

**Customer landing and discovery:** shared header, marketing hero, location/search, real kitchen/menu-item cards, loading/empty/error states and removal of remaining static discovery content. After that, complete cart/checkout and then chef dashboard/orders.
