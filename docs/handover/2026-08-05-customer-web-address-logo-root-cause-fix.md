# Customer Web Address and Logo Root-Cause Fix

Date: 2026-08-05  
Branch: `fix/customer-web-live-address-logo`  
Scope: customer Next.js application only

## Reported runtime symptoms

After the customer-address APIM pipeline and customer-web deployment pipeline both succeeded:

- Profile > Addresses still displayed `Address request could not be completed.`
- the approved Craves logo was not visible on the deployed landing page.

## Confirmed address root cause

The backend address API was not missing. Earlier applications could use it because the original database contract allowed historical address rows with nullable fields:

- `recipient_name` could be null;
- `postal_code` could be null;
- latitude and longitude could be null;
- the later location migration added nullable `area_name`.

The rebuilt Next.js address parser required every returned row to contain all of those newer fields. One historical row therefore caused the parser to reject the entire address array. The BFF then converted that parser failure into the generic `ADDRESS_REQUEST_FAILED` message.

A create request could also succeed in the backend and still look unsuccessful in the UI: after saving, the page reloaded the complete list; the same historical row made that reload fail.

## Address fix

Updated:

```text
apps/customer-web-next/src/lib/address-contract.ts
apps/customer-web-next/src/lib/address-contract.test.ts
apps/customer-web-next/src/lib/address-selection.ts
apps/customer-web-next/src/lib/address-selection.test.ts
apps/customer-web-next/src/app/api/customer/addresses/route.ts
apps/customer-web-next/src/screens/Profile/Addresses.tsx
apps/customer-web-next/src/components/customer-addresses.tsx
```

The customer web now:

1. Parses both current complete rows and historical nullable rows.
2. Keeps historical rows visible so the customer can edit them.
3. Marks incomplete rows as `UPDATE REQUIRED`.
4. Prevents incomplete rows from being selected for checkout or subscriptions.
5. Requires a complete recipient, phone, written address, area, postal code and valid coordinate pair before save.
6. Preserves safe upstream status, message and correlation metadata instead of hiding every failure behind one generic response.
7. Accepts direct arrays and common list envelopes without inventing address data.

No database record is automatically changed or deleted. Historical rows become complete only when the customer explicitly edits and saves them.

## Confirmed logo root cause

The deployed landing page already used `CravesLogo` directly. The previous patch changed a legacy layout wrapper rather than the real landing-page path.

The canonical `craves-logo.svg` was also not a true vector logo. It wrapped the approved PNG as a base64 `data:image/png` inside an SVG `<image>` element. That nested image path was not reliable in the production rendering path.

## Logo fix

Updated:

```text
apps/customer-web-next/src/components/brand/CravesLogo.tsx
apps/customer-web-next/scripts/extract-brand-logo.mjs
apps/customer-web-next/package.json
apps/customer-web-next/src/lib/logo-address-runtime.test.ts
```

The build now:

1. Extracts the already-approved embedded PNG from the canonical SVG before development and production builds.
2. Validates the PNG signature and minimum size.
3. Writes `public/brand/craves-logo.png` inside the build workspace.
4. Serves that real local PNG directly through `next/image` with optimisation bypassed for this small brand asset.

The Docker build copies the generated PNG from the builder's `public` directory into the runtime image.

## Safety boundaries

- No Azure resource is created.
- No APIM operation is changed by this source fix.
- No database migration or automatic data backfill is executed.
- No customer address is deleted or silently rewritten.
- No Firebase, Cashfree, Azure or Key Vault secret is changed.
- The approved logo image already stored in the repository remains the source of truth.

## Verification required before merge

The pull request must pass:

```text
npm run lint
npm run typecheck
npm run test
npm run build
```

The production build must log that the approved PNG was prepared.

## Deployment after merge

Run only:

```text
/azure-pipelines-customer-web-next-delivery-tracking.yml
```

From `main` with:

```text
confirmReplaceCurrentCustomerWeb: true
cashfreeMode: sandbox
```

The customer-address APIM pipeline does not need to be run again.

## Runtime acceptance

1. Open an Incognito/InPrivate window.
2. Confirm the approved red Craves logo is visible on the public landing header.
3. Sign in and open Profile > Addresses.
4. Confirm all historical saved addresses load.
5. Confirm incomplete older rows show `UPDATE REQUIRED` rather than breaking the full list.
6. Edit one incomplete row, complete the missing fields and capture coordinates.
7. Save, refresh and confirm it becomes checkout-eligible.
8. Create, edit and delete one clearly named test address.
9. Confirm checkout lists only complete active addresses.
