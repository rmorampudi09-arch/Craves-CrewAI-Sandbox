# Deploy the connected Craves customer web

This package updates only the existing Next.js web module and the guarded web deployment pipeline. It does not provision Azure resources and does not change Spring Boot services, API Management, databases, Firebase configuration, Cashfree secrets, or delivery-provider settings.

## Existing Azure targets

| Purpose | Existing resource |
| --- | --- |
| Resource group | `rg-craves-prodlow-centralindia` |
| Azure Container Registry | `cravesprodlowacr82121` |
| Customer web Container App | `ca-craves-web-prodlow` |
| API Management base | `https://api.craves.in/api/v1` |

## Repository placement

Copy the supplied files into the existing `Craves-Build-platform` repository:

```text
apps/customer-web-next/
azure-pipelines-customer-web-next-delivery-tracking.yml
```

Do not remove other backend, infrastructure, pipeline, or documentation folders.

## Azure DevOps variables

The pipeline already uses the existing `AZURE_SERVICE_CONNECTION`. Confirm these Firebase Web SDK values are present in the pipeline or its linked variable group:

```text
NEXT_PUBLIC_FIREBASE_API_KEY
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN
NEXT_PUBLIC_FIREBASE_PROJECT_ID
NEXT_PUBLIC_FIREBASE_APP_ID
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID
NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET
```

These are Firebase public web-app identifiers, not Firebase Admin credentials. Never add a service-account JSON or private key to this web pipeline.

## Safe deployment run

1. Create or edit the Azure DevOps pipeline so its YAML path is `/azure-pipelines-customer-web-next-delivery-tracking.yml`.
2. Run the pipeline manually.
3. Set `confirmReplaceCurrentCustomerWeb=true`.
4. Keep `cashfreeMode=sandbox`.
5. Leave the existing resource names and APIM URL unchanged.
6. The pipeline records the current image and revision in a `customer-web-rollback-<build-id>` artifact before it updates Azure.
7. It deploys a new `craves/customer-web-next:<build-id>` image and checks the home page plus the unauthenticated `/api/auth/me` boundary.
8. If readiness fails, the pipeline automatically restores the recorded previous image.

## Firebase step after deployment

Add this exact HTTPS hostname under Firebase Authentication authorized domains:

```text
ca-craves-web-prodlow.happysand-aedc7165.centralindia.azurecontainerapps.io
```

Also confirm Phone authentication is enabled and the SMS region policy allows India. Use a Firebase test phone number for repeated testing; avoid repeated real-SMS attempts.

## Acceptance test order

1. Open the Container App URL and confirm the preserved Craves colours, logo, food images, and icons.
2. Complete Firebase phone sign-in.
3. Confirm `/api/auth/me` returns the signed-in customer and a page reload preserves the secure session.
4. Test profile and saved addresses.
5. Test discovery and cart.
6. Create checkout from a saved address.
7. Complete only a Cashfree sandbox payment.
8. Confirm orders, delivery tracking, and notifications.
9. Keep wishlist treated as browser-local until a backend wishlist contract is implemented.

## Manual rollback

If a later issue appears after the pipeline has succeeded, download the rollback artifact and run the existing rollback pipeline with:

```text
confirmRollback=true
previousImage=<exact ROLLBACK_IMAGE value from state.env>
```
