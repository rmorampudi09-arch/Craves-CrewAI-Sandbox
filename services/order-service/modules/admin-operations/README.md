# Backend admin investigation APIs

This module gives authorized Craves operations administrators a read-only, audited view of Order, payment, refund and delivery state. It does not provide a generic database browser and it does not mutate commercial state.

## Endpoints

```text
GET /api/v1/admin/operations/orders/{orderId}
GET /api/v1/admin/operations/payments/{paymentOrderId}
GET /api/v1/admin/operations/refunds/{refundId}
GET /api/v1/admin/operations/delivery-commands/{commandId}
```

Every request requires:

```text
Authorization: Bearer <Craves ADMIN token>
X-Admin-Reason: <10 to 500 characters>
X-Correlation-ID: <optional UUID>
```

A UUID correlation ID is generated when one is not supplied and is returned in the response header.

## Data boundaries

The APIs return typed operational projections only. They do not return:

- Cashfree request or response payloads;
- webhook raw payloads or signatures;
- delivery quote/provider payloads;
- access tokens, provider secrets or device tokens;
- full customer phone numbers;
- internal database connection information.

The Order endpoint masks the recipient phone number and returns area/city/postal information instead of the full address lines.

## Audit

Every successful investigation writes an append-only row containing:

- administrator identity;
- resource type and resource ID;
- action;
- mandatory reason;
- correlation ID;
- timestamp.

Audit rows are not updated or deleted by this module.

## Safety

These endpoints are read-only with respect to Orders, payments, refunds and deliveries. They cannot:

- change an order status;
- create or verify a payment;
- request or retry a refund;
- book, cancel or track a delivery;
- publish a Service Bus event;
- resend a notification;
- alter pricing, commission or settlement values.

## Local verification

```bash
cd services/order-service && mvn -B -ntp verify
cd ../integration-service && mvn -B -ntp verify
```

The CI definition is:

```text
azure-pipelines-backend-admin-investigation-ci.yml
```

## Manual work later

1. Merge the stacked PRs in order after their exact heads pass CI.
2. Deploy Order and Integration with their existing fail-closed flags unchanged.
3. Add only the four named APIM operations.
4. Require Firebase/Craves JWT validation and the ADMIN role at APIM and service levels.
5. Keep APIM request/response body tracing disabled for these routes.
6. Verify a non-admin receives HTTP 403.
7. Verify a missing or short reason receives HTTP 400.
8. Verify a successful request creates exactly one audit row.

No Azure resource or APIM operation is created by this module.
