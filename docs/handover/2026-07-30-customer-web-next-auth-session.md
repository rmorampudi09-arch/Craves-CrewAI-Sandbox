# Craves Customer Web Next.js Authentication Session Handover

## 1. Status

Code complete on `feature/customer-web-next-auth-session`; no pipeline or Azure command has been run.

## 2. Parent dependency

This branch is stacked on `feature/customer-web-delivery-tracking-nextjs` and therefore depends on PRs #25, #26 and #27.

## 3. Goal

Provide production-style Firebase phone OTP login for the new Next.js customer web without exposing Craves tokens to browser JavaScript.

## 4. Existing backend reused

```text
POST /api/v1/auth/firebase/exchange
GET  /api/v1/auth/me
```

No Auth Service business logic or database schema is changed.

## 5. Browser flow

The browser requests and confirms OTP with the Firebase Web SDK. The Firebase ID token exists only briefly in component memory and is submitted to the same-origin session BFF.

## 6. Server flow

The Next.js route calls APIM, validates the Craves response, writes the access token into an HTTP-only cookie and returns only the allow-listed identity object.

## 7. Cookie

```text
name: craves_access_token
httpOnly: true
sameSite: lax
path: /
secure: true in production
maxAge: bounded to one hour
```

## 8. Cross-site protection

Session creation and logout compare the request `Origin` with the Next.js request origin and reject absent or mismatched origins.

## 9. Token privacy

No UI renders, copies, logs or stores Firebase or Craves tokens. No localStorage/sessionStorage token path exists.

## 10. Error privacy

Raw Auth Service response bodies are not relayed for failed exchanges.

## 11. Timeout

Auth exchange is cancelled after ten seconds. Identity lookup is cancelled after eight seconds.

## 12. Logout

Logout expires the HTTP-only access cookie through a same-origin POST.

## 13. Identity lookup

`/api/auth/me` forwards the access cookie to Auth Service and returns only identity ID, phone, email verification, display name, status and roles.

## 14. Session expiry

An Auth Service 401 clears the web access cookie.

## 15. Refresh limitation

The current approved backend refresh contract was not found. This module therefore does not invent one and does not retain a backend refresh token.

## 16. Password limitation

The customer password endpoint contract was not confirmed in the current Java backend. Password login remains deferred rather than implementing a conflicting contract.

## 17. Firebase dependency

The module pins `firebase` 12.16.0.

## 18. Build configuration

Firebase public web configuration is embedded into the image through Docker build arguments.

## 19. Runtime configuration

`CRAVES_API_BASE_URL` remains a Container App environment variable and points to the existing APIM `/api/v1` base.

## 20. Deployment guard

The deployment pipeline refuses replacement unless the four mandatory Firebase values exist and `confirmReplaceLegacyCustomerWeb=true`.

## 21. CI

`azure-pipelines-customer-web-next-auth-ci.yml` runs typecheck, tests, Next build and token-safety checks.

## 22. Test coverage

Contract tests validate response allow-listing, malformed identities, short sessions, same-origin return paths and public error text.

## 23. Manual Firebase work

Enable Phone provider, add final authorized domains and retain Firebase test numbers for non-production testing.

## 24. Manual Azure DevOps work

Create the six `NEXT_PUBLIC_FIREBASE_*` variables using values from the Firebase web application configuration.

## 25. Secrets warning

Never use a Firebase Admin service-account key as a `NEXT_PUBLIC_*` value.

## 26. Billing warning

Real OTP requests can incur Firebase/SMS usage and rate limits. Use test numbers during pipeline and manual validation.

## 27. Local setup

Copy `.env.example` to `.env.local`, fill the existing Firebase web configuration and start the Next.js app.

## 28. Expected local test

Send OTP, verify code, observe redirect, confirm `/api/auth/me` succeeds, logout, and confirm protected BFF requests return 401.

## 29. Logging rule

Do not add access tokens, Firebase ID tokens, cookies or complete upstream bodies to future logs.

## 30. Caching rule

Auth responses are `no-store` and must remain uncached.

## 31. APIM dependency

The Auth API is already exposed through the current APIM auth path. This module does not create another Auth API.

## 32. DNS dependency

The final customer-web domain must be added to Firebase Authorized domains before public login.

## 33. reCAPTCHA

Web phone auth uses Firebase `RecaptchaVerifier`. Do not disable verification outside Firebase test mode.

## 34. Accessibility

The OTP result message uses an aria-live region; form inputs have labels and OTP autocomplete hints.

## 35. Mobile separation

React Native phone auth uses native Firebase app verification and is implemented separately; this web module must not be copied directly into mobile.

## 36. Rollback

Rollback uses the existing explicit-image customer-web rollback pipeline and does not delete user or Auth Service data.

## 37. Database impact

None.

## 38. Service Bus impact

None.

## 39. Delivery-provider impact

None.

## 40. Cashfree impact

None.

## 41. Azure resource impact

No new Azure resource or SKU is created.

## 42. Key Vault impact

No new Key Vault secret is required.

## 43. Source-control impact

Public Firebase identifiers are represented only by placeholders; no real environment value is committed.

## 44. Merge order

Merge PR #25, then #26, then #27, then this module after all respective CI gates succeed.

## 45. Deployment order

Deploy backend consumer, configure APIM delivery route, validate delivery tracking, configure Firebase variables, then deploy the complete Next.js image.

## 46. Acceptance criteria

A customer can receive and confirm OTP, the server establishes an HTTP-only session, `/me` returns safe identity data, and no token is accessible from browser storage or page output.

## 47. Failure criteria

Do not merge when Next build fails, Firebase variables are absent from the deployment environment, token-safety grep fails, or the exact tested commit changes.

## 48. Future refresh module

Add refresh rotation only after Auth Service exposes an approved, tested refresh endpoint with reuse detection and revocation semantics.

## 49. Future password module

Add password sign-in only after the Java Auth Service contract and lockout/rate-limit requirements are documented.

## 50. Next module

Customer order history and order details reuse this HTTP-only session through server BFF routes.
