# Delivery Command Orchestration

This module implements the asynchronous delivery flow owned by the Craves Integration Service.
It consumes `CHEF_ACCEPTED_ORDER`, schedules dispatch close to `readyAt`, requests quotes from all
active and deployed delivery adapters, sends the quote candidates through the persisted Delivery
Intelligence engine, attempts delivery creation in intelligent rank order, persists one final delivery
job per chef sub-order, and publishes `DELIVERY_STATUS_CHANGED` through the transactional outbox.

The runtime remains disabled until:

```text
CRAVES_DELIVERY_COMMAND_ENABLED=true
```

Deploying the code while the flag is false does not start Service Bus processors and does not create
provider deliveries.

## Architecture rules preserved

- Never create delivery when payment succeeds.
- Schedule only after the chef accepts the chef-specific sub-order.
- Dispatch at `readyAt - leadTime`, with a default lead of 10 minutes.
- Keep exactly one delivery command, intelligence assignment, and final delivery job per
  `chefSubOrderId`.
- Consume Service Bus messages with PeekLock and manual settlement.
- Treat delivery as at-least-once messaging with idempotent business outcomes.
- Request provider quotes concurrently with a bounded timeout.
- Submit all quoted candidates to the Delivery Intelligence engine.
- Persist predicted success, live and stored performance, momentum, Thompson-sampling exploration,
  proximity, provider quality, final score, and the selected candidate.
- Attempt creation with the intelligence-selected provider first.
- Use the remaining persisted intelligence ranking as bounded fallback order.
- Persist the actual accepted fallback candidate and mark failed provider attempts.
- Store the complete quote, intelligence, and create audit snapshot on `delivery_job`.
- Write `DELIVERY_STATUS_CHANGED` to the database outbox in the same transaction as the delivery job.
- Recover abandoned command and outbox processing leases.
- Dead-letter invalid or exhausted Service Bus messages with an actionable reason.

## Intelligent partner-assignment formula

The existing Java port of the supplied delivery-intelligence code is now in the automatic path.
For every available provider candidate:

```text
predictedSuccessProbability
+ seven-day live performance
+ faded historical performance
+ momentum
+ Thompson-sampling exploration
= providerQualityScore

providerQualityScore
+ pickup proximity or pickup ETA
= finalScore
```

Default quality weights:

```text
Predictor:     55%
Rolling score: 35%
Exploration:   10%
```

Default final-ranking weights:

```text
Proximity or pickup ETA: 60%
Provider quality:         40%
```

The default selection strategy remains stochastic softmax. This prevents a new provider from being
permanently excluded while still favouring stronger candidates. `GREEDY` remains available for
controlled use.

### Provider APIs without pre-booking rider ETA

Some provider APIs, including the current Borzo calculate-order response, return price and
serviceability but do not expose the rider-to-pickup ETA before order creation. The system must not
fabricate rider locations or claim a false ETA.

For such candidates:

- `pickupDistanceKm` remains null.
- `pickupEtaMinutes` remains null.
- The engine uses the configurable neutral proximity score.
- Provider quality, historical performance, momentum, exploration, availability, service area and
  quoted cost remain persisted.

Default:

```text
CRAVES_DELIVERY_UNKNOWN_PROXIMITY_SCORE=50.0
```

When a future adapter supplies a genuine normalized pickup distance or ETA, the full proximity formula
is applied automatically.

## Service Bus topology

```text
Topic: craves-domain-events
  Subscription: integration-service-chef-accepted
    SQL filter: event_type = 'CHEF_ACCEPTED_ORDER'

Queue: delivery-command
  Built-in DLQ: delivery-command/$DeadLetterQueue
```

The Integration Service uses its system-assigned managed identity:

```text
Azure Service Bus Data Sender
  - craves-domain-events topic
  - delivery-command queue

Azure Service Bus Data Receiver
  - integration-service-chef-accepted subscription
  - delivery-command queue
```

A Service Bus connection string is supported only as a temporary local-development fallback. The
Azure deployment must use `SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE` and managed identity.

## Chef-accepted event envelope

The Order Service must publish this versioned envelope:

```json
{
  "eventId": "11111111-1111-1111-1111-111111111111",
  "eventType": "CHEF_ACCEPTED_ORDER",
  "eventVersion": "1.0",
  "occurredAt": "2026-07-14T13:00:00Z",
  "correlationId": "22222222-2222-2222-2222-222222222222",
  "causationId": null,
  "source": "order-service",
  "subject": "chef-sub-order/33333333-3333-3333-3333-333333333333",
  "data": {
    "orderId": "22222222-2222-2222-2222-222222222222",
    "chefSubOrderId": "33333333-3333-3333-3333-333333333333",
    "readyAt": "2026-07-14T13:30:00Z",
    "distanceKm": 4.6,
    "area": "Madhapur",
    "deliveryRequest": {
      "matter": "Freshly prepared packaged food",
      "totalWeightKg": 2,
      "thermoboxRequired": true,
      "pickup": {
        "address": "Madhapur, Hyderabad, Telangana, India",
        "contactName": "Craves Test Chef",
        "contactPhone": "919999999991",
        "latitude": 17.4483,
        "longitude": 78.3915,
        "requiredStart": null,
        "requiredFinish": null,
        "note": "Pickup"
      },
      "dropoff": {
        "address": "Gachibowli, Hyderabad, Telangana, India",
        "contactName": "Craves Test Customer",
        "contactPhone": "919999999992",
        "latitude": 17.4401,
        "longitude": 78.3489,
        "requiredStart": null,
        "requiredFinish": null,
        "note": "Dropoff"
      }
    }
  }
}
```

The Service Bus message must also contain:

```text
event_type=CHEF_ACCEPTED_ORDER
```

### Deterministic routing context

- `distanceKm` may be supplied by Order Service.
- When omitted, Integration Service calculates straight-line distance from pickup and dropoff
  coordinates using the Haversine formula.
- `area` may be supplied explicitly.
- When omitted, the first comma-separated pickup-address component is used, for example
  `Madhapur, Hyderabad` becomes `Madhapur`.
- `orderHour` and `dayOfWeek` are calculated once from `occurredAt` in `Asia/Kolkata` and stored in the
  delivery command. Monday is `0` and Sunday is `6`, matching the supplied predictor convention.

The event is rejected when distance cannot be supplied or calculated, or when an area cannot be
resolved.

## Processing flow

```text
CHEF_ACCEPTED_ORDER subscription
        |
        v
DeliveryCommandScheduler
  - validate event envelope
  - calculate deterministic intelligence context
  - calculate dispatchAt
  - insert delivery_command idempotently
  - schedule delivery-command message natively in Service Bus
        |
        v
DeliveryCommandWorker
  - atomically claim command
  - skip when delivery_job already exists
        |
        v
DeliveryProviderRouter
  - load active delivery_provider rows
  - quote deployed adapters concurrently
  - normalize genuine provider pickup ETA/distance when available
  - call DeliveryIntelligenceService.assign
  - use persisted selected provider first
  - use persisted ranked candidates as bounded fallback
        |
        v
DeliveryCommandCompletionService (one database transaction)
  - mark actual assignment candidate ACCEPTED
  - mark failed create candidates FAILED
  - update assignment status to ASSIGNED
  - insert delivery_job with assignment_id and complete audit snapshot
  - insert DELIVERY_STATUS_CHANGED into delivery_outbox
  - mark delivery_command COMPLETED
        |
        v
DeliveryOutboxPublisher
  - claim due rows with SKIP LOCKED
  - publish to craves-domain-events
  - mark published or retry/dead-letter
```

## Idempotency model

```text
Source event:       unique source_event_id
Chef sub-order:     unique delivery_command.chef_sub_order_id
Assignment:         unique delivery_assignment.chef_sub_order_id
Final delivery job: unique delivery_job.chef_sub_order_id
Provider delivery:  unique provider_id + provider_delivery_id
Outbox:             durable row with retry state
```

The intelligence engine seeds stochastic selection from `chefSubOrderId` and `orderId` and persists the
first assignment. A retry returns the same assignment instead of re-randomizing.

## Runtime variables

