# Craves Cashfree Production Readiness Closeout

**Document date:** 15 August 2026  
**Scope:** Cashfree Payment Gateway production engineering closeout  
**Repository:** `rmorampudi09-arch/Craves-Build-platform`  
**Feature branch:** `feature/cashfree-production-closeout-20260815`  
**Pull request:** #241  
**Target:** `main`  
**Status meaning:** Engineering source complete; production money remains disabled until the manual merchant/runtime gates in this document pass.

This handover records the complete engineering work performed in the 15 August Cashfree closeout session, the relevant decisions recovered from earlier Craves work, exact source paths, deployment order, manual-only inputs, validation evidence, rollback, risks, and the remaining boundary between engineering completion and live-money approval. It deliberately does not contain Cashfree secrets, Azure secrets, OTPs, card data, CVVs, UPI PINs, or private credentials.

---

## Page 1 — Executive outcome

The Cashfree integration is no longer treated as “production ready because sandbox checkout works.” The closeout establishes an explicit financial-safety chain from customer checkout to Cashfree and back to Craves. The integration now fails closed when customer identity data is unsuitable for Cashfree, when provider amounts/currency differ from the Craves checkout, when return URLs leave the Craves domain in production, when webhook infrastructure is not reachable, when a webhook cannot be authenticated, when Cashfree cannot independently confirm a successful payment, or when the public merchant website does not meet the required go-live conditions.

The code does **not** enable live charging automatically. That is intentional. Source-controlled readiness is now separated from merchant-dashboard readiness, Azure runtime readiness, and an approved low-value live transaction. The final operator must still complete KYC/production enablement, install production credentials by secret reference, whitelist the production domain, register the webhook, configure public merchant identity, deploy the web in production Cashfree mode, and run the guarded activation stages.

<div style="page-break-after: always;"></div>

## Page 2 — What triggered this closeout

The immediate request was to close Cashfree production readiness today and then remove repeated back-and-forth: the engineering team should finish everything it can control, leaving only genuinely manual actions for the authorised operator. The starting repository already contained substantial Cashfree work: sandbox checkout, customer payment BFF routes, production safety switches, durable webhook processing, Cashfree production activation/rollback pipelines, subscription payment integration, and refund production controls.

The closeout therefore did not rewrite the payment architecture or invent a new payment model. It audited the existing implementation against current Cashfree go-live expectations and Craves’ existing architecture. Gaps were closed in place so the integration remains compatible with the current Order Service, Integration Service, customer web, subscription payment flow, APIM path, Azure Container Apps, and existing refund lifecycle.

<div style="page-break-after: always;"></div>

## Page 3 — Architecture boundary retained

The existing Craves architecture remains authoritative: Integration Service owns third-party payment-provider communication; customer web uses Craves backend/BFF routes rather than holding provider secrets; payment and refund state is persisted and reconciled server-side; provider events must be verifiable and idempotent; and customer/order ownership remains enforced before exposing payment resources.

No Node.js backend pattern was introduced. The payment work remains in Spring Boot/Java 21. The web additions remain in Next.js/TypeScript/Tailwind. APIM remains the public gateway. Azure Container Apps remain the deployed runtime referenced by the existing pipelines. This closeout therefore hardens the current architecture rather than creating a parallel payment stack.

<div style="page-break-after: always;"></div>

## Page 4 — Existing production safety switches

The production integration already had three important default-off controls:

- `CRAVES_CASHFREE_PRODUCTION_ACTIVATION_APPROVED=false`
- `CRAVES_CASHFREE_PRODUCTION_PAYMENT_EXECUTION_ENABLED=false`
- `CRAVES_CASHFREE_WEBHOOK_WORKER_ENABLED=false`

`PaymentProviderProperties` validates that production cannot start with missing credentials or a sandbox/test host. `CashfreePaymentExecutionInterceptor` blocks checkout order creation, payment verification, and subscription payment-order creation while production payment execution is disabled. These controls were retained and incorporated into stronger activation gates rather than weakened.

The safe operating principle is: a code deployment can exist in production without granting the ability to charge customers. Only the explicit activation pipeline can move from configuration to live execution, and even that pipeline now refuses the move unless all required backend, APIM and public-web readiness checks pass.

<div style="page-break-after: always;"></div>

## Page 5 — Checkout Create Order idempotency

Normal customer checkout Create Order now sends a stable Cashfree idempotency key derived from the Craves checkout UUID:

`x-idempotency-key = checkout.id()`

