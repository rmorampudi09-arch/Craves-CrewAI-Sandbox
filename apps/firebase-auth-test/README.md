# Craves Firebase Auth Test App

Developer-only test page for validating the Craves Auth v1 flow before wiring it into the production web and mobile apps.

## What it tests

```text
Firebase Phone OTP
  -> Firebase ID token
  -> Azure API Management
  -> Craves Auth Service /api/v1/auth/firebase/exchange
  -> Craves access token + refresh token
  -> /api/v1/auth/me
```

## Prerequisites

- Node.js installed locally.
- Firebase project created.
- Firebase Authentication -> Sign-in method -> Phone enabled.
- A Firebase test phone number configured for first testing.
- APIM Auth route already created and returning `AUTHENTICATION_REQUIRED` for `/api/v1/auth/me` without a token.

## Local setup

From repository root:

```bash
cd apps/firebase-auth-test
cp .env.example .env.local
npm install
npm run dev
```

Open:

```text
http://localhost:3001
```

## Environment variables

Create `apps/firebase-auth-test/.env.local`:

```env
NEXT_PUBLIC_FIREBASE_API_KEY=
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=
NEXT_PUBLIC_FIREBASE_PROJECT_ID=
NEXT_PUBLIC_FIREBASE_APP_ID=
NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=
NEXT_PUBLIC_CRAVES_AUTH_BASE_URL=https://api.craves.in/api/v1/auth
```

Get the Firebase values from:

```text
Firebase Console
-> Project settings
-> General
-> Your apps
-> Web app
-> Firebase SDK config
```

These are Firebase browser app identifiers. Do not paste the Firebase Admin SDK private key into this file.

## Firebase test phone number

For development, use Firebase test phone numbers first:

```text
Firebase Console
-> Authentication
-> Sign-in method
-> Phone
-> Phone numbers for testing
```

Example format:

```text
+919876543210
123456
```

Do not use real SMS repeatedly while testing. Real SMS may create Firebase billing usage.

## Authorized domain

For local testing, Firebase Auth must allow the current browser domain.

Check:

```text
Firebase Console
-> Authentication
-> Settings
-> Authorized domains
```

For local development, make sure this exists:

```text
localhost
```

## How to test

1. Open `http://localhost:3001`.
2. Enter a phone number in E.164 format, for example `+919876543210`.
3. Complete reCAPTCHA.
4. Click **Send OTP**.
5. Enter the OTP.
6. Click **Verify OTP**.
7. Click **Exchange with Craves**.
8. Confirm Craves returns:

```json
{
  "tokenType": "Bearer",
  "accessToken": "...",
  "expiresIn": 900,
  "refreshToken": "...",
  "identity": {
    "phoneNumber": "+91...",
    "roles": ["CUSTOMER"],
    "status": "ACTIVE"
  }
}
```

9. Click **Test /me**.
10. Confirm `/me` returns the same Craves identity.

## Manual steps required

### Firebase Console

- Enable Phone sign-in provider.
- Add Firebase test phone numbers.
- Confirm `localhost` is in authorized domains.
- Create or select a Firebase Web App and copy its browser config values.

### Secrets and credentials

- Do not paste Firebase Admin SDK JSON here.
- Do not commit `.env.local`.
- Only use `NEXT_PUBLIC_*` browser config values in this app.

### Azure

No new Azure resources are required for this test app. It uses your existing APIM Auth route.
