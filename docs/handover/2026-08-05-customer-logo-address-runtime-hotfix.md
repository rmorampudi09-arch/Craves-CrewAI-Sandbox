# Craves Customer Logo and Address Runtime Hotfix

Date: 2026-08-05  
Branch: `fix/web-logo-address-apim`  
Scope: customer web brand correction and existing customer-address APIM activation

## Reported production symptoms

1. The public landing header displayed a legacy Craves lockup instead of the approved red rounded-square logo.
2. Existing saved addresses did not load.
3. Creating a new address returned:

```json
{
  "error": "ADDRESS_REQUEST_FAILED",
  "message": "Address request could not be completed."
}
```

## Root causes

### Logo

The shared customer and chef surfaces already used the canonical `/brand/craves-logo.svg` asset through `CravesLogo`. The public landing layout still used `src/assets/images/craves-logo.png` and rendered a second text wordmark plus the `FOOD FROM HOME` tagline. That legacy component produced a different logo treatment from the approved attached design.

### Addresses

The Next.js address BFF and User/Chef Service address CRUD endpoints were already implemented. The address handover explicitly recorded the module as code-complete but APIM/runtime activation pending. The collection route used by the browser is:

```text
Browser BFF: /api/customer/addresses
APIM:        /api/v1/customer/addresses
Backend:     /api/v1/customer/addresses
```

The existing `azure-pipelines-customer-addresses-apim.yml` only invoked the configuration script and did not prove that GET and POST operations were reachable after the APIM write. The pipeline is now hardened to validate assets, require explicit confirmation, configure the existing APIM service and verify that both collection methods return HTTP 401 when called without a Bearer token. HTTP 401 is the safe expected probe result: it proves that the route exists and authentication is enforced without using a customer token.

## Code changes

### Updated

```text
apps/customer-web-next/src/components/layout/Logo.tsx
azure-pipelines-customer-addresses-apim.yml
```

### Added

```text
apps/customer-web-next/src/lib/logo-address-runtime.test.ts
docs/handover/2026-08-05-customer-logo-address-runtime-hotfix.md
```

## Brand behavior after deployment

- Public landing navigation now uses the same canonical `CravesLogo` component as the rest of the customer and chef application.
- The legacy PNG, duplicate text wordmark and duplicate tagline are no longer rendered by `Logo.tsx`.
- The approved logo remains the single source of truth at:

```text
apps/customer-web-next/public/brand/craves-logo.svg
```

## Required APIM operations

The existing configuration script owns these operations under the existing `api/v1/customer` API:

```text
GET    /addresses
POST   /addresses
GET    /addresses/{addressId}
PUT    /addresses/{addressId}
DELETE /addresses/{addressId}
GET    /addresses/recommendation
```

No new Azure resource is created. The pipeline modifies the existing APIM configuration and therefore requires explicit operator confirmation.

## Manual execution order

### 1. Merge this hotfix after CI succeeds

The customer-web CI must pass lint, typecheck, tests and production build.

### 2. Configure address operations in APIM

Create or open the Azure DevOps pipeline whose YAML path is:

```text
/azure-pipelines-customer-addresses-apim.yml
```

Run from `main` with:

```text
confirmConfigureCustomerAddresses: true
resourceGroupName: rg-craves-prodlow-centralindia
apimServiceName: apim-craves-prodlow-l3ing6
userChefContainerAppName: ca-craves-user-chef-service-prod
```

Expected final log:

```text
SUCCESS: Customer address GET/POST routes exist in APIM and enforce Bearer authentication.
```

Expected safe unauthenticated probe result:

```text
GET=401 POST=401
```

Stop when either route returns 404, the User/Chef Container App is not Ready, multiple APIM APIs own the same path, or an inherited backend-id policy is detected.

### 3. Redeploy customer web

Run the existing customer web deployment pipeline from `main`:

```text
/azure-pipelines-customer-web-next-delivery-tracking.yml
```

Use:

```text
confirmReplaceCurrentCustomerWeb: true
cashfreeMode: sandbox
```

### 4. Runtime acceptance

1. Open the customer web in an Incognito/InPrivate window.
2. Confirm the public header displays only the approved attached Craves logo.
3. Sign in with the existing Firebase test/customer account.
4. Open Profile → Addresses.
5. Confirm existing active addresses load.
6. Create one test address with complete area and latitude/longitude values.
7. Refresh and confirm the address persists.
8. Edit the exact test address.
9. Delete only the exact test address and confirm it disappears from the active list.
10. Proceed to checkout and confirm the remaining saved/default address is selectable.

## Manual steps and safety

### Azure Portal / Azure DevOps

- Run the guarded APIM pipeline.
- Run the customer-web deployment pipeline after merge.
- No new paid Azure resource is provisioned.

### Secrets

- No new secret is required.
- Do not paste Firebase tokens, Azure credentials or customer access tokens into chat or pipeline logs.

### Database

- No migration is added by this hotfix.
- The existing User/Chef address migration and PostGIS support must already be deployed.

## Completion boundary

This hotfix does not add geocoding, reverse geocoding, delivery radius, delivery fee, serviceability or address-verification business rules. It activates the already-approved address CRUD contract and standardizes the public logo on the approved asset.