Source path: `services/integration-service/src/main/java/in/craves/integration/service/PaymentService.java`.

This matters when the provider call times out or a client retries: Craves must not accidentally generate multiple provider actions for the same logical checkout. The existing database-side “find by checkout” behavior is retained, but the provider request itself is now also explicitly idempotent.

Subscription payment creation already had a stable invoice-based idempotency key and continues to use it. The closeout therefore makes the ordinary checkout and subscription flows consistent on this safety property.

<div style="page-break-after: always;"></div>

## Page 6 — Customer phone normalization

A new shared class was added:

`services/integration-service/src/main/java/in/craves/integration/payment/CashfreeRequestSafety.java`

Firebase-authenticated customers commonly have phone values such as `+91 98765 43210`. Cashfree expects a valid Indian customer phone format for the payment order. The new logic strips formatting, converts a `91` country-code prefix to the domestic ten-digit number, and accepts only a valid ten-digit Indian mobile number for production.

Sandbox can still use the existing controlled fallback number if a test request lacks a phone. Production cannot. This distinction removes a dangerous “sandbox convenience leaks into production” behavior. A real production checkout with invalid phone data now fails with a clear `400` response instead of silently substituting test identity information.

<div style="page-break-after: always;"></div>

## Page 7 — Production return URL safety

The same `CashfreeRequestSafety` class now validates payment return URLs. Every return URL must be HTTPS. In production, a caller-provided return URL must remain on the configured Craves domain or a subdomain of that domain.

This prevents a compromised or malformed client request from sending a customer to an unrelated host after Cashfree checkout. The existing configured default remains the fallback. Sandbox retains flexibility for controlled testing, but production cannot use arbitrary external hosts.

The return URL is not used as proof of payment. It is only a browser navigation target. Final money state remains server-side, which prevents a forged browser redirect from creating a paid order.

<div style="page-break-after: always;"></div>

## Page 8 — Cashfree Create Order response reconciliation

Craves no longer trusts a structurally successful provider response without validating the financial identity returned by Cashfree. `CashfreeRequestSafety.requireCreateOrderResponse(...)` now requires:

- a non-empty `payment_session_id`;
- a non-empty `cf_order_id`;
- the returned provider `order_id` to match the Craves-generated order reference;
- the returned order amount to equal the server-owned Craves checkout amount;
- the returned currency to equal the Craves checkout currency.

This check is applied to normal customer checkout and subscription payment creation. A mismatch produces a controlled failure instead of handing an inconsistent provider session to the customer.

<div style="page-break-after: always;"></div>

## Page 9 — Server-owned amount principle

The payment amount continues to come from the server-owned checkout or subscription invoice. The browser does not choose the final amount sent to Cashfree. This closeout strengthens that principle by validating the amount again on the provider response, payment verification response, successful webhook, and Cashfree payment-by-ID confirmation.

No pricing formula, platform commission, delivery-fee rule, tax rule, or discount rule was invented here. Those remain owned by their existing Craves domains. The payment module is responsible only for ensuring that the amount it receives from the authoritative checkout/invoice is the same amount being processed and confirmed by Cashfree.

<div style="page-break-after: always;"></div>

## Page 10 — Payment verification endpoint hardening

`PaymentService.verifyPayment(...)` continues to call Cashfree server-side. It now also validates that the returned Cashfree order identity, order amount and currency match the stored Craves payment order before it can transition the local payment to `PAID`.

The verified provider status must be `PAID` before the Craves payment becomes paid. If the provider reports another state, Craves retains the existing local state rather than manufacturing success. The provider response is stored for reconciliation.

This keeps the customer-facing Verify button useful while preserving the rule that the browser cannot assert payment success on its own.

<div style="page-break-after: always;"></div>

## Page 11 — Duplicate paid-side-effect prevention

A significant closeout change is the `paidTransition` guard in `PaymentService`. Previously, a successful verification and a subsequent successful webhook could both attempt to notify Order Service that the checkout had been paid. The financial record might be idempotent, but duplicate downstream side effects are still undesirable.

The new logic calls the internal “payment paid” notification only when the stored Craves payment transitions from a non-paid state to `PAID`. If it is already paid, a repeated provider confirmation updates/reconciles state without re-triggering the order-paid action.

Webhook event uniqueness remains in place as an additional layer. The result is defense in depth across provider duplicate delivery, customer re-verification, and asynchronous/synchronous race conditions.

<div style="page-break-after: always;"></div>

## Page 12 — Successful webhook local reconciliation

