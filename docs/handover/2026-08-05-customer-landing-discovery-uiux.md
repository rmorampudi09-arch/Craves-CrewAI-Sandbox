# Craves Customer Landing and Discovery UI/UX

Date: 2026-08-05  
Branch: `feat/customer-landing-discovery-uiux`  
Design source: `CRV-UIUX-BUILD-001 v1.0`  
API source: `CRV-API-FE-001 v1.0`

## Scope completed

This module completes the public landing experience, authenticated discovery feed and live dish-detail entry path. It deliberately does not introduce example chefs, prices, reviews, ratings or menu records.

### Public landing

- Rebuilt the hero with the approved Craves red, cream, espresso and gold token system.
- Uses the exact Craves rounded-square logo component.
- Added responsive desktop/mobile navigation with real section anchors.
- Added accessible sign-in, registration, delivery-location and chef-onboarding actions.
- Removed fabricated testimonial and platform-stat sections from the landing composition.
- Existing signed-in sessions still redirect directly to `/home`.
- Added an explicit session-loading state rather than displaying the guest page briefly.

### Authenticated discovery

- Rebuilt the sticky customer header and service navigation.
- Uses the actual first name hydrated from `/customer/profile`.
- Removed the browser-only wishlist link/count because no backend wishlist contract is verified.
- The discovery feed now has explicit loading skeletons, mapped-address-required, backend error, empty, filtered-empty and populated states.
- Search filters actual returned item name, kitchen name, category and description.
- Category filters are derived from the live catalog rather than a fixed example list.
- Radius and dish count are derived from the actual discovery result.
- Catalog failure never substitutes the former `DISHES` development array.

### Dish cards and detail

- Rebuilt dish cards for mobile-first responsive layouts and 44px minimum actions.
- Add-to-cart exposes adding, success and error states.
- Ratings and reviews are rendered only if supplied by a verified contract; the current discovery DTO therefore does not fabricate them.
- Removed local-only wishlist actions.
- Deleted the static 16-record development dish catalogue from runtime code.
- Added a safe BFF detail route at `/api/catalog/menu-items/{menuItemId}`.
- The BFF fetches catalog item and kitchen records but returns an allow-list that excludes identity ID, phone, email and pickup-address fields.
- Direct dish-detail page refreshes now use the safe catalog detail route instead of depending on in-memory discovery or example records.
- Missing kitchen images use the Craves brand asset and are visibly labelled as unavailable rather than showing a misleading food image.

## Files changed

```text
apps/customer-web-next/src/components/sections/HeroSection.tsx
apps/customer-web-next/src/screens/public/LandingPage/LandingPage.tsx
apps/customer-web-next/src/components/home/BrowseHeader.tsx
apps/customer-web-next/src/components/home/WelcomeBanner.tsx
apps/customer-web-next/src/components/home/CategoryFilterChips.tsx
apps/customer-web-next/src/components/home/DishesGrid.tsx
apps/customer-web-next/src/components/home/DishCard.tsx
apps/customer-web-next/src/components/home/FloatingCartBar.tsx
apps/customer-web-next/src/constants/dishCategories.ts
apps/customer-web-next/src/screens/public/BrowseFoods/BrowseFoods.tsx
apps/customer-web-next/src/services/api/dishes.ts
apps/customer-web-next/src/lib/public-menu-item-contract.ts
apps/customer-web-next/src/lib/public-menu-item-contract.test.ts
apps/customer-web-next/src/app/api/catalog/menu-items/[menuItemId]/route.ts
apps/customer-web-next/src/screens/public/FoodDetails/FoodDetails.tsx
apps/customer-web-next/src/components/order/DishImageHeader.tsx
apps/customer-web-next/src/components/order/DishInfoSummary.tsx
apps/customer-web-next/src/components/order/DishBottomBar.tsx
```

## Data and security decisions

- No static kitchen, menu, price, rating, review, availability or delivery-distance record is used as runtime fallback.
- Public kitchen detail is treated as a private upstream DTO. Only the display-safe kitchen fields are returned by the BFF.
- The frontend does not mark a kitchen as verified based on UI assumptions.
- HTTPS is required for catalog image URLs.
- Protected customer routes continue to use the HTTP-only Craves session.

## Manual steps required

None for source-code integration.

Before production acceptance, an operator must verify that APIM exposes and secures:

```text
GET /api/v1/discovery/menu-items
GET /api/v1/catalog/menu-items/{menuItemId}
GET /api/v1/catalog/kitchens/{kitchenId}
```

No Azure resource, DNS record, Firebase setting, Cashfree credential or paid service is created by this module.

## Verification

Run from `apps/customer-web-next`:

```bash
npm ci --ignore-scripts --no-audit --no-fund
npm run lint
npm run typecheck
npm run test
npm run build
```

Manual browser checks:

1. Guest landing at 375px, 768px, 1024px and 1440px.
2. Keyboard navigation through header, auth actions and chef CTA.
3. Existing session redirects to `/home` without guest-content flash.
4. No-address state links to address management.
5. Live discovery populated, empty and upstream-error states.
6. Search and returned-category filters.
7. Add-to-cart success and backend error.
8. Direct refresh of `/dish/{validMenuItemId}`.
9. Inactive/unavailable menu item returns the unavailable state.
10. Inspect the BFF response and confirm private kitchen fields are absent.

## Remaining programme modules

This handover covers only customer landing and discovery. Cart/checkout, customer orders/tracking and chef workspaces are handled in the next controlled branches and must pass their own CI before the overall UI/UX programme is called complete.
