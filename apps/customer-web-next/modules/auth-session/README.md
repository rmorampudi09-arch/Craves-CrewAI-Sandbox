# Customer Web Phone OTP Session

## Purpose

This module converts the existing Firebase Phone Authentication proof into a customer-facing Next.js session flow.

```text
phone + reCAPTCHA
  -> Firebase SMS OTP
  -> Firebase ID token in browser memory
  -> same-origin Next.js session route
  -> Craves Auth /firebase/exchange through APIM
  -> HTTP-only craves_access_token cookie
  -> customer BFF routes
```

The Craves access token is never returned to browser JavaScript, copied to the clipboard, written to local storage, or printed in logs.

## Main files

```text
src/lib/firebase-client.ts
src/lib/auth-contract.ts
src/lib/auth-contract.test.ts
src/components/phone-auth-form.tsx
src/app/sign-in/page.tsx
src/app/api/auth/session/route.ts
src/app/api/auth/me/route.ts
src/app/api/auth/logout/route.ts
```

## Environment

Server runtime:

```text
CRAVES_API_BASE_URL
```

Public Firebase web configuration, embedded at build time:

```text
NEXT_PUBLIC_FIREBASE_API_KEY
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN
NEXT_PUBLIC_FIREBASE_PROJECT_ID
NEXT_PUBLIC_FIREBASE_APP_ID
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID
NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET
```

Firebase web configuration values identify the Firebase application; they are not Firebase Admin credentials. Never add service-account JSON, private keys, Craves access tokens, or refresh tokens to these variables.

## Security controls

- session creation and logout require a same-origin request;
- only a bounded Firebase ID token is accepted;
- upstream calls have a ten-second timeout;
- upstream error bodies are not returned;
- the Craves exchange response is validated and allow-listed;
- the access cookie is HTTP-only, SameSite=Lax, Path=/ and Secure in production;
- `/api/auth/me` returns only customer-safe identity fields;
- a backend 401 clears the access cookie;
- all auth responses use `Cache-Control: no-store`.

## Local run

```bash
cd apps/customer-web-next
cp .env.example .env.local
npm install
npm run dev
```

Use a Firebase test phone number while developing to avoid SMS cost and rate limits.

## CI

```text
azure-pipelines-customer-web-next-auth-ci.yml
```

## Deployment

The existing guarded Next.js deployment pipeline now requires Firebase web configuration:

```text
azure-pipelines-customer-web-next-delivery-tracking.yml
```

The deployment remains disabled unless `confirmReplaceLegacyCustomerWeb=true`.

## Manual steps required

- Firebase Console: confirm Phone provider is enabled.
- Firebase Console: register the final web domain under Authorized domains.
- Firebase Console: copy the existing web-app configuration into Azure DevOps variables using the exact names above.
- Azure DevOps: do not mark the public Firebase web values as application secrets that are later expected at runtime; they are image build inputs.
- Do not paste any Firebase Admin private key or Craves token into chat or source control.

## Deferred

- password login remains deferred until the approved backend password endpoint contract is confirmed;
- automatic Craves refresh-token rotation remains deferred until the backend refresh contract is confirmed;
- Google and Apple sign-in remain separate modules;
- account creation/profile completion remains governed by the existing User-Chef/Auth service contract.