When a Cashfree webhook reports `SUCCESS`, checkout payment processing now checks the webhook payment amount and currency against the stored Craves payment order before applying success. The stored attempt currency is no longer blindly hard-coded when the provider supplies the actual payment currency.

This local check is additional to, not a replacement for, the stronger provider-side payment-by-ID verification performed before dispatch. A mismatch is treated as a conflict/failure and remains visible to the durable webhook retry/dead-letter workflow instead of being silently accepted.

<div style="page-break-after: always;"></div>

## Page 13 — Cashfree provider re-verification

The pre-existing closeout hardening in `CashfreeWebhookProviderVerifier.java` is part of the final design. For a signed webhook claiming payment success, Integration Service calls Cashfree:

`GET /pg/orders/{orderId}/payments/{paymentId}`

and requires provider confirmation of order ID, payment ID, `SUCCESS` status, payment amount, payment currency, order amount and order currency. Only then is the event dispatched to checkout or subscription payment logic.

This directly implements the Cashfree go-live principle that an asynchronous success event should be followed by authoritative server-side status verification rather than being treated as final simply because it is signed.

<div style="page-break-after: always;"></div>

## Page 14 — Raw webhook signature integrity

Cashfree webhook authentication remains based on the exact raw body and timestamp. `CashfreeWebhookInboxService` verifies the HMAC before accepting the delivery. It intentionally does not parse and re-serialize the body before signature verification because even harmless JSON normalization can change the signed bytes.

The handler requires the provider timestamp, signature and webhook version, enforces accepted version values, checks timestamp skew, caps payload size at 1 MiB, and uses constant-time comparison for generated vs received signature bytes.

This closeout preserves that implementation and ensures the new APIM operation does not undermine it by rewriting the request body.

<div style="page-break-after: always;"></div>

## Page 15 — Durable webhook inbox

Accepted Cashfree callbacks are persisted in `payment_schema.cashfree_webhook_delivery`. The durable inbox protects against process crashes and decouples provider delivery acknowledgment from downstream processing. It retains a processing status, timestamps, attempts, next-attempt time, lock information and failure details.

A webhook is therefore not lost merely because an Order Service call or database operation is temporarily unavailable after Cashfree delivered it. The worker can retry based on persisted state. The production readiness endpoint exposes pending and dead-letter counts so activation can use real operational state rather than assuming an empty queue.

<div style="page-break-after: always;"></div>

## Page 16 — Multi-replica worker safety

The webhook claim query uses PostgreSQL `FOR UPDATE SKIP LOCKED`. This permits multiple Integration Service replicas to process work without all replicas claiming the same delivery. Stale processing locks can be reclaimed after the configured window.

This is important for the platform’s intended scale: scaling Integration Service horizontally should increase webhook throughput rather than introduce duplicate consumers. The worker still relies on event idempotency and payment-state guards because distributed systems require multiple layers, but the database claim is the first concurrency boundary.

<div style="page-break-after: always;"></div>

## Page 17 — Webhook retry and dead letter

Failed deliveries use bounded retry with backoff. Once the configured maximum attempt count is reached, a delivery becomes `DEAD_LETTER`. The production activation pipeline refuses to enable live payment execution while any Cashfree webhook dead-letter exists.

That behavior is deliberate: a dead-letter before go-live means the asynchronous financial path is not clean. The operator must understand and resolve the failure rather than enabling more real traffic over a known broken path.

The readiness endpoint also reports the pending count. Activation compares it with the expected count supplied by the operator so an unexplained backlog cannot be ignored.

<div style="page-break-after: always;"></div>

## Page 18 — APIM gap found during review

The existing customer-payment APIM configuration exposed customer payment-order operations but explicitly did not configure the Cashfree webhook operation. The default Cashfree webhook URL nevertheless pointed at the APIM path. This was a real production-readiness gap: the backend could be correct while the provider had no verified public route to reach it.

The closeout adds:

- `infra/apim/customer-payments/cashfree-webhook-policy.xml`
- `scripts/apim/configure-cashfree-webhook-apim.sh`
- `azure-pipelines-cashfree-webhook-apim.yml`

The operation is `POST /api/v1/payments/webhooks/cashfree`.

<div style="page-break-after: always;"></div>

## Page 19 — Webhook APIM authentication model

The provider webhook intentionally does **not** inherit the customer Bearer-token/JWT requirement. Cashfree cannot obtain a Firebase/Craves customer token to call a provider callback URL. Instead, the callback is authenticated cryptographically by the Cashfree HMAC headers at Integration Service.

