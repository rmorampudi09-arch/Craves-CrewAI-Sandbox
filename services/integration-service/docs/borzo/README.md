# Borzo Delivery Provider Adapter

This module integrates Borzo Business API 1.8 into the existing Craves Integration Service.
It is deliberately disabled by default and must remain inactive in the delivery-provider registry
until sandbox callbacks, commercial onboarding and production KYC are complete.

Official technical reference:

- https://borzodelivery.com/in/business-api/doc

## What this module implements

- Provider-neutral `DeliveryProviderAdapter` contract.
- Adapter registry for future delivery-command worker lookup.
- Borzo price calculation through `POST /calculate-order`.
- Borzo order creation through `POST /create-order`.
- Borzo cancellation through `POST /cancel-order`.
- Borzo tracking through `GET /orders` and `GET /courier`.
- Motorbike vehicle type `8`, currently limited to 20 kg.
- Optional thermobox request propagation.
- Craves client reference propagation through destination `client_order_id`.
- Borzo order and delivery status normalization.
- HMAC-SHA256 callback verification against the exact raw request body.
- Durable callback ingestion into `delivery_schema.delivery_webhook_inbox`.
- Internal, service-key-protected sandbox/operational endpoints for quote, create, track and cancel.
- Exact retry deduplication through a deterministic event identity.
- An inactive Borzo provider-registry seed through Flyway V3.

## Files

```text
src/main/java/in/craves/integration/config/BorzoProperties.java
src/main/java/in/craves/integration/delivery/provider/DeliveryProviderAdapter.java
src/main/java/in/craves/integration/delivery/provider/DeliveryProviderAdapterRegistry.java
src/main/java/in/craves/integration/delivery/borzo/BorzoApiClient.java
src/main/java/in/craves/integration/delivery/borzo/BorzoSignatureVerifier.java
src/main/java/in/craves/integration/delivery/borzo/BorzoStatusMapper.java
src/main/java/in/craves/integration/delivery/borzo/BorzoWebhookInboxRepository.java
src/main/java/in/craves/integration/delivery/borzo/BorzoWebhookService.java
src/main/java/in/craves/integration/web/BorzoWebhookController.java
src/main/java/in/craves/integration/web/BorzoInternalController.java
src/main/java/in/craves/integration/web/BorzoControllerAdvice.java
src/main/resources/db/migration/V3__register_borzo_provider.sql
```

## Runtime variables

```text
BORZO_API_ENABLED=false
BORZO_API_BASE_URL=https://robotapitest-in.borzodelivery.com/api/business/1.8
BORZO_API_AUTH_TOKEN=<secret>
BORZO_CALLBACK_TOKEN=<secret>
BORZO_CONNECT_TIMEOUT_SECONDS=5
BORZO_READ_TIMEOUT_SECONDS=20
```

`BORZO_API_AUTH_TOKEN` is sent only to Borzo in the `X-DV-Auth-Token` header.
`BORZO_CALLBACK_TOKEN` is used only to validate the `X-DV-Signature` callback header.
They must be different secrets and must never be committed to Git.

During the current build stage, use secret-backed Azure Container Apps values. Migrate both
values to Azure Key Vault references after the adapter stabilizes.

## Internal adapter endpoints

All endpoints require:

```http
X-Craves-Internal-Secret: <CRAVES_INTERNAL_SERVICE_KEY>
```

```http
POST /internal/v1/delivery-providers/borzo/quote
POST /internal/v1/delivery-providers/borzo/deliveries
GET  /internal/v1/delivery-providers/borzo/deliveries/{providerDeliveryId}
POST /internal/v1/delivery-providers/borzo/deliveries/{providerDeliveryId}/cancel
```

They return `503 Service Unavailable` while `BORZO_API_ENABLED=false`. Do not expose these routes
through public APIM products. They are intended for controlled service-to-service and sandbox use.

## Callback endpoint

```http
POST /api/v1/webhooks/delivery/borzo
Content-Type: application/json
X-DV-Signature: <lowercase HMAC-SHA256 hex>
```

The signature is calculated over the exact UTF-8 request body. Parsing or re-serializing the body
before verification would change the bytes and invalidate the signature.

Valid callbacks are stored in:

```text
delivery_schema.delivery_webhook_inbox
```

The endpoint returns a JSON receipt containing the event identity, event type, normalized status
and whether the callback was a duplicate.

The callback stays in `RECEIVED` state. A later delivery-event processor will associate it with
`delivery_job`, insert the normalized `delivery_event`, update the job and create the outbox event.
This separation allows the webhook endpoint to respond quickly and safely.

## Status normalization

Examples:

```text
planned                  -> SEARCHING
courier_assigned         -> COURIER_ASSIGNED
courier_departed         -> COURIER_TO_PICKUP
courier_at_pickup        -> AT_PICKUP
parcel_picked_up         -> PICKED_UP
active                   -> IN_TRANSIT
courier_arrived          -> AT_DROPOFF
finished                 -> DELIVERED
canceled                 -> CANCELLED
delayed                  -> DELAYED
return_*                 -> RETURNING / RETURNED
invalid or deleted       -> FAILED
```

## Local test

```bash
cd services/integration-service
mvn -B clean test
```

Do not use a real token in automated tests. The API client tests use Spring's mock HTTP server.

## Safe deployment sequence

1. Deploy with `BORZO_API_ENABLED=false` and no Borzo secrets.
2. Confirm Maven tests, Flyway V3 and Spring startup.
3. Add the sandbox API token and callback token as Container App secrets.
4. Bind the environment variables to those secret references.
5. Keep `BORZO_API_ENABLED=false` while testing only callback signature handling.
6. Configure the sandbox callback URL in the Borzo test cabinet.
7. Send controlled sandbox callbacks and confirm inbox deduplication.
8. Set `BORZO_API_ENABLED=true` only for controlled sandbox adapter tests.
9. Keep `delivery_provider.is_active=false` until all acceptance checks pass.

## Current limitations

- No automatic order-service event wiring yet.
- No delivery-command worker yet.
- No quote fan-out or fallback execution yet.
- No callback-to-delivery-job processor yet.
- Borzo does not document `client_order_id` as a guaranteed idempotency key. Craves must prevent
  duplicate create attempts using its own delivery command and job records.
- The adapter currently uses motorbike type `8`. Vehicle selection must become policy-driven before
  supporting heavier or oversized deliveries.
- Thermobox request support does not prove rider availability. Written operational confirmation
  from Borzo is still required.
- Production activation is blocked pending business registration, KYC, commercial terms and a
  controlled Hyderabad pilot.
