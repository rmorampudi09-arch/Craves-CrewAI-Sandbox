# Craves Documentation Evidence Ledger

Evidence date: **14 August 2026**  
Repository: **rmorampudi09-arch/Craves-Build-platform**  
Product evidence snapshot: **main @ 3225f7fa531a6b07185fb5e034f7930f8bd8b571**

## Evidence by area

### Platform
Craves is a two-sided food marketplace connecting customers with home-chef supply. The repository contains a newer Next.js customer/chef web application, seven Spring Boot services, APIM guidance, legacy web/admin/API paths, infrastructure assets and milestone handovers.

The architectural direction is a front-door pattern: clients authenticate, use a same-origin Backend-for-Frontend or APIM, and reach domain services rather than databases directly. Customer and chef journeys share identity while privileged capabilities remain role-gated.

Sources: `README.md; apps/customer-web-next/README.md; services/*/README.md; infra/apim/README.md; docs/handover/`

### Frontend
The primary customer-facing web implementation is apps/customer-web-next, a Next.js 14 App Router application containing customer, chef and newer admin/support modules.

The browser uses same-origin BFF handlers, secure HTTP-only token cookies and APIM at /api/v1. Firebase phone OTP is used for the current web sign-in flow, and backend responses remain authoritative for price, availability and order state.

Sources: `apps/customer-web-next/README.md; apps/customer-web-next/src/app/; apps/customer-web-next/src/components/; apps/customer-web-next/src/features/`

### Backend
The active backend target is seven Spring Boot services: auth, user-chef, catalog, order, subscription, integration and notification.

Services own focused business domains, validate JWT role/ownership, expose health endpoints, evolve schema through Flyway where documented, and isolate provider-specific behavior behind integration/notification boundaries.

Sources: `services/auth-service/README.md; services/user-chef-service/README.md; services/catalog-service/README.md; services/order-service/README.md; services/subscription-service/README.md; services/integration-service/README.md; services/notification-service/README.md`

### Auth
Authentication proves who the caller is; authorization decides what that caller may do. Craves uses phone OTP for the current web and JWT-based authorization in backend services, with Entra External ID/PKCE documented for the mobile milestone.

Secure HTTP-only cookies keep web token state away from ordinary browser JavaScript. Backend services evaluate roles such as USER, CHEF and ADMIN and may also enforce object ownership. Mobile milestone guidance stores refresh credentials in Keychain/Keystore and access tokens in memory.

Sources: `apps/customer-web-next/README.md; services/user-chef-service/README.md; services/order-service/README.md; docs/handover/2026-07-30-customer-mobile-auth-foundation.md`

### Chef
Chef mode is the supply side of the marketplace: onboarding, verification, menus, kitchen/order operations and earnings.

user-chef-service persists chef profiles and verification state. The frontend can switch context, but the server remains the authority: showing Chef mode never grants a CHEF role by itself.

Sources: `services/user-chef-service/README.md; apps/customer-web-next/src/components/chef/; apps/customer-web-next/src/components/chef-mode/`

### Catalog
Catalog is the source of browseable meal and cuisine information used by discovery and commerce validation.

catalog-service is a Spring Boot/Flyway service with JWT controls. The web contains discovery, meals, search and selector modules. Administrative catalog operations are described by APIM guidance, including bulk pause/import and SHA-256 integrity checks.

Sources: `services/catalog-service/README.md; apps/customer-web-next/src/components/discovery/; apps/customer-web-next/src/components/meals/; infra/apim/README.md`

### Checkout
Checkout turns a basket into a validated purchase attempt and combines address, authoritative quote, order creation and hosted payment.

The browser does not decide the payable total. BFF/API calls obtain current backend state; Cashfree payment_session_id is used for hosted payment entry, while backend order/payment paths also document Razorpay acceptance and signed callbacks.

Sources: `apps/customer-web-next/README.md; apps/customer-web-next/src/components/cart/; apps/customer-web-next/src/components/checkout/; services/order-service/README.md`

### Order
Order management is the transactional spine of Craves: create, retrieve, status, cancellation/refund, delivery state, ETA/shortage, proof and administrative controls.

order-service documents idempotency, structured Problem Details errors, reconciliation, provider integration and controlled multi-database routing. Supported roles include customer, chef, admin and delivery-partner contexts with ownership enforcement.

Sources: `services/order-service/README.md; apps/customer-web-next/src/components/order-history/; apps/customer-web-next/src/components/order-tracking/`

### Payment
Payments are separated from raw card handling so the Craves UI can use provider-hosted entry while the backend protects order/payment state.

The web documents Cashfree hosted payment sessions. order-service verifies Razorpay callbacks with HMAC SHA-256 over the raw body using X-Razorpay-Signature, and integration-service documents normalized payment-link operations.

Sources: `apps/customer-web-next/README.md; apps/customer-web-next/src/lib/cashfree/; services/order-service/README.md; services/integration-service/README.md`

### Subscription
Subscriptions support repeat service and entitlements on top of one-off ordering.

subscription-service migrations cover plans, subscriptions, payment identifiers/tokens, idempotency, request fingerprints and usage counters. The latest observed main commit adds shared entitlements and gated services.

Sources: `services/subscription-service/README.md; apps/customer-web-next/src/components/plans/; apps/customer-web-next/src/components/subscriptions/; commit 3225f7fa`

### Notification
Notifications centralize email, SMS and push so domain services do not each implement vendor logic.

notification-service documents Azure Communication Services, FCM, preferences, device tokens, optional Service Bus topic craves-domain-events, event-id idempotency, correlation propagation and a seven-day resend worker.