The operation-level APIM policy therefore omits inbound `<base />` and does not contain `validate-jwt` or a Bearer requirement. It does require the presence of `x-webhook-timestamp`, `x-webhook-signature` and `x-webhook-version` before forwarding. These are edge hygiene checks; they are not considered the cryptographic verification itself.

<div style="page-break-after: always;"></div>

## Page 20 — APIM raw-body preservation

The webhook APIM policy does not read, parse, transform or reconstruct the request body. It sets the Integration Service backend and lets the original bytes pass through. This design protects the downstream HMAC calculation.

APIM also marks responses as non-cacheable and adds `X-Content-Type-Options: nosniff`. Missing mandatory provider headers receive `400` at the edge. A request containing the headers still must pass timestamp/version/signature validation inside Integration Service.

The public negative probe deliberately posts `{}` without provider headers and expects `400`. This proves that the route exists and that its expected edge policy is active without requiring or fabricating a valid Cashfree signature.

<div style="page-break-after: always;"></div>

## Page 21 — APIM configuration pipeline

`azure-pipelines-cashfree-webhook-apim.yml` is manual-only (`trigger: none`) and requires `confirmCashfreeWebhookExposure=true`. It uses the already-established Azure service connection `Craves-Dev-Service-Connection`.

The script resolves the live Integration Service FQDN, requires the latest revision to be ready/running, verifies `/actuator/health`, finds the one APIM API that owns `api/v1/payments`, creates/updates the webhook operation, applies the raw XML policy, re-reads the resulting configuration, checks for mandatory headers/no customer JWT policy, and performs the public negative probe.

No new billable Azure resource is provisioned by this pipeline; it modifies an existing APIM API.

<div style="page-break-after: always;"></div>

## Page 22 — Public Contact Us page

A real public route now exists at `/contact`. It publishes the established Craves support email `support@craves.in`, business email `contact@craves.in`, and Hyderabad location. It warns users never to send card number, CVV, UPI PIN, OTP, password or API secret to support.

Two values are intentionally runtime-configured rather than invented in source:

- `CRAVES_PUBLIC_SUPPORT_PHONE`
- `CRAVES_REGISTERED_BUSINESS_NAME`

If either is missing, the page visibly says production configuration is pending. This prevents engineering from guessing a private phone or legal entity name merely to satisfy an onboarding form.

<div style="page-break-after: always;"></div>

## Page 23 — Merchant identity configuration pipeline

`azure-pipelines-cashfree-customer-web-merchant-identity.yml` provides the one-time operator workflow for the two public merchant identity values. Parameters are passed to the shell through environment variables so punctuation in the registered business name cannot break shell quoting.

The pipeline validates that both values were replaced, updates the existing customer-web Container App environment, waits for the new revision to become ready, calls `/api/readiness/cashfree`, and finally checks `/contact` to prove both values are actually rendered publicly.

These values are public business data, not payment credentials. Cashfree client ID/secret are never accepted by this pipeline.

<div style="page-break-after: always;"></div>

## Page 24 — Terms of Service route

A customer-facing `/terms` route is now published. It describes the Craves marketplace model, secure account expectations, INR checkout, Cashfree hosted payments, server-side payment verification, chef acceptance/fulfilment, references the refund policy, prohibits abusive/fraudulent use, and directs support questions to the established support address.

The page does not define commissions, chef payout percentages, delivery-radius calculations, FSSAI policy or other product/business logic outside the existing architecture. The authorised business/legal owner must still review the wording before live activation; that review is a manual governance step, not an engineering guess.

<div style="page-break-after: always;"></div>

## Page 25 — Refunds & Cancellations page

`/refunds-cancellations` is based on the earlier Craves decision already approved in the project history rather than newly invented rules. The published behavior is:

- the chef response window begins after verified payment;
- the chef has up to 30 minutes to accept;
- explicit decline or timeout causes the affected chef-specific order to request a refund;
- delivery is not booked for an unaccepted failed chef-specific order;
- the refund uses the stored affected chef-specific order total;
- no new deduction is recalculated at refund time.

The page deliberately does not promise automatic refunds for scenarios that are not already defined by the product workflow.

<div style="page-break-after: always;"></div>

## Page 26 — Multi-chef refund behavior

The recovered approved Craves behavior for multi-chef checkout is preserved and documented publicly. If one chef-specific sub-order fails while another is accepted, only the failed sub-order is refunded and the accepted order continues. If every chef-specific sub-order fails, combined refunds equal the full paid checkout.

