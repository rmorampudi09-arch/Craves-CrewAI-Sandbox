# Subscription payments APIM package

This package exposes the customer-authorized subscription payment flow and Cashfree payment webhook through the existing Craves APIM instance.

## Routes

Authenticated customer routes:

- `GET /api/v1/subscription-payments/invoices/{invoiceId}`
- `POST /api/v1/subscription-payments/invoices/{invoiceId}/orders`

Provider callback route:

- `POST /api/v1/payments/webhooks/cashfree`

The customer routes require a Bearer access token at APIM and are re-authorized by Integration Service ownership logic. The Cashfree webhook is intentionally not Bearer-authenticated because Cashfree authenticates with webhook timestamp/signature headers; Integration Service validates those headers against the exact raw body.

## Files

- `authenticated-policy.xml` — Bearer guard, Integration backend, no-store/nosniff response headers.
- `cashfree-webhook-policy.xml` — provider-public raw-body pass-through, Integration backend, no-store/nosniff response headers.
- `scripts/apim/configure-subscription-payments-apim.sh` — guarded idempotent apply; adopts an existing operation when method + URL template already exists.
- `scripts/apim/status-subscription-payments-apim.sh` — read-only route/policy validation.
- `scripts/apim/rollback-subscription-payments-apim.sh` — removes only the three named method/template routes and retains API containers.

## Pipelines

Static/no-write:

`azure-pipelines-subscription-payments-apim-ci.yml`

Guarded apply:

`azure-pipelines-subscription-payments-apim.yml`

Set runtime parameter `confirmApimWrite=true` and pipeline variable `AZURE_SERVICE_CONNECTION=Craves-Dev-Service-Connection`.

Read-only status:

`azure-pipelines-subscription-payments-apim-status.yml`

Controlled rollback:

`azure-pipelines-subscription-payments-apim-rollback.yml`

Set `confirmApimRollback=true` only when rollback is required.

## Cashfree raw-body rule

Do not add `set-body`, JSON parsing/rewrite, Liquid transformation, or any other request-body transformation to the Cashfree webhook inbound policy. Cashfree signature verification depends on the timestamp concatenated with the exact raw body received from Cashfree.
