# Craves Integration Service

Spring Boot 3 / Java 21 service that owns Craves provider integrations. The service currently contains the Cashfree sandbox payment foundation and the Delivery Intelligence V1 foundation.

## Delivery Intelligence architecture decision

The supplied Python delivery-intelligence package was used as the algorithm and business-rule reference. It is not deployed as a separate Python/FastAPI service because the approved Craves backend is Java 21 and Spring Boot, and a separate always-on Container App plus scheduled Container Apps Job would introduce a new deployment unit and additional Azure cost.

The production implementation therefore lives inside this existing Integration Service:

```text
services/integration-service/src/main/java/in/craves/integration/delivery
```

The port preserves the intended scoring rules:

- Seven-day live score window.
- Daily archival of aged-out orders.
- Per-day historical fade factor `0.965`.
- New-provider prior `100` with zero stored weight.
- Provider quality weights: predictor `55%`, rolling score `35%`, exploration `10%`.
- Final candidate ranking weights: proximity/ETA `60%`, provider quality `40%`.
- Default stochastic softmax assignment, with greedy mode available for controlled use.
- Persisted Thompson-sampling alpha/beta state instead of process-local memory.
- Immutable assignment candidate audit trail and durable outcome idempotency receipt.

## Important real-provider limitation

The intelligence engine supports optional `agentId` candidates, but it does not fabricate rider locations. Rider-level cascading is activated only when a provider adapter genuinely exposes nearby rider candidates and an offer/decline capability. For providers that expose only serviceability/quote/create APIs, the engine ranks one or more provider quote candidates by pickup ETA/distance and provider quality.

## Non-negotiable delivery timing rule

A delivery must not be created when payment succeeds. The Order Service first records chef acceptance and preparation time. Delivery scheduling happens close to the calculated ready time, with exactly one final delivery job for each chef-specific sub-order.

## Delivery Intelligence APIs

All delivery-intelligence endpoints are internal-only and require:

```http
X-Craves-Internal-Secret: <CRAVES_INTERNAL_SERVICE_KEY>
```

```http
POST /internal/v1/delivery-intelligence/providers
POST /internal/v1/delivery-intelligence/assignments
GET  /internal/v1/delivery-intelligence/assignments/{assignmentId}
POST /internal/v1/delivery-intelligence/outcomes
```

### Register or update a provider

```json
{
  "providerId": "borzo",
  "displayName": "Borzo",
  "adapterType": "BORZO_BUSINESS_API",
  "active": true,
  "serviceAreas": ["Madhapur", "Gachibowli"],
  "capabilities": {
    "QUOTE": true,
    "CREATE_DELIVERY": true,
    "WEBHOOK": true,
    "RIDER_LEVEL_CANDIDATES": false
  }
}
```

### Rank delivery candidates

```json
{
  "chefSubOrderId": "11111111-1111-1111-1111-111111111111",
  "orderId": "22222222-2222-2222-2222-222222222222",
  "distanceKm": 6.4,
  "orderHour": 19,
  "dayOfWeek": 1,
  "area": "Madhapur",
  "strategy": "STOCHASTIC",
  "candidates": [
    {
      "providerId": "borzo",
      "providerQuoteId": "quote-123",
      "agentId": null,
      "pickupDistanceKm": 1.2,
      "pickupEtaMinutes": 8,
      "quotedCost": 92.00,
      "currency": "INR",
      "available": true,
      "providerMetadata": {}
    }
  ]
}
```

Assignment is idempotent by `chefSubOrderId`. A retry returns the originally persisted decision and never re-randomizes the selected provider.

### Record a terminal delivery outcome

```json
{
  "deliveryId": "33333333-3333-3333-3333-333333333333",
  "chefSubOrderId": "11111111-1111-1111-1111-111111111111",
  "orderId": "22222222-2222-2222-2222-222222222222",
  "providerId": "borzo",
  "status": "DELIVERED",
  "promisedPickupMinutes": 10,
  "actualPickupMinutes": 11,
  "promisedDeliveryMinutes": 25,
  "actualDeliveryMinutes": 24,
  "quotedCost": 92.00,
  "actualCost": 92.00,
  "customerRating": 5,
  "hadComplaint": false,
  "distanceKm": 6.4,
  "area": "Madhapur",
  "orderHour": 19,
  "dayOfWeek": 1,
  "occurredAt": "2026-07-14T14:30:00Z"
}
```

Outcome ingestion is idempotent by `deliveryId`. The permanent receipt remains after the hot seven-day score row is archived, preventing a delayed duplicate webhook from updating the bandit twice.

## Delivery database objects

Flyway migration `V2__delivery_intelligence_foundation.sql` creates `delivery_schema` in `craves_integration_db`, including:

- Provider registry and capabilities.
- Assignment decisions and ranked candidate audit.
- Hot seven-day scores.
- Permanent outcome idempotency receipts.
- Persisted bandit state.
- Rolling historical state and daily archive.
- Delivery command, job, event, webhook inbox, and outbox foundations.

The maintenance scheduler runs at 02:00 in `Asia/Kolkata` by default. A PostgreSQL transaction advisory lock ensures only one Container App replica archives scores during each cycle.

## Runtime variables

Existing universal variables remain unchanged:

```text
AZURE_SERVICE_CONNECTION
POSTGRES_INTEGRATION_DB_URL
POSTGRES_INTEGRATION_DB_USER
POSTGRES_INTEGRATION_DB_PASSWORD
CRAVES_INTERNAL_SERVICE_KEY
```

Delivery tuning variables are optional because safe defaults are committed in `application.yml`:

```text
CRAVES_DELIVERY_INTELLIGENCE_ENABLED
CRAVES_DELIVERY_ASSIGNMENT_STRATEGY
CRAVES_DELIVERY_LIVE_WINDOW_DAYS
CRAVES_DELIVERY_DAILY_FADE_FACTOR
CRAVES_DELIVERY_ML_WEIGHT
CRAVES_DELIVERY_ROLLING_SCORE_WEIGHT
CRAVES_DELIVERY_EXPLORATION_WEIGHT
CRAVES_DELIVERY_PROXIMITY_WEIGHT
CRAVES_DELIVERY_QUALITY_WEIGHT
CRAVES_DELIVERY_SEARCH_RADIUS_KM
CRAVES_DELIVERY_MAX_PICKUP_ETA_MINUTES
CRAVES_DELIVERY_MAINTENANCE_CRON
CRAVES_DELIVERY_MAINTENANCE_ZONE
```

Do not commit vendor API tokens. Keep them in Azure DevOps secret variables during the current build stage and migrate them to Azure Key Vault references after module stabilization.

## Current predictor status

`HeuristicDeliverySuccessPredictor` is the production cold-start predictor. It uses the same live/stored blend and momentum adjustment as the supplied package. The `DeliverySuccessPredictor` interface is the controlled extension point for a trained model later.

A trained Gradient Boosting model is deliberately not activated yet because Craves does not have real labelled delivery history, model governance, drift monitoring, or a production Blob/ONNX model store. Deploying a synthetic-data-trained model would look intelligent while making untrustworthy routing decisions.

## Local verification

```bash
cd services/integration-service
mvn -B clean test
mvn -B spring-boot:run
```

Health:

```bash
curl http://localhost:8080/actuator/health
```

A PostgreSQL database named `craves_integration_db` is required. Flyway creates both `payment_schema` and `delivery_schema` objects through the Integration Service migration history.
