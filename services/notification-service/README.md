# Craves Notification Service

Production foundation for Craves transactional notifications.

## What this service owns

- In-app notification inbox records for customer and chef apps
- Notification request audit trail
- Delivery attempt log for email, SMS and push channels
- Template seed data for core order/payment/chef events

## Current channel behavior

- `IN_APP`: persisted and marked `SENT`
- `EMAIL`, `SMS`, `PUSH`: persisted as `PENDING` until provider adapters are enabled

OTP is not handled here. Phone OTP remains with Firebase Authentication and the Craves Auth Service.

## Database

Uses `craves_business_db` with schema `notification_schema`.

Required environment variables:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://<server>:5432/craves_business_db?sslmode=require
SPRING_DATASOURCE_USERNAME=<db-user>
SPRING_DATASOURCE_PASSWORD=<db-password>
CRAVES_INTERNAL_SERVICE_KEY=<shared-service-key>
```

## Local run

```bash
cd services/notification-service
mvn spring-boot:run
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

## Internal create notification API

```bash
curl -X POST http://localhost:8080/internal/v1/notifications \
  -H "X-Craves-Internal-Key: $CRAVES_INTERNAL_SERVICE_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "requestKey":"test-001",
    "sourceService":"manual-test",
    "eventType":"ORDER_CREATED",
    "userId":"00000000-0000-0000-0000-000000000001",
    "userRole":"CUSTOMER",
    "channel":"IN_APP",
    "title":"Order created",
    "body":"Your order has been created.",
    "targetType":"ORDER"
  }'
```

## In-app inbox API

For the current foundation, APIM or an internal caller must pass the resolved Craves identity ID as `X-Craves-Identity-Id`. Before public production exposure, this must be moved behind APIM JWT validation and identity-header injection.

```bash
curl http://localhost:8080/api/v1/notifications/in-app \
  -H "X-Craves-Identity-Id: 00000000-0000-0000-0000-000000000001"
```

## Deployment

Use `azure-pipelines-notification-service.yml`. The pipeline expects the Azure DevOps variable:

```text
AZURE_SERVICE_CONNECTION=Craves-Dev-Service-Connection
```

Container App runtime env vars must be configured separately using Azure Container App secrets/env vars. Do not paste live secrets into source control.