Craves does not automatically reassign the failed order to another chef because different chefs can have different dishes, prices, preparation times, ingredients, packaging and pickup locations. The customer may place another order after the affected order is refunded.

No change to the underlying existing refund engine was required in this Cashfree closeout; the public page aligns with the already implemented workflow.

<div style="page-break-after: always;"></div>

## Page 27 — Refund provider behavior

The existing refund-production module remains in force. Refund requests use Cashfree’s standard refund flow with deterministic identity/idempotency and cumulative refund guards. A provider acceptance is not confused with final settlement; Craves tracks provider status and supports reconciliation/retry/manual review for non-terminal or failed cases.

The Cashfree production rollback disables refund-provider execution and refund reconciliation together with payment execution. This is intentional because a payment incident may require stopping both new charges and automated refund-provider traffic while preserving all financial evidence.

<div style="page-break-after: always;"></div>

## Page 28 — Privacy page

`/privacy` now describes the principal categories of information Craves processes: account/identity data, saved and order-specific delivery addresses, cart/checkout/order/meal-plan/chef/delivery/support data, payment/refund references and statuses, and operational/security logs.

It explains that Cashfree handles hosted payment processing while Craves stores provider references and financial status required for order/refund reconciliation. It identifies the principal service categories already present in the project (Firebase, Cashfree, delivery providers, Azure and transactional communications) without exposing credentials.

Business/legal review remains a manual approval step before live activation.

<div style="page-break-after: always;"></div>

## Page 29 — Security page

`/security` provides clear customer guidance: Craves will not ask users to send a UPI PIN, card CVV, banking password, Firebase OTP/account password by email, or internal Craves provider credentials. It also states that Cashfree checkout is hosted and that Craves treats payment as final only after server-side verification or a verified signed provider event.

This page is not a substitute for internal security controls. It is a public trust/support page and an explicit place to direct customers who suspect unauthorised access.

<div style="page-break-after: always;"></div>

## Page 30 — Live Products & Pricing page

Cashfree’s website review expects product/service information and INR pricing. The closeout does not invent sample ₹ prices. Instead, `/products-pricing` reads the existing public Catalog Service endpoints, selects real active/available menu items whose currency is `INR`, and renders the current catalog price using Indian currency formatting.

If no qualifying real menu item can be loaded, the page renders `data-craves-live-pricing-status="unavailable"` and explicitly states that placeholder prices are not published. When at least one real item is rendered, it exposes `data-craves-live-pricing-status="ready"`.

This marker is consumed by the activation pipeline, so live payment execution cannot be enabled while the public product/pricing evidence is empty.

<div style="page-break-after: always;"></div>

## Page 31 — Footer navigation

The landing-page footer now links to actual public routes rather than muted planned labels:

- Products & Pricing
- Contact Us
- Privacy Policy
- Terms of Service
- Refunds & Cancellations
- Security

The rest of the landing visual system and Craves logo remain unchanged in intent. The change is functional navigation needed for production customer trust and Cashfree website review, not a redesign of the landing page.

<div style="page-break-after: always;"></div>

## Page 32 — Customer-web readiness endpoint

A non-secret route exists at `/api/readiness/cashfree`. It reports:

- customer-web service identity;
- build-time Cashfree mode;
- whether production eligibility is possible;
- legal/policy route names;
- whether support phone is configured;
- whether registered business name is configured.

It never returns the Cashfree client ID, client secret, internal smoke secret, payment payloads or private user data. It is used by the activation pipeline to validate the **deployed** web application rather than trusting repository intent.

`productionEligible` requires `NEXT_PUBLIC_CASHFREE_MODE=production` plus the two public merchant identity values.

<div style="page-break-after: always;"></div>

## Page 33 — Production CI pipeline

`azure-pipelines-cashfree-production-ci.yml` now performs both real project tests and static safety assertions. It uses Java 21 and runs Maven verification for Integration Service. It uses Node 24 and runs customer-web `npm ci`, lint, typecheck and tests.

It then asserts that the production kill switches still default false, the durable inbox still uses `SKIP LOCKED`, provider payment re-verification is still wired, checkout idempotency/phone/return URL/financial checks still exist, required public routes exist, merchant-identity and live-pricing gates exist, and APIM webhook policy/script/pipeline cannot silently lose their core security requirements.

The static assertions are intentionally redundant with unit tests because financial safety switches should fail loudly if removed during refactoring.

