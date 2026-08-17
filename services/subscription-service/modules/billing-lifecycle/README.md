# Subscription billing lifecycle

This module creates immutable weekly or monthly billing cycles for active Craves meal subscriptions and publishes `SUBSCRIPTION_PAYMENT_REQUESTED` through a transactional outbox.

## Flow

```text
eligible customer_subscription.next_billing_date
→ multi-replica PostgreSQL claim
→ immutable subscription_invoice amount/currency snapshot
→ one invoice per subscription and cycle start
→ transactional SUBSCRIPTION_PAYMENT_REQUESTED outbox
→ bounded Service Bus publication retries
```

## Safety defaults

```text
CRAVES_SUBSCRIPTION_BILLING_GENERATOR_ENABLED=false
CRAVES_SUBSCRIPTION_BILLING_PUBLISHER_ENABLED=false
```

No provider is called by this module. It does not activate a subscription, mark an invoice paid, create a meal occurrence, or create an Order. The Integration Service payment consumer and the Subscription payment-status consumer must be deployed and validated before either flag is enabled.

## Business ownership

The invoice copies the amount, currency and billing period from the approved active plan. Source code does not choose subscription price, trial period, grace period, payment retry schedule, cancellation-effective date, credit, skip-meal or refund behavior.

## Idempotency and concurrency

- `(subscription_id, cycle_start)` is unique.
- Due subscriptions use `FOR UPDATE SKIP LOCKED`.
- Stale billing and outbox leases are recoverable.
- Outbox message ID equals the outbox UUID.
- Failed publication retries are bounded and end in local `DEAD_LETTER` state.

## Local validation

```bash
cd services/subscription-service
mvn -B -ntp verify
```

## Activation order later

1. Deploy with both flags false.
2. Apply Flyway V5 and inspect tables.
3. Deploy the Integration subscription-payment consumer disabled.
4. Deploy the Subscription payment-status consumer disabled.
5. Validate both consumers synthetically.
6. Enable billing publisher only after Integration is ready.
7. Enable billing generator last.
