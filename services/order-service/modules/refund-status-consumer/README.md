# Refund Status Consumer Module

This Order Service module consumes `REFUND_STATUS_CHANGED` v1 from Azure Service Bus and updates one chef-specific order.

## Files

```text
src/main/java/in/craves/order/config/RefundStatusConsumerProperties.java
src/main/java/in/craves/order/refund/RefundStatusModels.java
src/main/java/in/craves/order/refund/RefundStatusEventValidator.java
src/main/java/in/craves/order/refund/RefundStatusTransitionPolicy.java
src/main/java/in/craves/order/refund/RefundStatusUpdateService.java
src/main/java/in/craves/order/refund/RefundStatusChangedServiceBusProcessor.java
src/main/resources/db/migration/V7__refund_status_consumer.sql
```

## Local setup

Required environment variables:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

The consumer is disabled by default. To run it locally against a test Service Bus namespace, additionally set:

```text
CRAVES_REFUND_STATUS_CONSUMER_ENABLED=true
CRAVES_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE=<namespace>.servicebus.windows.net
CRAVES_DOMAIN_EVENTS_TOPIC_NAME=craves-domain-events
CRAVES_REFUND_STATUS_SUBSCRIPTION=order-service-refund-status-changed
```

Authenticate locally with Azure CLI or provide a test-only Service Bus connection string through `SERVICE_BUS_CONNECTION_STRING`. Never commit that value.

## Run

```bash
cd services/order-service
mvn spring-boot:run
```

## Test

```bash
cd services/order-service
mvn -B clean verify
```

## Safety

Keep these settings unchanged until controlled validation is complete:

```text
CRAVES_REFUND_STATUS_CONSUMER_ENABLED=false
CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=false
CRAVES_DOMAIN_EVENT_ENABLED_TYPES=CHEF_ACCEPTED_ORDER
```

No Cashfree request is made by this module.
