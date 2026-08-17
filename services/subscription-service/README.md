# Craves Subscription Service

Spring Boot 3 / Java 21 service for Craves prepaid meal subscription foundations.

## Purpose

This module replaces the temporary Azure Container Apps quickstart placeholder for `ca-craves-subscription-service-p` with a real Craves service.

The MVP foundation covers:

```text
Subscription plans
Customer subscription records
Subscription lifecycle status tracking
Health/metrics endpoints
Craves JWT validation
Flyway migration into subscription_schema
```

## Important business-rule note

This module intentionally does not invent final product rules for:

```text
subscription pricing strategy
unused meal credits
chef payout calculation
holiday handling
refund/cancellation cutoffs
automatic renewal mandates
```

Those remain Product/Finance/Operations decisions.

## Main endpoints

```text
GET   /actuator/health
GET   /api/v1/subscriptions/plans
GET   /api/v1/subscriptions/plans/{planId}
POST  /api/v1/admin/subscription-plans
GET   /api/v1/admin/subscription-plans
PATCH /api/v1/admin/subscription-plans/{planId}/status
POST  /api/v1/subscriptions
GET   /api/v1/subscriptions
GET   /api/v1/subscriptions/{subscriptionId}
PATCH /api/v1/subscriptions/{subscriptionId}/pause
PATCH /api/v1/subscriptions/{subscriptionId}/cancel
PATCH /api/v1/admin/subscriptions/{subscriptionId}/status/{status}
```

## Environment variables

These are passed from Azure DevOps pipeline / Container App environment. Do not paste secret values into chat.

```text
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=$(POSTGRES_BUSINESS_DB_URL)
SPRING_DATASOURCE_USERNAME=$(POSTGRES_BUSINESS_DB_USER)
SPRING_DATASOURCE_PASSWORD=$(POSTGRES_BUSINESS_DB_PASSWORD)
CRAVES_JWT_VERIFICATION_PEM_BASE64=$(CRAVES_JWT_VERIFICATION_PEM_BASE64)
CRAVES_JWT_ISSUER=https://api.craves.in/auth
CRAVES_JWT_AUDIENCE=craves-api
```

## Local run

```bash
mvn -B clean package
java -jar target/subscription-service-0.1.0-SNAPSHOT.jar
```

Local PostgreSQL must contain `craves_business_db`. Flyway creates `subscription_schema` and the initial tables.

## Azure deployment

Use `azure-pipelines-subscription-service.yml`.

Important: the existing Container App placeholder currently used target port 80. This pipeline updates ingress target port to 8080 to match Spring Boot services.
