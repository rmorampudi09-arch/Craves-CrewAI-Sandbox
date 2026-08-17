# CRAVES Landing Reference Visual Handover — 2026-08-11

## Scope

The public customer landing page is being updated to the four user-approved desktop reference images supplied on 2026-08-11. This is a landing-page change only; no image-generation work is part of the implementation.

## Design rules preserved

- Keep the current canonical Craves logo unchanged and continue rendering it through the shared `CravesLogo` component.
- Use the supplied reference artwork as the visual source for the desktop landing sections; do not redraw or alter the depicted content.
- Preserve customer authentication, chef registration, signed-in redirect, and mobile delivery-location behavior.
- Do not add fabricated backend data or change pricing, commission, serviceability, compliance, payments, Firebase, backend, APIM, or Azure resources.

## Code paths

Desktop reference implementation:

- `apps/customer-web-next/src/components/sections/landing-reference/ReferenceHeroDesktop.tsx`
- `apps/customer-web-next/src/components/sections/landing-reference/ReferenceArtworkSection.tsx`
- `apps/customer-web-next/src/screens/public/LandingPage/LandingPage.tsx`
- `apps/customer-web-next/src/screens/public/LandingPage/LandingV2.module.css`
- `apps/customer-web-next/src/components/sections/FooterSection.tsx`

Reference assets:

- `apps/customer-web-next/public/landing/reference/hero-reference.jpg`
- `apps/customer-web-next/public/landing/reference/how-craves-works-reference.jpg`
- `apps/customer-web-next/public/landing/reference/why-craves-reference.jpg`
- `apps/customer-web-next/public/landing/reference/home-chefs-app-reference.jpg`

## Responsive behavior

The supplied references are desktop compositions. Desktop (`lg`, 1024px and above) uses the supplied compositions directly. Below 1024px the existing responsive landing components remain active so baked-in desktop text is not reduced to unreadable mobile size.

## Manual steps required

No Azure Portal resource creation, DNS, Firebase, Cashfree, Key Vault, secret, certificate, or billing-sensitive change is required. After source validation and merge, deploy only through the existing customer-web deployment pipeline.

## Production acceptance checks

1. Signed-out desktop landing matches the four approved references in sequence.
2. Current canonical square Craves logo is rendered in the hero and has not been changed.
3. `Order Homemade Food` opens customer authentication.
4. `For Chefs` opens chef registration.
5. How It Works, Why Craves, Contact, and app anchors work.
6. Mobile remains responsive.
7. Signed-in users still redirect to `/home`.
8. Browser console is clean.
9. Customer web lint, typecheck, tests, and build pass before deployment.
