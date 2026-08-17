# Cashfree production hardening

This module separates production configuration, webhook processing and payment execution so one deployment cannot accidentally activate real customer charges.

## Durable payment path

```text
Customer checkout
→ Craves-owned checkout amount/currency
→ Cashfree Create Order with stable x-idempotency-key
→ validate Cashfree order identity, amount, currency and payment session
→ Cashfree hosted checkout
→ server verification and/or signed webhook
→ durable webhook inbox
→ Cashfree Get Payment by ID re-verification for SUCCESS
→ single Craves PAID transition
→ order/subscription fulfilment
```

Customer phone numbers are normalized from Firebase-style India formats before they are sent to Cashfree. Invalid production phone numbers fail closed rather than using test data. Production return URLs must remain on the configured Craves HTTPS domain. Checkout and subscription create-order responses are reconciled against Craves order identity, amount and currency before a payment session is trusted.

## Durable webhook path

```text
Cashfree HTTPS callback
→ APIM public webhook operation (no customer Bearer requirement; raw body preserved)
→ version/timestamp/idempotency/signature validation
→ durable payment_schema.cashfree_webhook_delivery inbox
→ multi-replica-safe claim
→ SUCCESS callbacks re-verified against Cashfree Get Payment by ID API
→ checkout or subscription payment processing
→ completed, retry, or local dead-letter state
```

The signature check uses the exact raw request body and timestamp. Payload size is capped at 1 MiB. Idempotency-key reuse with different content is rejected. A signed `SUCCESS` webhook is not sufficient by itself to finalize money state: the worker calls Cashfree `GET /pg/orders/{orderId}/payments/{cfPaymentId}` and requires matching order ID, payment ID, `SUCCESS` status, payment/order amount and payment/order currency before dispatching the event.

## Public Cashfree readiness routes

The customer web publishes:

```text
/contact
/terms
/refunds-cancellations
/privacy
/security
/products-pricing
/api/readiness/cashfree
```

`/products-pricing` reads real active INR-priced dishes from Catalog Service. It never fabricates example prices. The payment activation pipeline requires `data-craves-live-pricing-status="ready"`, which means at least one real active INR-priced dish is publicly renderable.

Contact Us reads the following non-secret runtime values:

```text
CRAVES_PUBLIC_SUPPORT_PHONE
CRAVES_REGISTERED_BUSINESS_NAME
```

They are intentionally not hard-coded. Configure them with `azure-pipelines-cashfree-customer-web-merchant-identity.yml` using the real public business values before production activation.

## Internal readiness API

```text
GET /internal/v1/payment-provider-readiness
X-Craves-Internal-Secret: <shared internal secret>
```

It reports configuration readiness, execution state, webhook backlog/dead-letter counts and blocker codes. Credential values and payment payloads are never returned.

## Financial switches

```text
CRAVES_CASHFREE_PRODUCTION_ACTIVATION_APPROVED=false
CRAVES_CASHFREE_PRODUCTION_PAYMENT_EXECUTION_ENABLED=false
CRAVES_CASHFREE_WEBHOOK_WORKER_ENABLED=false
```

Production payment creation and verification are rejected until payment execution is explicitly enabled. Sandbox remains usable for controlled testing.

## Engineering-side production sequence

Run these pipelines in this order after the current source is deployed/available in Azure DevOps:

1. `azure-pipelines-cashfree-production-ci.yml`
   - Java 21 Maven verification;
   - Next.js lint, typecheck and tests;
   - static guards for payment idempotency, provider reconciliation, webhook durability, public policy routes, live pricing, merchant identity and APIM source.
2. Deploy Integration Service containing the current Cashfree hardening.
3. Deploy customer web using `azure-pipelines-customer-web-next-delivery-tracking.yml` with `cashfreeMode=production` only when beginning the controlled production cutover.
4. `azure-pipelines-cashfree-customer-web-merchant-identity.yml`
   - enter the real public support phone;
   - enter the registered Cashfree merchant/business name;
   - set `confirmPublicMerchantIdentity=true`.
