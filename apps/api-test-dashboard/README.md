# Craves API Test Dashboard

Internal web dashboard for testing the current Craves backend flow without running manual `curl` commands every time.

## What this module tests

1. Firebase Phone OTP login
2. Firebase ID token generation
3. Craves Auth token exchange through APIM
4. Craves `/api/v1/auth/me`
5. Craves `/api/v1/notifications/in-app`
6. Craves `PATCH /api/v1/notifications/in-app/{noticeId}/read`
7. Craves `GET /api/v1/customer/profile`
8. Craves `PUT /api/v1/customer/profile`
9. Craves `GET /api/v1/customer/addresses`
10. Craves `POST /api/v1/customer/addresses`
11. Craves `PUT /api/v1/customer/addresses/{addressId}`
12. Craves `DELETE /api/v1/customer/addresses/{addressId}`

## Deployment target

This module is deployed as a container image to the existing Craves web Azure Container App:

```text
Resource group: rg-craves-prodlow-centralindia
Container App: ca-craves-web-prodlow
ACR: cravesprodlowacr82121
APIM gateway: https://api.craves.in
Dashboard URL: https://ca-craves-web-prodlow.happysand-aedc7165.centralindia.azurecontainerapps.io
```

Browser calls use the dashboard same-origin route `/api/v1/*`. Next.js rewrites those requests to APIM. This avoids the browser CORS issue we hit during notification testing.

## Local setup

Create `.env.local` inside this folder:

```env
NEXT_PUBLIC_FIREBASE_API_KEY=
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=
NEXT_PUBLIC_FIREBASE_PROJECT_ID=
NEXT_PUBLIC_FIREBASE_APP_ID=
NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=
NEXT_PUBLIC_APIM_BASE_URL=https://api.craves.in
```

Then run:

```bash
npm install
npm run dev
```

Open:

```text
http://localhost:3000
```

## Azure DevOps pipeline

For deployment, use the existing working pipeline YAML from the Firebase auth test flow:

```text
azure-pipelines-firebase-auth-test.yml
```

That pipeline now builds:

```text
apps/api-test-dashboard/Dockerfile
```

and pushes the image to:

```text
cravesprodlowacr82121.azurecr.io/craves/api-test-dashboard:<BuildId>
```

Then it deploys the image to:

```text
ca-craves-web-prodlow
```

## Required Azure DevOps variables

These should already match the Firebase auth test pipeline pattern used earlier:

```text
AZURE_SERVICE_CONNECTION
NEXT_PUBLIC_FIREBASE_API_KEY
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN
NEXT_PUBLIC_FIREBASE_PROJECT_ID
NEXT_PUBLIC_FIREBASE_APP_ID
NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID
```

Do not paste private secrets into chat. Firebase Web Config values are public browser config values, but still keep them in Azure DevOps variables for consistent deployment.

## Firebase Console manual check

Firebase Authentication must allow the deployed Container App domain:

```text
ca-craves-web-prodlow.happysand-aedc7165.centralindia.azurecontainerapps.io
```

For local testing, also allow:

```text
localhost
```

## Expected successful test result

After OTP login and token exchange:

```text
Auth /me: HTTP 200
Notification Inbox: HTTP 200
Mark as Read: HTTP 204 or HTTP 200
Customer Profile GET: HTTP 200 or expected empty-profile response
Customer Profile PUT: HTTP 200 or HTTP 204 depending on backend response design
Customer Addresses GET: HTTP 200
Customer Addresses POST: HTTP 200 or HTTP 201
Customer Addresses PUT: HTTP 200 or HTTP 204
Customer Addresses DELETE: HTTP 200 or HTTP 204
```

`204 No Content` is success for update/delete style APIs.

## Customer payload note

The profile and address payloads are editable JSON in the dashboard. If backend validation expects different field names, use the API error shown in the dashboard test log to adjust the JSON body. This is intentional so the dashboard does not hardcode product decisions into the test UI.