```text
CRAVES_DELIVERY_COMMAND_ENABLED=false
SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE=
SERVICE_BUS_CONNECTION_STRING=
SERVICE_BUS_TOPIC_NAME=craves-domain-events
SERVICE_BUS_CHEF_ACCEPTED_SUBSCRIPTION=integration-service-chef-accepted
SERVICE_BUS_DELIVERY_COMMAND_QUEUE=delivery-command
CRAVES_DELIVERY_COMMAND_LEAD_TIME_MINUTES=10
CRAVES_DELIVERY_QUOTE_TIMEOUT_SECONDS=4
CRAVES_DELIVERY_MAX_PROVIDER_ATTEMPTS=3
CRAVES_DELIVERY_MAX_DELIVERY_ATTEMPTS=5
CRAVES_DELIVERY_MAX_CONCURRENT_MESSAGES=4
CRAVES_DELIVERY_PREFETCH_COUNT=8
CRAVES_DELIVERY_MAX_AUTO_LOCK_RENEW_MINUTES=5
CRAVES_DELIVERY_OUTBOX_BATCH_SIZE=20
CRAVES_DELIVERY_OUTBOX_PUBLISH_INTERVAL_MS=5000
CRAVES_DELIVERY_UNKNOWN_PROXIMITY_SCORE=50.0
```

Do not store `SERVICE_BUS_CONNECTION_STRING` in Azure for the managed-identity deployment.

## Internal controlled-test endpoint

The endpoint exists only when delivery commands are enabled:

```http
POST /internal/v1/delivery-orchestration/chef-accepted
X-Craves-Internal-Secret: <CRAVES_INTERNAL_SERVICE_KEY>
Content-Type: application/json
```

It accepts the same envelope as Service Bus and is only for controlled sandbox validation before the
Order Service publisher is wired. It must not be exposed through a public APIM product.

## Main source files

```text
src/main/java/in/craves/integration/delivery/DeliveryIntelligenceService.java
src/main/java/in/craves/integration/delivery/DeliveryAssignmentRepository.java
src/main/java/in/craves/integration/delivery/command/DeliveryCommandModels.java
src/main/java/in/craves/integration/delivery/command/DeliveryCommandProperties.java
src/main/java/in/craves/integration/delivery/command/DeliveryCommandScheduler.java
src/main/java/in/craves/integration/delivery/command/DeliveryCommandWorker.java
src/main/java/in/craves/integration/delivery/command/DeliveryProviderRouter.java
src/main/java/in/craves/integration/delivery/command/DeliveryCommandCompletionService.java
src/main/java/in/craves/integration/delivery/command/DeliveryJobRepository.java
src/main/java/in/craves/integration/delivery/command/DeliveryOutboxRepository.java
src/main/java/in/craves/integration/delivery/command/DeliveryOutboxPublisher.java
src/main/java/in/craves/integration/delivery/command/DeliveryServiceBusConfiguration.java
src/main/java/in/craves/integration/delivery/command/DeliveryServiceBusProcessors.java
src/main/java/in/craves/integration/web/DeliveryOrchestrationInternalController.java
src/main/resources/db/migration/V4__delivery_command_orchestration.sql
```

## Tests

```bash
cd services/integration-service
mvn -B clean test
```

Coverage includes:

- Scheduling before `readyAt`.
- Haversine distance and area fallback.
- Persisted intelligence context.
- Intelligent selected-provider execution.
- Ranked provider fallback without re-quoting.
- Neutral proximity for provider APIs without pre-booking rider ETA.
- Transactional final assignment and delivery-job completion.
- Worker redelivery idempotency.

## Safe rollout

1. Deploy and test with `CRAVES_DELIVERY_COMMAND_ENABLED=false`.
2. Confirm Spring startup and `/actuator/health`.
3. Keep `BORZO_API_ENABLED=false` outside a controlled sandbox window.
4. Register only technically and commercially approved providers as active.
5. Activate one sandbox provider and orchestration for one controlled future `readyAt` event.
6. Confirm one command, one assignment, one provider delivery, one job and one published outbox event.
7. Repeat the identical event and confirm no second assignment or delivery.
8. Disable Borzo and orchestration after the controlled test.
9. Wire Order Service publishing only after the controlled test is documented.

## Remaining launch blockers

- Order Service does not yet publish `CHEF_ACCEPTED_ORDER`.
- Borzo remains sandbox-only outside a contracted production account.
- Borzo does not document `client_order_id` as a guaranteed create-order idempotency key. An ambiguous
  network timeout requires reconciliation before automatic create retry is safe in production.
- The webhook inbox is signed and idempotent, but inbox-to-delivery-job/status/outbox processing is a
  separate pending module.
- Additional providers need real adapters and normalized pickup ETA/distance fields where their APIs
  expose them.
- Private ingress/APIM restrictions and Key Vault references remain hardening items.
- Production requires provider KYC, written commercial/SLA terms, monitoring, support runbooks, and a
  controlled Hyderabad pilot with at least two independent live providers.
