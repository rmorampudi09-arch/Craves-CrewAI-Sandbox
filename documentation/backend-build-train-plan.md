# Craves Backend Build Train Plan

## Scope
- Canonical backend runtime: Java 21, Spring Boot 3, Maven.
- Canonical backend modules: `services/auth-service`, `services/user-chef-service`, `services/catalog-service`, `services/order-service`, `services/subscription-service`, `services/integration-service`, `services/notification-service`.
- Platform dependencies: PostgreSQL/PostGIS, Redis, Azure Service Bus, Azure APIM, Azure Container Apps.
- Guardrail: no new Node.js backend services and no production routing to `apps/api`.

## Repository baseline validated
Root and `services/` structure were confirmed on branch `crewai/full-build-train-request` before changes. Service-level Maven descriptors were validated from:
- `services/auth-service/pom.xml`
- `services/order-service/pom.xml`
- `services/integration-service/pom.xml`
- `services/subscription-service/pom.xml`

All four confirmed Java 21 + Spring Boot 3 service baselines and showed existing production-hardening work already present in the shared branch.

## Current backend completion snapshot
### Auth service
Already includes:
- JWT exchange and verification support
- refresh session persistence
- Redis-backed token revocation hooks
- admin account intervention module
- internal admin RBAC module
- Flyway migrations through `V6__internal_admin_rbac.sql`
- unit coverage for abuse protection, admin RBAC, and repository behavior

### Order service
Already includes:
- checkout/cart orchestration
- delivery status consumer and customer delivery projection logic
- refund status consumer
- chef acceptance timeout workflow
- domain event outbox and notification outbox support
- admin dashboard and operational investigation endpoints
- Flyway migrations through launch policy and dynamic pricing work

### Integration service
Already includes:
- provider-neutral payment persistence with Razorpay-specific wiring
- refund workflow persistence and status publication
- delivery command orchestration and reconciliation
- multi-provider delivery abstractions
- subscription payment request/status integration
- admin readiness and investigation endpoints
- Flyway migrations through multi-provider pickup mapping

### Subscription service
Already includes:
- billing lifecycle state management
- occurrence generation
- payment status consumption
- capacity enforcement and projections
- chef-owned plan workflow and public schedule exposure
- Flyway migrations through meal snapshot and capacity changes

## Build-train execution order
1. Architecture lock and deprecation guardrails
2. Azure deployability alignment
3. Cross-service auth, session, Redis fail-closed validation
4. Admin capability closure on approved web surface
5. Integration-service provider standardization and replay safety
6. Service-by-service production completion validation
7. BFF contract verification against Spring services
8. Data, messaging, observability, and runbook hardening
9. CI/CD release gate simplification
10. Security review and rollback rehearsal

## Service-by-service backend build train
### 1. `services/auth-service`
Required exit criteria:
- verify Firebase to Craves JWT exchange paths for login, refresh, logout, and `me`
- validate Redis outage behavior for privileged revocation-sensitive flows
- confirm role grant and internal admin role endpoints are APIM/internal-only
- prove Flyway clean bootstrap and upgrade path in non-prod
- verify token revocation cleanup scheduling and retention

Recommended checks:
- unit tests around `CravesJwtService`, revocation publisher, and refresh invalidation
- integration test for Redis-backed revocation fail-closed behavior
- migration smoke using empty database and previous schema state

### 2. `services/user-chef-service`
Required exit criteria:
- validate customer profile/address CRUD and reverse-geocode safety
- validate chef application, evidence upload, review, and notice dispatch outbox behavior
- confirm document store references use restricted blob access only
- verify internal calls to auth and notification services remain contract-safe

Recommended checks:
- expand tests for document review transitions and notice outbox dispatch
- add migration bootstrap verification for address, favorites, and chef document schemas

### 3. `services/catalog-service`
Required exit criteria:
- confirm PostGIS bootstrap requirements and extension runbook
- validate nearby kitchen/menu discovery query correctness and index usage
- verify public/private endpoint split and JWT enforcement for chef mutations
- confirm media storage references and signed-access expectations

Recommended checks:
- migration test proving PostGIS-enabled bootstrap
- query-level tests for distance, radius, and unavailable kitchen filtering

### 4. `services/order-service`
Required exit criteria:
- validate cart -> checkout -> payment callback -> order creation flow
- confirm chef acceptance timeout/refund trigger safety
- verify outbox emission for order domain events and notification events
- validate delivery status and refund status consumers are idempotent
- confirm admin investigation and dashboard projections support support-team workflows

Recommended checks:
- end-to-end integration around duplicate delivery/refund events
- scheduler tests for timeout handling and stale work reclamation
- migration drift tests across `V1` through `V15`

