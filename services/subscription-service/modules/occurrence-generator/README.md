# Subscription occurrence generator

This module turns an ACTIVE customer subscription plus an ACTIVE plan schedule into immutable dated meal occurrences. It does not create an Order or call a payment provider.

## Generation flow

```text
ACTIVE customer_subscription.next_service_date
→ multi-replica row claim
→ active versioned plan schedule
→ generation lead-time check
→ exact matching service-day items
→ unique subscription_occurrence
→ immutable occurrence items
→ next matching service date
```

## Safety

```text
CRAVES_SUBSCRIPTION_OCCURRENCE_GENERATOR_ENABLED=false
```

The worker is absent unless the flag is true. Claims use PostgreSQL row locks and stale-lock recovery. The `(subscription_id, service_date)` uniqueness constraint prevents duplicate occurrences.

An occurrence starts in `BILLING_PENDING`. It cannot become an order until later billing and Order-consumer modules succeed.

## Scheduling behavior

- Weekly plans match ISO weekday 1–7.
- Monthly plans match day 1–28.
- Service timestamps use the plan's IANA timezone and explicit service time.
- Generation opens only at the configured lead time.
- A date with no matching item advances to the next explicit service date without inventing a meal.
- Missing customer, chef or saved-address references fail the claimed occurrence and preserve the subscription for investigation.

## Local test

```bash
cd services/subscription-service
mvn -B -ntp verify
```

## Activation later

Keep the generator false until billing, payment-status handling and Order occurrence consumption have passed CI and controlled synthetic validation.