5. Ensure the existing customer-payment APIM operations are configured, then run `azure-pipelines-cashfree-webhook-apim.yml` with `confirmCashfreeWebhookExposure=true`.
6. In `azure-pipelines-cashfree-production-activation.yml`, run stage `webhook` with `confirmCashfreeProductionActivation=true` and the final HTTPS webhook URL.
   - it publicly probes the webhook route;
   - it binds production credentials by secret reference;
   - it enables ingress/worker only;
   - real payment creation remains disabled.
7. Complete the Cashfree Merchant Dashboard manual gates.
8. Run `azure-pipelines-cashfree-production-activation.yml` stage `payment_execution`.
   - it requires Integration Service readiness;
   - expected webhook pending count;
   - zero webhook dead letters;
   - customer web `cashfreeMode=production`;
   - public merchant phone/business name;
   - Contact, Terms, Refunds/Cancellations and Privacy pages;
   - real live INR product pricing;
   - only then enables production payment creation.
9. Execute one approved low-value live payment and one approved low-value refund, then reconcile all systems.

The activation pipeline requires secret variable `CRAVES_INTERNAL_SMOKE_SECRET`. Never commit or paste it into chat.

## APIM webhook source

```text
infra/apim/customer-payments/cashfree-webhook-policy.xml
scripts/apim/configure-cashfree-webhook-apim.sh
azure-pipelines-cashfree-webhook-apim.yml
```

The operation is:

```text
POST /api/v1/payments/webhooks/cashfree
```

It does not inherit the customer Bearer-token requirement. APIM checks that Cashfree timestamp/signature/version headers exist but does not parse or rewrite the request body. Integration Service remains the cryptographic authority for HMAC, timestamp, allowed version, idempotency and payload validation.

## Refund behaviour already approved for Craves

Current Craves order/refund behaviour remains:

- chef acceptance window begins after verified payment;
- explicit chef decline or acceptance timeout causes the affected chef-specific order to request a refund;
- refund uses the stored affected chef-specific order total and does not recalculate a new deduction;
- in a multi-chef checkout only failed chef-specific orders are refunded while accepted orders continue;
- if every chef-specific order fails, combined refunds equal the full paid checkout amount;
- no automatic replacement chef is selected;
- Cashfree STANDARD refund flow, deterministic references and cumulative refund guards remain in force.

No pricing, commission, delivery-radius or tax rule is defined by this Cashfree module.

## Rollback

`azure-pipelines-cashfree-production-rollback.yml` disables payment execution, webhook processing and refund provider workers. It never deletes payments, webhooks, refunds or audit evidence. Keep it available as the financial kill switch before running a real transaction.

## Manual-only Cashfree gates

These cannot be completed from source control and require the authorised merchant/operator:

- complete Cashfree merchant KYC, bank verification and Payment Gateway production activation;
- confirm the exact registered merchant/business name and public support phone used on the Craves website;
- create/rotate production API credentials and place them in Azure as `cashfree-client-id` and `cashfree-client-key` without exposing the values;
- configure `CRAVES_INTERNAL_SMOKE_SECRET` as a secret Azure DevOps variable;
- submit/confirm `https://craves.in` domain whitelisting;
- register the final production webhook URL and supported webhook version in Cashfree;
- select/enable approved payment methods and dashboard alerts;
- review the customer-facing Terms, Privacy and Refund/Cancellation wording as business/legal owner;
- run the explicitly approved low-value live payment/refund proof.

## Final live-money proof

Never use a fake or synthetic production payment.

1. create one real Craves checkout and pay through production Cashfree hosted checkout;
2. confirm Cashfree Dashboard shows the successful payment;
3. confirm Integration DB records provider order/payment identifiers and the durable webhook delivery completes without dead-lettering;
4. confirm Craves order becomes paid exactly once;
5. confirm the expected chef-acceptance state begins only after verified payment;
6. initiate one approved low-value refund through the Craves refund path;
7. confirm Cashfree refund state, Integration DB refund state, Order state and customer notification agree;
8. retain transaction IDs, timestamps and evidence references without recording credentials, card data, CVV, OTP or UPI PIN.

Cashfree production readiness is **engineering-complete** when the CI pipeline passes on the merged commit. It is **live-approved** only after the manual merchant/runtime gates and live-money proof also pass.