### 5. `services/subscription-service`
Required exit criteria:
- verify plan workflow, publication state, and public schedule exposure
- validate recurring occurrence generation and billing state transitions
- confirm capacity reservation, skip handling, and incident projection logic
- prove payment status consumption is replay-safe and idempotent
- decide launch posture: launch-scope or feature-flagged

Recommended checks:
- integration tests for recurrence generation across timezone boundaries
- capacity conflict tests for paid vs skipped occurrences
- migration bootstrap verification through `V17`

### 6. `services/integration-service`
Required exit criteria:
- standardize active payment provider posture on Razorpay for launch
- keep provider-specific logic centralized here only
- verify webhook signature validation, replay defense, and inbox processing
- confirm refund state machine and downstream normalized events
- validate delivery adapter routing, reconciliation, and provider readiness endpoints

Recommended checks:
- sandbox replay/idempotency tests for Razorpay webhook sequences
- delivery outage simulation and retry/recovery behavior
- validation of event payloads against contracts in `contracts/events/`

### 7. `services/notification-service`
Required exit criteria:
- verify in-app notification inbox behavior and read-state transitions
- confirm recovery endpoints are protected and auditable
- validate ACS/FCM adapters, suppression policy, and retry handling
- verify preference-aware channel selection for customer/chef/admin events

Recommended checks:
- replay tests for failed delivery requests
- migration test for production delivery and recovery operations tables

## Database build-train requirements
### PostgreSQL / PostGIS
- Confirm PostgreSQL version compatibility with all Spring Boot services.
- Run clean bootstrap for every service-owned schema.
- Confirm `catalog-service` PostGIS extension enablement and geospatial index creation.
- Maintain forward-only Flyway migrations; rollback remains runbook-based.

### Required validation set
- `services/auth-service/src/main/resources/db/migration/`
- `services/user-chef-service/src/main/resources/db/migration/`
- `services/catalog-service/src/main/resources/db/migration/`
- `services/order-service/src/main/resources/db/migration/`
- `services/subscription-service/src/main/resources/db/migration/`
- `services/integration-service/src/main/resources/db/migration/`
- `services/notification-service/src/main/resources/db/migration/`

### Messaging persistence controls
- outbox tables must be retained as source of truth for async publishing
- consumers must use provider event IDs, aggregate/version checks, or dedupe keys
- cleanup policies must be documented per service

## Azure runtime alignment
### `infra/main.bicep`
Current branch state still deploys placeholder quickstart images for container apps. Before production sign-off, replace with CI-produced image contracts for:
- `apps/customer-web-next`
- `services/auth-service`
- `services/user-chef-service`
- `services/catalog-service`
- `services/order-service`
- `services/subscription-service`
- `services/integration-service`
- `services/notification-service`

### Runtime requirements
- Key Vault-backed secret references only
- PostgreSQL, Redis, Service Bus, ACR, APIM, Application Insights wiring
- probes, ingress, revision strategy, and rollback expectations per service
- explicit non-prod/prod parameter strategy

## Security build-train controls
- Deprecate `apps/api` as non-production and ensure no public ingress/APIM exposure.
- Enforce issuer/audience/role validation consistently across Spring services.
- Fail closed for privileged/admin operations if revocation state cannot be trusted.
- Never commit secret values; secret names only in docs and infra.
- Keep provider webhook verification and replay protection in `integration-service`.

## Manual actions required
- Provision Azure Redis / managed Redis equivalent.
- Provision non-prod PostgreSQL with PostGIS enabled.
- Register Razorpay webhook endpoints and confirm sandbox/prod credentials.
- Confirm delivery-provider launch list and credential onboarding.
- Approve mobile launch posture and subscription launch posture.
- Schedule security review, backup/restore rehearsal, and rollback drill.

## Secrets required by name only
- `POSTGRES_ADMIN_PASSWORD`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `REDIS_CONNECTION_STRING`
- `FIREBASE_PROJECT_ID`
- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `CRAVES_JWT_PRIVATE_KEY_PEM`
- `CRAVES_JWT_PUBLIC_KEY_PEM`
- `RAZORPAY_KEY_ID`
- `RAZORPAY_KEY_SECRET`
- `RAZORPAY_WEBHOOK_SECRET`
- `AZURE_SERVICEBUS_CONNECTION_STRING`
- `AZURE_STORAGE_CONNECTION_STRING`
- `AZURE_COMMUNICATION_SERVICES_CONNECTION_STRING`
- `FCM_SERVICE_ACCOUNT_JSON`
- `AZURE_MAPS_SUBSCRIPTION_KEY`

## Definition of done for backend build train
Backend is build-train ready when:
- all canonical Spring Boot services build on Java 21
- Flyway migrations bootstrap cleanly in non-prod
- Redis, PostgreSQL/PostGIS, Service Bus, and APIM wiring are validated
- auth/RBAC/revocation controls are consistent across services
- provider-specific payment and delivery logic remains centralized in `integration-service`
- contract, smoke, and resilience evidence is captured for release governance
