# Subscription sandbox runtime activation

This package activates the completed Craves meal-subscription runtime in the prod-low environment while keeping Cashfree strictly in SANDBOX mode.

## What becomes active

Subscription Service:
- capacity projection scheduler
- occurrence generator
- billing generator
- billing Service Bus publisher
- payment-status Service Bus consumer
- recurring-order request worker
- recurring-order Service Bus publisher

Integration Service:
- Cashfree signed webhook ingress
- durable webhook worker
- SUBSCRIPTION_PAYMENT_REQUESTED consumer
- SUBSCRIPTION_PAYMENT_STATUS_CHANGED publisher
- payment-order API execution in Cashfree sandbox only

Order Service:
- SUBSCRIPTION_ORDER_REQUESTED consumer
- order-created callback worker back to Subscription Service

## Business boundary

This package does not create an automatic recurring debit or Cashfree mandate. Billing generation creates a durable invoice/payment request. The customer must still authorize the hosted Cashfree payment for the invoice. Production Cashfree activation/execution stay disabled.

## Cashfree safety state

The activation script explicitly sets:

- `PAYMENT_PROVIDER_ENVIRONMENT=sandbox`
- `CRAVES_CASHFREE_PRODUCTION_ACTIVATION_APPROVED=false`
- `CRAVES_CASHFREE_PRODUCTION_PAYMENT_EXECUTION_ENABLED=false`
- `PAYMENT_PROVIDER_SANDBOX_BASE_URL=https://sandbox.cashfree.com`
- `PAYMENT_PROVIDER_API_VERSION=2025-01-01`

It never writes Cashfree credentials. `PAYMENT_PROVIDER_CLIENT_ID` must already be bound on Integration Service and `PAYMENT_PROVIDER_CLIENT_KEY` must already be a Container App secret reference.

## Internal callback security correction

Order Service posts order-created acknowledgements to:

`POST /internal/v1/subscription-occurrences/{occurrenceId}/order-created`

The endpoint already validates `X-Craves-Internal-Secret`. The Subscription Spring Security chain now permits only that POST path to reach the controller without a Bearer token so the controller can perform its internal-secret check. The endpoint is not added to APIM.

Regression test:

`services/subscription-service/src/test/java/in/craves/subscription/order/OccurrenceOrderInternalSecurityTest.java`

## APIM additions

Authenticated customer operations:

- `GET /api/v1/subscription-payments/invoices/{invoiceId}`
- `POST /api/v1/subscription-payments/invoices/{invoiceId}/orders`

Cashfree provider callback:

- `POST /api/v1/payments/webhooks/cashfree`

The webhook APIM policy has no Bearer guard and does not use `set-body`; the Integration Service verifies Cashfree timestamp/signature against the exact raw request body before durable inbox insertion.

## Existing Azure resources reused

- Resource group: `rg-craves-prodlow-centralindia`
- Subscription Container App: `ca-craves-subscription-service-p`
- Integration Container App: `ca-craves-integration-service-pr`
- Order Container App: `ca-craves-order-service-prodlow`
- Service Bus namespace: `sb-craves-prodlow-l3ing6`
- Topic: `craves-domain-events`
- APIM: `apim-craves-prodlow-l3ing6`

No new Service Bus namespace, APIM service, Container App, PostgreSQL server, Redis resource, or Key Vault is provisioned. The activation does create/reconcile three topic subscriptions/rules inside the existing Service Bus namespace.

## Service Bus topology

- `integration-service-subscription-payment-requested`
  - filter: `SUBSCRIPTION_PAYMENT_REQUESTED`
- `subscription-service-payment-status-changed`
  - filter: `SUBSCRIPTION_PAYMENT_STATUS_CHANGED`
- `order-service-subscription-order-requested`
  - filter: `SUBSCRIPTION_ORDER_REQUESTED`

All named rules are idempotently reconciled and `$Default` is removed so unrelated domain events are not consumed.

## Required existing secret bindings

Do not paste secret values into pipelines, Git, screenshots, or chat.

Integration Service:
- `PAYMENT_PROVIDER_CLIENT_ID`
- `PAYMENT_PROVIDER_CLIENT_KEY` as `secretRef`

