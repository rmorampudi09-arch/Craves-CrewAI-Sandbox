# Subscription payment-status consumer

This module consumes `SUBSCRIPTION_PAYMENT_STATUS_CHANGED` and applies the result to the Subscription Service transactionally.

## Atomic changes

For a valid event, one database transaction:

1. records the event in an idempotent inbox;
2. locks and validates the invoice;
3. verifies amount and currency against the invoice snapshot;
4. updates invoice status and history;
5. activates or marks the subscription payment-failed where permitted;
6. releases only occurrences inside the paid cycle to `READY_FOR_ORDER`;
7. records occurrence and subscription history.

## Late and duplicate events

- `event_id` is unique.
- A paid invoice cannot be downgraded by a late failure.
- Duplicate status events are harmless.
- Amount or currency mismatches are rejected and dead-lettered.
- Existing and newly generated occurrences are both covered. Flyway V6 adds a database trigger that starts a new occurrence as `READY_FOR_ORDER` when a paid invoice already covers its service date.

## Safety default

```text
CRAVES_SUBSCRIPTION_PAYMENT_STATUS_CONSUMER_ENABLED=false
```

The processor bean does not exist until the flag is true. No event is consumed during deployment with defaults.

## Business boundaries

This module does not define payment retry timing, grace periods, skipped meals, credits, cancellation effective dates or refund eligibility. A payment failure only records `PAYMENT_FAILED`; no charge or refund is initiated.

## Local validation

```bash
cd services/subscription-service
mvn -B -ntp verify
```

## Activation later

1. Apply Flyway V6 with the consumer false.
2. Create the filtered Service Bus subscription through the guarded pipeline.
3. Deploy the Subscription image.
4. Send one synthetic pending, paid and duplicate event.
5. Verify invoice, subscription, occurrence and inbox rows.
6. Verify dead-letter behavior for amount mismatch.
7. Enable Integration status publisher only after this consumer is healthy.