<div style="page-break-after: always;"></div>

## Page 34 — Unit tests added

`CashfreeRequestSafetyTest.java` covers the new shared request/reconciliation layer. Tests include:

- conversion of a Firebase-style `+91` Indian mobile to ten digits;
- rejection of an invalid phone in production;
- sandbox-only test fallback behavior;
- rejection of an external production return URL;
- rejection of amount mismatch;
- successful Create Order response validation;
- failure for an incomplete Create Order response.

Existing tests for `CashfreeWebhookProviderVerifier` continue to cover successful reconciliation, unconfirmed success, amount mismatch, provider-order amount mismatch and non-success callbacks.

<div style="page-break-after: always;"></div>

## Page 35 — Integration Service deployment

After CI passes, deploy the merged Integration Service with the existing `azure-pipelines-integration-service.yml`. This deployment must contain all Cashfree code changes before the webhook/APIM and activation pipelines are used.

Do not enable live payment execution as part of a generic Integration Service image deployment. The runtime safety switches remain off until the later activation pipeline stages. This separation allows the new code to be deployed and health-checked without immediately creating financial exposure.

Verify the latest revision is ready/running and `/actuator/health` succeeds before continuing.

<div style="page-break-after: always;"></div>

## Page 36 — Customer payment APIM prerequisite

The existing `azure-pipelines-customer-payments-apim.yml` configures the customer-facing payment-order API operations. It should be run/verified before the dedicated provider webhook pipeline so one APIM API owns `api/v1/payments`.

The customer operations retain their customer authentication/ownership model. The Cashfree webhook is added as a dedicated provider operation with a different authentication mechanism; the closeout does not remove customer JWT requirements from the normal order create/get/verify operations.

<div style="page-break-after: always;"></div>

## Page 37 — Customer web deployment

Deploy the customer web with `azure-pipelines-customer-web-next-delivery-tracking.yml`. For ordinary testing the pipeline can remain in sandbox mode. During the controlled Cashfree production cutover, run it with:

- `confirmReplaceCurrentCustomerWeb=true`
- `cashfreeMode=production`

The production activation pipeline later calls `/api/readiness/cashfree` and refuses payment execution unless the deployed build reports production mode. Therefore accidentally leaving the web in sandbox mode cannot coexist with an enabled backend payment execution state through the approved pipeline.

<div style="page-break-after: always;"></div>

## Page 38 — Public merchant identity manual input

After customer web deployment, run `azure-pipelines-cashfree-customer-web-merchant-identity.yml`. The authorised operator must enter two real public values:

- `publicSupportPhone`
- `registeredBusinessName`

The registered name must match the merchant/business identity approved for Cashfree. Do not use a made-up company name or private number simply to satisfy the gate. Set `confirmPublicMerchantIdentity=true`.

The pipeline writes the corresponding runtime environment values, waits for readiness, and verifies the Contact page. Because these values were not found in approved project source/history, this is a legitimate manual boundary rather than a missing engineering task.

<div style="page-break-after: always;"></div>

## Page 39 — Production credential manual input

Cashfree production credentials are manual secrets. The Integration Service Container App must have secret names:

- `cashfree-client-id`
- `cashfree-client-key`

The values must come from the production Cashfree Merchant Dashboard and must never be pasted into chat, committed to Git, stored in public pipeline YAML, or exposed to customer web/mobile code.

The activation pipeline checks the secret **names** and binds runtime variables by secret reference. It does not print secret values. Rotate credentials through the authorised Azure/Cashfree secret-management flow when required.

`CRAVES_INTERNAL_SMOKE_SECRET` must also exist as a secret Azure DevOps variable for the internal readiness call.

<div style="page-break-after: always;"></div>

## Page 40 — Webhook-stage activation

Run `azure-pipelines-cashfree-production-activation.yml` with:

- `activationStage=webhook`
- `confirmCashfreeProductionActivation=true`
- `apiVersion=2025-01-01`
- the final HTTPS Cashfree webhook URL.

Before changing runtime configuration, the pipeline sends a headerless negative probe to the webhook URL and requires HTTP `400`. If it receives `404`, `401`, a connection failure or another result, it stops and tells the operator to run/fix the APIM webhook pipeline.

The webhook stage then switches provider configuration to production, binds credentials by secret reference, enables webhook ingress and the durable worker, and **keeps payment execution/order creation disabled**.

<div style="page-break-after: always;"></div>

