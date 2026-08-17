# Customer and Chef Web Canonical Logo Replacement

## Request

Replace the previous Craves logo everywhere in the customer and chef web experience with the user-approved red rounded-square logo containing white `CRAVES` lettering. Remove the large transparent/white exterior margin from the uploaded source and prevent browsers from retaining the older asset.

## Implementation

- `apps/customer-web-next/public/brand/craves-logo.svg`
  - Replaced the embedded raster source with the cropped transparent version of the uploaded logo.
  - The source is 112 × 112 pixels, sufficient for the current maximum 56 CSS-pixel logo rendering at 2× density.
- `apps/customer-web-next/scripts/extract-brand-logo.mjs`
  - Generates `public/brand/craves-logo-20260805.png` before development and production builds.
  - Validates PNG signature, square dimensions, minimum dimensions, and the exact approved SHA-256 hash.
- `apps/customer-web-next/src/components/brand/CravesLogo.tsx`
  - Continues to be the single shared logo component for customer and chef pages.
  - Uses the versioned asset URL to bypass stale browser and edge caches.
- `apps/customer-web-next/src/app/layout.tsx`
  - Uses the same versioned logo for favicon, shortcut icon, and Apple touch icon.
- `apps/customer-web-next/src/lib/logo-address-runtime.test.ts`
  - Verifies the public landing page, authentication modal, browse, cart, checkout, profile, tracking, order history, and chef workspace shell all render the shared `CravesLogo` component.

## Safety

- No backend, APIM, database, address, payment, Firebase, or Azure resource changes.
- No secrets or environment variables added.
- No customer or chef data changes.

## Deployment

Run only:

```text
/azure-pipelines-customer-web-next-delivery-tracking.yml
```

with `main`, `confirmReplaceCurrentCustomerWeb=true`, and `cashfreeMode=sandbox` after the PR is merged.

The customer-address APIM pipeline does not need another run.
