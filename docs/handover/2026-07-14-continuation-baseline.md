# Craves Continuation Baseline — 14 July 2026

## Source of truth

The uploaded `8-7-2026 progress.txt` handover is the continuation baseline for this session. The final completed activity recorded there was:

- Subscription Service image deployed and healthy on Azure Container Apps.
- Dedicated Subscription Service Flyway history table applied manually.
- Customer and admin Subscription APIs registered in Azure API Management.
- Subscription business-flow testing intentionally deferred until route registration was complete.

## Repository and platform

- Repository: `rmorampudi09-arch/Craves-Build-platform`
- Branch: `main`
- Active backend: Java 21, Spring Boot 3, Maven
- Deployment: Azure Container Apps
- Gateway: Azure API Management
- Databases: `craves_auth_db`, `craves_business_db`, `craves_integration_db`
- Legacy `apps/api` Node.js code remains read-only reference and must not be extended.

## Current module status

| Module | Status | Immediate note |
|---|---|---|
| Authentication Service | Deployed foundation | Firebase exchange and Craves JWT flow available. |
| User/Chef Service | Deployed foundation | Customer/chef profiles and approval flow available; notification outbox hardening was started. |
| Catalog Service | Deployed foundation | Kitchen/menu/catalog foundations available. |
| Order Service | Deployed foundation | Cart, checkout, order lifecycle, and notification outbox foundation available. |
| Integration Service | Deployed payment foundation | Cashfree sandbox payment foundation exists; Service Bus payment event replacement remains pending. |
| Notification Service | Deployed foundation | In-app notification APIs and outbox consumers available. |
| Subscription Service | Deployed and APIM-routed | Health confirmed; business-flow test is pending. |

## Corrections persisted in Git during continuation

1. Added a service-specific Flyway history table default in Subscription Service application configuration:
   `subscription_service_flyway_schema_history`.
2. Added the same Flyway setting to the Subscription Service Azure DevOps deployment pipeline without changing universal pipeline variable names.
3. Added `scripts/configure-subscription-apim.sh` so the successful APIM operation setup is repeatable and no longer exists only as a Cloud Shell transcript.

## Next module

### Delivery Integration V1 foundation

Implement inside the existing `services/integration-service` module, not as a new backend service.

Planned scope:

- Canonical `DeliveryProviderAdapter` contract: quote, create, cancel, track, normalize webhook.
- Delivery schema migration in `craves_integration_db`.
- Delivery command idempotency and provider webhook inbox.
- Provider-neutral quote/routing service.
- Borzo adapter configuration boundary, disabled unless sandbox credentials are supplied.
- Internal APIs/events needed to accept a chef-accepted order and schedule delivery close to readiness.
- Tests, README update, and deployment variable documentation.

### Non-negotiable rule

Delivery must not be created at payment success. It is scheduled only after chef acceptance and close to the preparation-completion time. Each chef-specific order creates one delivery job.

## Manual intervention expected for Delivery V1

- Vendor sandbox account and API credentials must be created by the Craves owner.
- Secret values must be entered in Azure DevOps variables initially and later migrated to Azure Key Vault.
- Do not paste vendor API keys or webhook secrets into chat or commit them to Git.
- Creating a new Azure Service Bus namespace/queue is billing-sensitive and requires explicit approval before provisioning. Existing Service Bus resources should be reused when available.

## Pending validation before Delivery deployment

- Confirm whether a Borzo sandbox account/token already exists.
- Confirm whether the planned Azure Service Bus namespace and `delivery-command` queue already exist.
- Run a controlled Subscription API business-flow test later with valid customer/admin Craves tokens.
