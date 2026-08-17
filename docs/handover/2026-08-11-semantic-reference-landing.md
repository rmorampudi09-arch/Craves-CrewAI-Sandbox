# Craves customer landing — semantic reference implementation

Date: 2026-08-11

## Goal

Replace the temporary full-screenshot desktop composition with a production landing page that keeps the approved visual direction while making all important content native HTML and all controls real interactive elements.

## Source artwork

The four product-owner supplied PNGs remain unchanged under:

- `apps/customer-web-next/public/landing/reference/hero-reference.png`
- `apps/customer-web-next/public/landing/reference/how-craves-works-reference.png`
- `apps/customer-web-next/public/landing/reference/why-craves-reference.png`
- `apps/customer-web-next/public/landing/reference/home-chefs-app-reference.png`

`ReferenceImageCrop.tsx` clips approved source pixels at render time. It does not create, recolour, filter, retouch, resize on disk or otherwise modify the source PNG files.

## Native HTML

The following are now native semantic text rather than screenshot text:

- header/navigation
- hero label, title, body copy and CTAs
- four hero feature cards
- How Craves Works heading, step numbers, labels, descriptions and callout
- Why Craves heading, four reasons, descriptions and impact callout
- Meet the Home Chefs heading/body/chef CTA
- Craves App heading/body/store availability labels

## Real controls

- `Order Homemade Food` opens the existing customer login flow.
- `For Chefs` and `Become a Home Chef` open the existing chef registration flow.
- delivery location opens the existing LocationModal.
- `Watch How It Works`, `Why Craves`, `Contact` and `Get the App` use real anchor navigation.
- current auth/session redirects are unchanged.

## Logo

The canonical `CravesLogo` component remains the only logo source. No logo asset was changed.

## Responsive behavior

The previous desktop-reference screenshot plus separate mobile fallback was removed. The new semantic reference experience is one responsive implementation across desktop, tablet and mobile breakpoints.

## Files

- `apps/customer-web-next/src/components/sections/landing-reference/ReferenceImageCrop.tsx`
- `apps/customer-web-next/src/components/sections/landing-reference/ReferenceHeroDesktop.tsx`
- `apps/customer-web-next/src/components/sections/landing-reference/ReferenceArtworkSection.tsx`
- `apps/customer-web-next/src/screens/public/LandingPage/LandingPage.tsx`
- `apps/customer-web-next/src/screens/public/LandingPage/LandingV2.module.css`
- `apps/customer-web-next/src/lib/landing-reference-assets.test.ts`

## Scope exclusions

No backend, APIM, Firebase, Cashfree, database, DNS, Azure resource, secret, pricing, commission, delivery-rule or mobile-app runtime change is included.