## Page 41 — Cashfree Merchant Dashboard manual gates

Before payment execution, the authorised merchant/operator must complete the Cashfree-side items that source control cannot perform:

- merchant KYC and bank verification;
- Payment Gateway production enablement;
- production API key generation/rotation;
- production domain whitelisting for the real checkout origin;
- webhook URL registration and required event/version selection;
- approved payment-method enablement;
- API/success-rate alert configuration;
- review of public website details.

The operator should use the same registered business name and support contact published by the Craves website. Any mismatch should be corrected before activation rather than bypassed.

<div style="page-break-after: always;"></div>

## Page 42 — Webhook dashboard configuration

Register the final production callback:

`https://api.craves.in/api/v1/payments/webhooks/cashfree`

unless the production APIM custom domain is intentionally changed and the activation parameter is updated to the exact final HTTPS URL.

Select the supported payment webhook version used by the current integration (`2025-01-01` is the configured primary version) and enable the required payment event types. Cashfree can retry callbacks; Craves’ idempotency/durable inbox is built for that behavior.

After registration, do not test by fabricating a provider signature. The live proof uses an approved real payment after execution is enabled.

<div style="page-break-after: always;"></div>

## Page 43 — Payment-execution activation

Run the same activation pipeline with `activationStage=payment_execution` only after the webhook stage and merchant-dashboard tasks are complete.

The pipeline requires all of the following before setting the execution switch true:

- provider environment is production;
- production activation is approved;
- webhook ingress and worker are enabled;
- payment execution is still currently disabled;
- Integration readiness says configuration is ready;
- webhook pending count equals the expected operator value;
- webhook dead-letter count is zero;
- customer web reports Cashfree production mode;
- public support phone and registered business name exist;
- required policy pages are live;
- Contact Us is complete;
- `/products-pricing` exposes at least one real INR-priced active item.

Only after all checks pass are `CRAVES_CASHFREE_PRODUCTION_PAYMENT_EXECUTION_ENABLED` and `CRAVES_PAYMENT_ORDER_API_ENABLED` set true.

<div style="page-break-after: always;"></div>

## Page 44 — Live payment proof

The first production transaction must be an explicitly approved low-value real payment. Do not use a fake/synthetic production payment and do not share payment secrets while collecting evidence.

Evidence should show the same transaction across:

1. Craves checkout/order reference;
2. Cashfree provider order/payment ID;
3. Integration Service payment order;
4. durable webhook delivery completed status;
5. Cashfree payment-by-ID verification result;
6. Craves customer order transitioned to paid exactly once;
7. expected chef-acceptance state began after verified payment;
8. customer-visible My Orders state is correct.

If any layer disagrees, stop and run rollback instead of continuing with more production payments.

<div style="page-break-after: always;"></div>

## Page 45 — Live refund proof

After the first payment reconciles, use the existing `azure-pipelines-refund-production-activation.yml` in its guarded stage order (`downstream`, `reconciliation`, then `provider_execution`) as required by the existing refund-production module. The provider-execution stage must not be enabled unless Cashfree production payment execution is already active and refund prerequisites are clean.

Trigger one approved low-value refund through the Craves refund path. Evidence must reconcile Cashfree refund ID/status, Integration refund state, Order Service refund state and customer notification. A provider request being accepted is not sufficient evidence of final refund completion; track the terminal provider state.

<div style="page-break-after: always;"></div>

## Page 46 — Financial rollback

`azure-pipelines-cashfree-production-rollback.yml` is the financial kill switch. To use it, set `confirmDisableCashfreeProduction=true`.

It sets the following controls false and then verifies the resulting revision:

- `CRAVES_PAYMENT_ORDER_API_ENABLED`
- `CRAVES_CASHFREE_PRODUCTION_PAYMENT_EXECUTION_ENABLED`
- `CRAVES_CASHFREE_WEBHOOK_INGRESS_ENABLED`
- `CRAVES_CASHFREE_WEBHOOK_WORKER_ENABLED`
- `CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED`
- `CRAVES_REFUND_RECONCILIATION_ENABLED`

Rollback preserves payments, webhook rows, refunds and audit evidence. It is a stop-new-financial-processing action, not data deletion. Use it immediately if the first live proof finds mismatched money state, repeated order transitions, provider verification failures, unexplained dead letters or incorrect public/merchant configuration.

<div style="page-break-after: always;"></div>

## Page 47 — Scaling and reliability assessment