Subscription Service:
- `CRAVES_INTERNAL_SERVICE_SECRET` as `secretRef`

Order Service:
- `CRAVES_INTERNAL_SERVICE_SECRET` as `secretRef`

The preflight verifies presence without printing values. If both internal-secret references use Key Vault URLs, it also verifies that they point to the same Key Vault secret URL.

## Source CI

Run:

`azure-pipelines-subscription-sandbox-runtime-ci.yml`

It validates shell/XML assets and runs Java 21 Maven `clean verify` for Subscription, Integration and Order services.

## Deployment before runtime activation

After this package is merged to `main`, deploy all three participating service images from that same merged source before activating flags:

1. `azure-pipelines-subscription-service.yml`
2. `azure-pipelines-integration-service.yml`
3. `azure-pipelines-order-service.yml`

Use `AZURE_SERVICE_CONNECTION=Craves-Dev-Service-Connection`.

This ensures the running images actually contain the callback security correction and the already-developed subscription payment/order workers.

## APIM rollout order

1. `azure-pipelines-subscription-payments-apim-ci.yml`
2. `azure-pipelines-subscription-payments-apim.yml` with `confirmApimWrite=true`
3. `azure-pipelines-subscription-payments-apim-status.yml`

Rollback, only when required:

`azure-pipelines-subscription-payments-apim-rollback.yml` with `confirmApimRollback=true`.

## Runtime rollout order

1. `azure-pipelines-subscription-sandbox-runtime-preflight.yml`
2. `azure-pipelines-subscription-sandbox-runtime-activate.yml` with `confirmSandboxActivation=true`
3. `azure-pipelines-subscription-sandbox-runtime-status.yml`

Activation is downstream-first:

1. Integration webhook worker + payment-request consumer, while payment execution/status publisher remain off.
2. Subscription payment-status consumer.
3. Order subscription-order consumer + callback worker.
4. Integration payment-status publisher + sandbox payment-order API.
5. Subscription publishers, capacity projection, occurrence generation, billing generation and recurring-order request worker.

Every Container App update receives an activation marker and must produce the exact new Healthy/Ready revision before the next stage proceeds. Default observation window is 25 minutes per revision because Integration Service has previously demonstrated slow Azure Container Apps readiness.

## Runtime status evidence

`azure-pipelines-subscription-sandbox-runtime-status.yml` verifies:

- all three apps latest == latestReady, Healthy and HTTP health UP
- every requested worker flag is true
- Cashfree environment is sandbox
- both Cashfree production flags are false
- Service Bus rules are correct
- subscription-specific dead-letter counts are zero
- subscription-payment APIM routes/policies are valid
- anonymous subscription-payment request returns 401
- unsigned Cashfree webhook reaches the backend validator and returns 400
- consumer startup logs when present in the available console-log tail

An authenticated hosted-payment E2E remains a later test if a Craves access token is not available during activation.

## Rollback

Run:

`azure-pipelines-subscription-sandbox-runtime-rollback.yml`

with `confirmSandboxRollback=true`.

Rollback order disables upstream generation/publishing first, then provider execution/status publication, then order consumers/callbacks, then payment-status consumption, and finally webhook/payment-request consumption. It retains APIM routes, Service Bus topology and durable database evidence.

## Manual steps required

- Azure DevOps: run the listed pipelines and keep `AZURE_SERVICE_CONNECTION=Craves-Dev-Service-Connection`.
- Secrets: if preflight reports a missing secret binding, wire the existing value through Azure Key Vault/Container App secret references. Never paste the value into chat.
- Cashfree dashboard: no production KYC/go-live action is required for this sandbox rollout. A real sandbox checkout later requires the existing sandbox merchant credentials and a Craves customer access token.
- No DNS, mobile-store, Firebase Console, new Azure resource, or production Cashfree action is required by this activation package.

## Stop rule

If CI, deployment, APIM status, preflight, revision health, Service Bus, dead-letter, webhook smoke, or runtime status fails, do not continue to the next stage. Preserve the output and use the controlled rollback only if execution flags were already enabled.
