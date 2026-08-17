# Subscription payment intents

This module receives `SUBSCRIPTION_PAYMENT_REQUESTED`, persists a durable invoice-owned payment intent, and exposes customer-authorized Cashfree hosted-checkout creation.

## Deliberate payment model

The module does **not** silently debit a customer. A Craves customer access token must prove ownership of the subscription before a provider order is created. Automatic recurring debit, mandate handling, grace period, retry schedule, credits and skip-meal policy remain explicit future product decisions.

## Flow

```text
Subscription invoice outbox
→ Service Bus SUBSCRIPTION_PAYMENT_REQUESTED
→ durable Integration inbox
→ one payment intent per invoice
→ authenticated customer provider-order request
→ Cashfree hosted payment session
→ signed durable Cashfree webhook inbox
→ payment-intent state update
→ transactional SUBSCRIPTION_PAYMENT_STATUS_CHANGED outbox
```

## APIs

```text
GET  /api/v1/subscription-payments/subscriptions/{subscriptionId}
GET  /api/v1/subscription-payments/invoices/{invoiceId}
POST /api/v1/subscription-payments/invoices/{invoiceId}/orders
```

All three require a Craves bearer token. Ownership is revalidated against Subscription Service. The subscription-scoped lookup validates ownership before invoice existence is disclosed and returns the latest payment intent for that owned subscription; while the asynchronous invoice has not arrived yet, it returns `404`. Browser/mobile clients never receive provider credentials.

## Safety defaults

```text
CRAVES_SUBSCRIPTION_PAYMENT_CONSUMER_ENABLED=false
CRAVES_SUBSCRIPTION_PAYMENT_STATUS_PUBLISHER_ENABLED=false
CRAVES_CASHFREE_WEBHOOK_WORKER_ENABLED=false
CRAVES_CASHFREE_PRODUCTION_PAYMENT_EXECUTION_ENABLED=false
```

## Consistency

- `invoice_id` is unique.
- Service Bus events are idempotent by `event_id`.
- Cashfree order IDs use the deterministic `CRVSUB_<invoice UUID>` form.
- Webhook amount and currency must match the invoice snapshot.
- Paid state is terminal.
- Status events use a transactional outbox with bounded retries and local dead-letter state.

## Local validation

```bash
cd services/integration-service
mvn -B -ntp verify
```

## Activation later

1. Apply Flyway V104 with all workers false.
2. Configure the Service Bus subscription using the guarded pipeline.
3. Deploy and validate the request consumer with a synthetic event.
4. Validate customer ownership for GET and POST.
5. Enable Cashfree webhook worker and test sandbox payment.
6. Deploy Subscription payment-status consumer.
7. Enable status publisher only after downstream validation.