The closeout does not introduce a single-node webhook assumption. Durable PostgreSQL inbox state plus `FOR UPDATE SKIP LOCKED` is compatible with multiple Integration Service replicas. Provider calls remain server-to-server, and customer web does not hold provider secrets. Retryable operations carry idempotency boundaries.

At very high concurrency, further performance work may still be required around database connection pools, provider rate limits, worker batch size, queue/backlog SLOs and external-service circuit breaking. Those are operational scale concerns rather than a reason to weaken financial correctness. The correct response to provider throttling is bounded retry/backpressure, not skipping reconciliation or signature verification.

<div style="page-break-after: always;"></div>

## Page 48 — Failure scenarios and operator response

Examples and required response:

- **APIM route returns 404:** run/fix `azure-pipelines-cashfree-webhook-apim.yml`; do not enable webhook stage.
- **Webhook signature fails:** confirm exact raw body preservation and production credential pair; do not bypass HMAC.
- **Webhook dead letter > 0:** investigate payload/provider/order downstream failure; payment execution stays blocked.
- **Public pricing unavailable:** publish at least one real active INR-priced catalog item; do not add fake ₹ values.
- **Contact phone/business name pending:** configure the real values with the merchant-identity pipeline.
- **Cashfree amount/currency mismatch:** treat as a financial incident; do not force local paid state.
- **First live payment inconsistent:** execute Cashfree production rollback and reconcile before any second live attempt.

<div style="page-break-after: always;"></div>

## Page 49 — Conversation-derived decisions and non-decisions

The closeout explicitly reused prior Craves decisions instead of repeatedly asking for information already supplied. The important recovered product decision is the 30-minute post-payment chef acceptance window with refund of the affected chef-specific stored order total on chef decline/timeout, partial refund only for failed chef-specific orders in a multi-chef checkout, full combined refund if all fail, no automatic replacement chef, and Cashfree standard refund handling.

The session also reused the established Azure service connection `Craves-Dev-Service-Connection`, existing production resource names, public support emails, APIM payment path and Cashfree version configuration.

Items **not** invented: production Cashfree credentials, legal registered business name, public support phone, pricing/commission formulas, delivery-radius rules, FSSAI rules, tax policy, additional cancellation scenarios, or live transaction values. Those remain operator/product/legal responsibilities where appropriate.

<div style="page-break-after: always;"></div>

## Page 50 — Final checklist, ownership and next step

### Engineering/source complete

- Cashfree checkout Create Order idempotency added.
- Production customer phone/return URL safety added.
- Create Order and verification financial reconciliation added.
- Successful webhook provider re-verification retained and guarded.
- Duplicate paid side effects suppressed.
- Subscription payment creation hardened consistently.
- Dedicated Cashfree webhook APIM source and pipeline added.
- Contact, Terms, Refunds/Cancellations, Privacy, Security, Products & Pricing pages added.
- Public web readiness endpoint added.
- Public merchant identity runtime pipeline added.
- Production CI and activation gates expanded.
- Existing rollback preserved.
- This handover and module README updated.

### Manual operator work still required

1. Merge/deploy the reviewed source and run `azure-pipelines-cashfree-production-ci.yml` successfully.
2. Deploy `azure-pipelines-integration-service.yml`.
3. Verify/run `azure-pipelines-customer-payments-apim.yml`.
4. Deploy customer web with `cashfreeMode=production` for controlled cutover.
5. Run `azure-pipelines-cashfree-customer-web-merchant-identity.yml` with the real public phone and registered merchant/business name.
6. Install/verify Azure secrets `cashfree-client-id`, `cashfree-client-key`, and secret pipeline variable `CRAVES_INTERNAL_SMOKE_SECRET` without exposing values.
7. Run `azure-pipelines-cashfree-webhook-apim.yml`.
8. Run Cashfree activation stage `webhook`.
9. Complete KYC, domain whitelist, production webhook configuration, payment methods, alerts and business/legal review in Cashfree/Craves governance.
10. Run activation stage `payment_execution` only when all gates are green.
11. Run one approved low-value production payment and reconcile it end to end.
12. Activate/refund-test the production refund path and reconcile one approved low-value refund.
13. If any financial inconsistency appears, run `azure-pipelines-cashfree-production-rollback.yml` immediately.

**Definition of done:** source is merged and CI-clean; all manual merchant/runtime gates pass; the first real payment and refund reconcile across Cashfree, Integration Service, Order Service and customer-visible state; rollback remains ready. Until then, the correct status is “engineering ready, live money not yet approved.”