Sources: `services/notification-service/README.md; apps/customer-web-next/src/components/notifications/`

### Location
Location/address features help a customer provide a deliverable address while retaining manual fallback when device permission or geocoding fails.

The web contains location/maps/address-dialog modules. Maps are an external dependency; UI convenience does not replace backend serviceability and address validation.

Sources: `apps/customer-web-next/src/components/location/; apps/customer-web-next/src/components/maps/; apps/customer-web-next/src/lib/location/; docs/handover/2026-08-06-customer-chef-precise-ui-address-dialog.md`

### Apim
Azure API Management is the intended policy and routing front door between clients/BFFs and backend services.

The APIM README describes craves-customer without APIM subscription requirement and craves-admin with subscription required, JWT validation against Entra and operation-level role checks from named values. Its cited OpenAPI path was not resolvable on the reviewed main snapshot and is documented as a gap.

Sources: `infra/apim/README.md`

### Integration
integration-service is the provider boundary so third-party APIs do not leak provider-specific rules into every domain.

Repository documentation covers Google Maps fallback, Calendar creation, Gmail drafts, user grants, contact aliasing, pseudonymized audit, Razorpay requirements/payment links and deterministic disabled-by-default stubs.

Sources: `services/integration-service/README.md; services/integration-service/**/README.md`

### Privacy
Privacy capability includes consent preferences plus recent customer data export/deletion implementation on main.

Recent commits record centralized consent enforcement, a consent-center UI/backend wiring, customer export and deletion workflows. Exact legal retention periods are not invented where source is silent.

Sources: `commit 188385e; commit 93ebfa4; commit 5dd7971; apps/customer-web-next/`

### Ai
Craves includes a governed semantic meal concierge for assisted discovery rather than an authority that can bypass commerce rules.

Recent main history and apps/customer-web-next/docs/signalr-ai-concierge.md describe SignalR semantic concierge work. Recommendations still rely on governed product data and ordinary catalog, quote, authorization and order paths for transactions.

Sources: `apps/customer-web-next/docs/signalr-ai-concierge.md; commit 71e795b`

### Admin
Administrative tooling lets authorized staff review marketplace state unavailable to ordinary customers and chefs.

The repository contains legacy apps/admin plus newer customer-web-next admin/support/chef-review modules. APIM guidance places admin operations in a subscription-protected product with additional role checks.

Sources: `apps/admin/README.md; apps/customer-web-next/src/features/admin/; apps/customer-web-next/src/features/chef-review/; infra/apim/README.md`

### Mobile
Native mobile is proven mainly through milestone identity/API/CI contracts rather than a clearly complete runnable app on the reviewed main snapshot.

The 30 July handover specifies Entra External ID, authorization code with PKCE/MFA, refresh token in Keychain/Keystore, access token in memory, APIM bearer calls, one silent refresh after 401, then interactive sign-in. A short-lived React Native bootstrap branch was referenced.

Sources: `docs/handover/2026-07-30-customer-mobile-auth-foundation.md; docs/handover/2026-07-30-mobile-ci-foundation.md`

### Devops
The repository has broad CI/CD references across backend, frontend, mobile, APIM, data, privacy, security, smoke and release controls.

customer-web-next README describes ACR build/push, Container App revision deployment, readiness checks and restoration of the prior image/revision on failed readiness. Pipeline names not resolvable at their cited path are classified as reference-only instead of assigned invented triggers.

Sources: `apps/customer-web-next/README.md; repository YAML search; docs/handover/`

### Security
Security is layered: identity proof, token validation, role/ownership checks, secret isolation, provider-signature checks and deployment gates address different failure modes.

APIM adds gateway policy, services re-check authorization, web uses secure cookies, mobile milestones use secure token storage, Razorpay callbacks use HMAC verification, and local starter secrets are prohibited in deployed Azure profiles where documented.

Sources: `infra/apim/README.md; services/user-chef-service/README.md; services/order-service/README.md; apps/customer-web-next/README.md`

### Errors
A useful failure model separates user input/state conflicts, identity/permission failures, dependency outages, data faults and deployment/configuration incidents.

order-service documents Problem Details fields and codes including ORDER_NOT_FOUND, ORDER_IDEMPOTENCY_CONFLICT, INVALID_STATUS, CANCELLATION_NOT_ALLOWED, REFUND_FAILED, UNAUTHORIZED, FORBIDDEN, BACKEND_CHANGE_DISABLED and BACKEND_UNAVAILABLE. Diagnostics should start from request/correlation evidence.

Sources: `services/order-service/README.md; services/notification-service/README.md; apps/customer-web-next/README.md`

### Legacy
Older web/admin/Node API paths remain useful for migration history but are not automatically treated as the preferred current architecture.

The root README and apps/web/apps/admin READMEs show earlier SPA/API patterns and historical Azure preview URLs. Newer documentation centers on customer-web-next, Spring Boot services and APIM.

Sources: `README.md; apps/web/README.md; apps/admin/README.md`

## Explicit repository gaps
- APIM README cites infra/apim/craves-openapi.yaml, but that literal artifact was not resolvable on main during this evidence snapshot.
- customer-web-next README names azure-pipelines-customer-web-next.yml, but that literal root path was not resolvable; deployment behavior described by the README is retained while trigger/variable details are not invented.
- Native mobile has strong milestone identity/API/CI evidence, but a clearly complete runnable React Native application was not established on the reviewed main snapshot.
- Legacy preview URLs prove historical deployment references rather than current production routing.
- Commercial values such as commissions, payout percentages, subscription pricing, catering thresholds and SLAs are not fabricated where source is silent.
