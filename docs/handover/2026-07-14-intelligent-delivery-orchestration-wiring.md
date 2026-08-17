# Craves Intelligent Delivery Orchestration Wiring Handover

Date: 2026-07-14  
Service: Integration Service  
Stack: Spring Boot 3, Java 21, PostgreSQL, Azure Container Apps, Azure Service Bus  
Deployment state at implementation time: delivery command disabled, Borzo outbound disabled

## Objective

Connect the already implemented Java port of the supplied delivery-intelligence algorithm to the
automatic Service Bus delivery workflow. Before this change, the command router quoted active
providers and selected primarily by pickup ETA and price. After this change, automatic routing uses
the persisted intelligence assignment and then uses the remaining intelligence ranking for bounded
fallback.

## Result

The automatic flow now performs:

```text
CHEF_ACCEPTED_ORDER
→ deterministic routing context
→ scheduled delivery command
→ concurrent provider quote fan-out
→ DeliveryIntelligenceService.assign
→ selected provider create attempt
→ ranked fallback create attempts
→ actual candidate marked ACCEPTED
→ failed candidates marked FAILED
→ delivery_job linked to assignment_id
→ DELIVERY_STATUS_CHANGED outbox event
```

## Intelligent factors now used automatically

- Delivery distance.
- Order hour in Asia/Kolkata.
- Day of week using Monday=0 through Sunday=6.
- Provider active state.
- Provider service area.
- Provider quote availability.
- Genuine pickup distance when supplied by an adapter.
- Genuine pickup ETA when supplied by an adapter.
- Predicted success probability.
- Seven-day live score.
- Historical rolling score with fade.
- Performance momentum.
- Thompson-sampling exploration.
- Provider quality score.
- Proximity score.
- Final weighted score.
- Greedy or stochastic softmax assignment.

## Provider-only API adaptation

Borzo calculate-order returns serviceability and price but does not expose rider-to-pickup ETA before
booking. Craves does not fabricate rider locations or fake pickup ETA. Such candidates retain null
pickup ETA and distance and receive a configurable neutral proximity score:

```text
CRAVES_DELIVERY_UNKNOWN_PROXIMITY_SCORE=50.0
```

This permits provider-quality learning and intelligent cold-start selection while preserving data
truthfulness. An adapter that later returns a genuine pickup ETA or distance automatically receives
full proximity scoring.

## Event contract changes

`CHEF_ACCEPTED_ORDER.data` now accepts:

```json
{
  "distanceKm": 4.6,
  "area": "Madhapur"
}
```

Both fields have controlled fallbacks:

- Missing `distanceKm`: calculate Haversine distance from pickup/dropoff coordinates.
- Missing `area`: use the first comma-separated pickup-address component.

The event is rejected when neither supplied nor derivable.

## Main code paths changed

```text
services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryCommandModels.java
```

Adds persisted intelligence context to scheduled commands and adds the assignment/executed candidate
to the routing result.

```text
services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryCommandScheduler.java
```

Builds deterministic distance, area, order hour and day-of-week context before the command is stored
and scheduled.

```text
services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryProviderRouter.java
```

Quotes active adapters concurrently, normalizes candidate data, calls `DeliveryIntelligenceService`,
uses the selected provider first, and follows the persisted ranking for fallback.

```text
services/integration-service/src/main/java/in/craves/integration/delivery/DeliveryIntelligenceService.java
```

Supports truthful provider-only candidates without pickup ETA/distance through a configurable neutral
proximity score. Scoring version is now `PROXIMITY_QUALITY_V2`.

```text
services/integration-service/src/main/java/in/craves/integration/config/DeliveryIntelligenceProperties.java
```

Adds `unknownProximityScore`, validated from 0 through 100.

```text
services/integration-service/src/main/java/in/craves/integration/delivery/DeliveryAssignmentRepository.java
```

Marks the actual executed candidate `ACCEPTED`, failed create candidates `FAILED`, and the assignment
`ASSIGNED` with the actual provider/candidate.

```text
services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryCommandCompletionService.java
```

Completes assignment state, delivery job, outbox row and command in one database transaction.

```text
services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryJobRepository.java
```

Persists `assignment_id` and the complete routing audit snapshot.

```text
services/integration-service/src/main/resources/application.yml
```

Adds:

```text
CRAVES_DELIVERY_UNKNOWN_PROXIMITY_SCORE
```

## Tests added or updated

```text
DeliveryCommandSchedulerTest
DeliveryProviderRouterTest
DeliveryCommandWorkerTest
DeliveryCommandCompletionServiceTest
DeliveryIntelligenceNeutralProximityTest
```

The tests cover deterministic context, intelligent selection, fallback, neutral proximity, final
assignment persistence and redelivery idempotency.

## No database migration required

The existing schema already contains:

- `delivery_assignment.selected_candidate_id`
- `delivery_assignment.selected_provider_id`
- `delivery_assignment.status`
- candidate execution statuses including `FAILED` and `ACCEPTED`
- `delivery_job.assignment_id`

Therefore this wiring uses the existing V2/V4 schema and does not introduce V5.

## Operational safety state

Keep these values until the pipeline and controlled sandbox test pass:

```text
CRAVES_DELIVERY_COMMAND_ENABLED=false
BORZO_API_ENABLED=false
```

Service Bus namespace and entity bindings may remain configured while the command flag is false.

## Manual steps required

- Run a new Integration Service pipeline from `main`.
- Verify Maven compile and all tests.
- Verify a healthy new Container App revision.
- Do not enable automatic commands yet.
- After technical verification, activate Borzo only for one controlled sandbox event.
- Keep provider registry activation limited to approved/deployed adapters.

## Pending work

- Controlled Service Bus end-to-end sandbox event.
- Order Service `CHEF_ACCEPTED_ORDER` publisher.
- Webhook inbox processor that updates delivery job/event/outbox state.
- Production-safe provider create reconciliation after ambiguous timeouts.
- Additional delivery-provider adapters.
- APIM/private-ingress hardening.
- Key Vault references for all vendor secrets.
- Monitoring, alerts, support runbook and Hyderabad pilot.
