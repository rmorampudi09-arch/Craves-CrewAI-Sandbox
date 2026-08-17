# Order Service: CHEF_ACCEPTED_ORDER Transactional Outbox

This module makes chef acceptance durable and duplicate-safe.

## Business transition

Only this state transition is allowed:

```text
CHEF_ACCEPTANCE_PENDING -> CHEF_ACCEPTED
```

The acceptance request requires a positive `prepTimeMinutes`. Craves does not impose a product-level maximum preparation time.

```http
POST /api/v1/chef/orders/{orderId}/accept
Authorization: Bearer <Craves access token>
X-Correlation-ID: <optional UUID>
Idempotency-Key: <optional stable request key>
Content-Type: application/json
```

```json
{
  "prepTimeMinutes": 35,
  "note": "Order confirmed"
}
```

The first successful request persists:

```text
status = CHEF_ACCEPTED
accepted_at = database current UTC time
ready_at = accepted_at + prepTimeMinutes
```

The same request repeated with the same preparation time returns the existing accepted order and does not create another event. A repeated request with a different preparation time returns:

```json
{
  "error": "ORDER_ALREADY_ACCEPTED",
  "message": "The order was already accepted with a different preparation time."
}
```

## Transaction boundary

One PostgreSQL transaction performs all of the following:

1. locks the chef-specific order row;
2. validates the current state;
3. updates the order to `CHEF_ACCEPTED`;
4. persists `accepted_at`, `prep_time_minutes`, and `ready_at`;
5. appends order status history;
6. inserts one `CHEF_ACCEPTED_ORDER` event into `order_schema.domain_event_outbox`.

A unique `event_key` prevents more than one chef-accepted event for the same chef-specific order.

## Event contract

The versioned schema is:

```text
contracts/events/chef-accepted-order-v1.schema.json
```

The payload intentionally matches the existing Integration Service `ChefAcceptedOrderData` contract:

```text
orderId          = parent checkout identifier
chefSubOrderId   = chef-specific customer_order identifier
readyAt          = calculated food-ready UTC timestamp
distanceKm       = null; Integration calculates it from coordinates
area             = immutable pickup area snapshot
deliveryRequest  = immutable pickup, drop-off, package and thermobox data
```

The internal event contains delivery-required personal data. It must remain inside the protected Service Bus and PostgreSQL boundaries. Application logs record event IDs and status only; they must never log `payload_json`.

## Outbox publication

The scheduled worker:

- claims rows with PostgreSQL `FOR UPDATE SKIP LOCKED`;
- supports multiple Order Service replicas;
- detects stale processing locks;
- publishes using Azure Service Bus `messageId = eventId`;
- marks rows published only after the Service Bus send succeeds;
- retries with bounded exponential backoff;
- moves exhausted local publication attempts to `DEAD` for operations review.

Service Bus uses `DefaultAzureCredential`, which resolves the Container App managed identity in Azure.

## Environment variables

```text
CRAVES_DOMAIN_EVENT_OUTBOX_ENABLED=false
CRAVES_DOMAIN_EVENT_OUTBOX_FIXED_DELAY_MS=5000
CRAVES_DOMAIN_EVENT_OUTBOX_BATCH_SIZE=20
CRAVES_DOMAIN_EVENT_OUTBOX_MAX_ATTEMPTS=10
CRAVES_DOMAIN_EVENT_OUTBOX_RETRY_BASE_DELAY_SECONDS=5
CRAVES_DOMAIN_EVENT_OUTBOX_STALE_LOCK_SECONDS=300

CRAVES_DOMAIN_EVENT_SERVICE_BUS_ENABLED=false
CRAVES_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE=
CRAVES_DOMAIN_EVENTS_TOPIC_NAME=craves-domain-events
```

Both enable flags remain `false` until the existing Service Bus topic and managed-identity sender role are verified. Chef acceptance still creates durable `PENDING` outbox rows while publication is disabled.

## Database migration

```text
src/main/resources/db/migration/V5__chef_acceptance_domain_outbox.sql
```

The migration adds:

- `customer_order.accepted_at`;
- positive preparation-time guard;
- accepted timestamp consistency guard;
- `domain_event_outbox` table;
- unique event key;
- dispatch and aggregate indexes.

## Local tests

```bash
cd services/order-service
mvn -B clean test
```

Local Service Bus publication should remain disabled unless the developer has an explicitly approved Azure identity and development topic.

## Azure deployment prerequisites

1. The existing `craves-domain-events` topic must exist.
2. The Order Container App must have a managed identity.
3. That identity must have `Azure Service Bus Data Sender` on the topic or namespace.
4. The fully qualified namespace must be supplied without `https://`, for example:

```text
example-namespace.servicebus.windows.net
```

After deployment, run:

```bash
SERVICE_BUS_NAMESPACE="<namespace>.servicebus.windows.net" \
  bash scripts/configure-order-domain-event-publisher.sh
```

The script validates prerequisites and updates runtime settings. It does not create resources or role assignments.

## Scope deliberately deferred

The following belong to the next module:

- 30-minute chef acceptance timeout;
- timed reminder notifications;
- automatic timeout rejection;
- `REFUND_REQUESTED` outbox event;
- Cashfree automatic refund processing;
- acceptance-expiry scheduler and operations dashboard.
