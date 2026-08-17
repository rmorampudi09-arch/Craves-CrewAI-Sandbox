# Craves Borzo Adapter Foundation Handover

Date: 2026-07-14
Service: Integration Service
Provider: Borzo Business API 1.8
Deployment target: Azure Container Apps

## Objective

Add the first real delivery-provider adapter without activating production deliveries. The work
follows CRV-INT-DELIVERY-001: Integration Service owns delivery integrations; adapters implement
quote, create, cancel and track; callbacks are verified and stored durably; provider activation is
separate from code deployment.

## Implemented

- Provider-neutral Java adapter contract and adapter registry.
- Borzo Business API 1.8 sandbox/production base URL configuration.
- `X-DV-Auth-Token` outbound authentication.
- Quote calculation, order creation, cancellation, order retrieval and courier lookup.
- Motorbike type 8 and thermobox request mapping.
- Borzo `client_order_id` mapping with 32-character validation.
- Detailed Borzo order/delivery status normalization.
- Public Borzo callback route with raw-body HMAC-SHA256 verification.
- Durable callback insertion into `delivery_webhook_inbox` with deterministic deduplication.
- Internal endpoints protected by `X-Craves-Internal-Secret` for controlled sandbox operations.
- Flyway V3 inactive Borzo provider registration.
- Unit tests for API request/response mapping, signatures, statuses, webhook ingestion and Spring
  constructor wiring.

## Security decisions

- Borzo is disabled by default.
- No auth token or callback token is committed.
- Callback signature is verified before JSON is trusted or written to PostgreSQL.
- Only a SHA-256 fingerprint of the callback signature is stored.
- Internal adapter endpoints use the existing Craves internal-service secret.
- Borzo remains inactive in `delivery_provider` after migration.

## Configuration

```text
BORZO_API_ENABLED=false
BORZO_API_BASE_URL=https://robotapitest-in.borzodelivery.com/api/business/1.8
BORZO_API_AUTH_TOKEN=<Container App secret reference>
BORZO_CALLBACK_TOKEN=<Container App secret reference>
BORZO_CONNECT_TIMEOUT_SECONDS=5
BORZO_READ_TIMEOUT_SECONDS=20
```

## Manual steps after a successful pipeline

1. Confirm Flyway V3 succeeded and `delivery_provider.provider_id='borzo'` is inactive.
2. Add sandbox auth and callback values as Azure Container Apps secrets. Do not paste values into
   chat, GitHub, YAML or command output.
3. Bind the environment variable names above to the secret references.
4. Deploy first with `BORZO_API_ENABLED=false`.
5. Confirm the callback endpoint rejects missing/invalid signatures.
6. Configure the sandbox callback URL only after `BORZO_CALLBACK_TOKEN` is present.
7. Run controlled sandbox callback and duplicate-callback tests.
8. Enable outbound sandbox calls temporarily for quote/create/track/cancel tests.
9. Disable again until the delivery command worker is ready.

## Pending engineering

- Delivery-command worker and retry/dead-letter flow.
- Quote fan-out and provider fallback orchestration.
- Persistence of the delivery job before/after Borzo create calls.
- Callback inbox processor that links callbacks to `delivery_job`, inserts `delivery_event`, updates
  job status and writes the transactional outbox event.
- Reconciliation scheduler for callback/order status gaps.
- Key Vault references.
- APIM/private-ingress hardening for internal endpoints.
- Metrics, alerts and provider circuit breaker.

## Pending business and operations

- Borzo response to the pre-launch procurement email.
- Business registration and production KYC.
- Written prepared-food and thermobox confirmation.
- Hyderabad commercial rate card and SLA.
- Sandbox callback approval and production API enablement.
- Controlled Hyderabad pilot and acceptance thresholds.

## Risk notes

Borzo does not document `client_order_id` as a guaranteed idempotency key. The future command worker
must use Craves' own unique command/job records and must reconcile ambiguous timeouts before retrying
create-order. A blind retry could create two courier orders.

The existence of `is_thermobox_required` in the API does not guarantee that an equipped rider is
available. Provider activation remains blocked until Borzo confirms the operational behavior in
writing and it is demonstrated during the pilot.
