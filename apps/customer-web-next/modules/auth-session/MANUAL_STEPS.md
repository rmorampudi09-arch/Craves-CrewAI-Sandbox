# Manual Steps Required

## Firebase Console

- Open the existing Craves Firebase project.
- Confirm Authentication > Sign-in method > Phone is enabled.
- Add the final customer-web hostname to Authorized domains.
- Copy the existing web app configuration values.
- Configure test phone numbers for development.

## Azure DevOps variables

Create these exact non-secret build variables:

```text
NEXT_PUBLIC_FIREBASE_API_KEY
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN
NEXT_PUBLIC_FIREBASE_PROJECT_ID
NEXT_PUBLIC_FIREBASE_APP_ID
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID
NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET
```

Do not create or paste a Firebase Admin private key.

## Pipeline

Keep `confirmReplaceLegacyCustomerWeb=false` until all parent PRs, CI gates, APIM configuration and authenticated smoke tests are complete.
