# Craves Mobile (React Native)

This mobile workspace is the approved native mobile target for Craves.

## Status
- Stack: React Native + TypeScript
- Auth: Firebase client auth + backend token exchange
- API access: Craves Spring Boot services via BFF/API gateway base URL
- Push: Firebase Cloud Messaging integration scaffolded
- Store publishing: PENDING MANUAL ACTION

## Scope implemented
- React Native app scaffold documentation
- Firebase environment contract
- API client contract
- Auth/session token exchange service
- Mobile test plan and smoke test placeholders
- Explicitly no Flutter references

## Required manual actions
- Create or confirm actual React Native app shell if not already present
- Provision Firebase mobile apps and runtime config values
- Provide APIM/base API URL
- Configure FCM/APNs credentials
- Complete Android/iOS signing and store release flows

## Environment variables
Create a runtime env strategy compatible with your chosen RN setup:
- `CRAVES_API_BASE_URL`
- `CRAVES_FIREBASE_API_KEY`
- `CRAVES_FIREBASE_AUTH_DOMAIN`
- `CRAVES_FIREBASE_PROJECT_ID`
- `CRAVES_FIREBASE_APP_ID`
- `CRAVES_FIREBASE_MESSAGING_SENDER_ID`

## Store actions
- Apple App Store: PENDING MANUAL ACTION
- Google Play: PENDING MANUAL ACTION
