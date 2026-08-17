# Craves Dynamic Package Metadata Handover

Date: 2026-07-14  
Branch: `feature/dynamic-package-metadata`  
Services: Catalog Service, Order Service, Integration Service  
Stack: Spring Boot 3, Java 21, PostgreSQL, Azure Container Apps

## Product decision implemented

Krishna confirmed the following rules:

1. Package weight is dynamic rather than one fixed value for all orders.
2. Every menu item has a chef-supplied packaged weight for one sellable unit.
3. Weight is stored inside Craves in grams.
4. Every menu item has an explicit thermobox decision: `true` or `false`.
5. Both values are mandatory when a chef creates or edits a menu item.
6. A chef-specific order's total weight is calculated from the ordered quantities.
7. A chef-specific order requires a thermobox when any ordered line requires one.
8. External-provider unit conversion happens only inside the provider adapter.

No pricing, commission, delivery-radius, or legal rule was introduced.

## End-to-end data flow

```text
Chef creates/edits menu item
    ↓
Catalog Service validates explicit package metadata
    ↓
Catalog stores unit_package_weight_grams + thermobox_required
    ↓
Customer checks out
    ↓
Order Service refreshes each active Catalog item
    ↓
Order item snapshots are stored
    ↓
Chef-specific total weight and thermobox requirement are calculated
    ↓
Chef accepts with prepTimeMinutes
    ↓
Order Service persists ready_at
    ↓
Future CHEF_ACCEPTED_ORDER event carries gram weight and thermobox decision
    ↓
Integration Service keeps grams in the provider-neutral contract
    ↓
Borzo adapter rounds grams up to the whole kilograms required by Borzo
```

## Calculation rules

For one chef-specific order:

```text
total_package_weight_grams =
    sum(unit_package_weight_grams_snapshot × ordered quantity)
```

Example:

```text
2 × 650 g meal boxes = 1300 g
1 × 250 g dessert    =  250 g
--------------------------------
Total                = 1550 g
```

Thermobox aggregation:

```text
thermobox_required = true
when any order item has thermobox_required_snapshot = true
```

The calculation uses checked integer arithmetic. Overflow or a non-positive calculated weight is rejected rather than silently wrapped.

## Catalog Service changes

### API contract

Changed:

```text
services/catalog-service/src/main/java/in/craves/catalog/web/ApiDtos.java
```

`MenuItemRequest` now requires:

```json
{
  "unitPackageWeightGrams": 650,
  "thermoboxRequired": false
}
```

Validation:

- `unitPackageWeightGrams` must be present and greater than zero.
- `thermoboxRequired` must be explicitly present, even when its value is `false`.

### Persistence and service behavior

Changed:

```text
services/catalog-service/src/main/java/in/craves/catalog/service/CatalogService.java
```

The service now:

- writes both fields during menu-item creation;
- writes both fields during menu-item update;
- rejects missing or invalid delivery metadata;
- prevents an incomplete legacy item from being made available;
- returns nullable response values only to support legacy rows that predate the migration.

### Flyway migration

Added:

```text
services/catalog-service/src/main/resources/db/migration/V2__menu_item_delivery_metadata.sql
```

Columns:

```text
catalog_schema.menu_item.unit_package_weight_grams
catalog_schema.menu_item.thermobox_required
```

Legacy handling:

- Existing values are not guessed or backfilled.
- Existing available items missing either value are made unavailable.
- The chef must edit those items and provide real values before they can be sold again.

### Test

Added:

```text
services/catalog-service/src/test/java/in/craves/catalog/web/MenuItemRequestValidationTest.java
```

It verifies:

- valid explicit metadata;
- missing weight rejection;
- missing thermobox decision rejection.

### README

Updated:

```text
services/catalog-service/README.md
```

It now contains the request contract, examples, migration behavior, and operational explanation.

## Order Service changes

### Catalog client

Changed:

```text
services/order-service/src/main/java/in/craves/order/service/CatalogClient.java
```

Order Service now reads and validates:

```text
unitPackageWeightGrams
thermoboxRequired
```

It also reads the full kitchen pickup profile required by the next delivery-event module.

### Flyway migration

Added:

```text
services/order-service/src/main/resources/db/migration/V3__delivery_package_snapshots.sql
```

Order-item snapshot columns:

```text
order_schema.order_item.unit_package_weight_grams_snapshot
order_schema.order_item.thermobox_required_snapshot
```

Chef-specific aggregate columns:

```text
order_schema.customer_order.total_package_weight_grams
order_schema.customer_order.thermobox_required
order_schema.customer_order.ready_at
```

### Checkout and acceptance behavior

Changed:

```text
services/order-service/src/main/java/in/craves/order/service/OrderService.java
```

Checkout now:

1. refreshes each cart item from Catalog Service;
2. rejects incomplete delivery metadata;
3. groups the items by kitchen;
4. calculates one package total for each kitchen-specific order;
5. calculates one thermobox decision for each kitchen-specific order;
6. inserts the parent checkout before child order rows;
7. stores immutable item snapshots and order aggregates.

Chef acceptance now requires a positive preparation time and persists:

```text
ready_at = PostgreSQL current time + prepTimeMinutes
```

### API validation

Changed:

```text
services/order-service/src/main/java/in/craves/order/web/ApiDtos.java
```

`ChefAcceptRequest.prepTimeMinutes` is now both `@NotNull` and `@Min(1)`.

### README

Updated:

```text
services/order-service/README.md
```

It documents snapshots, formulas, `ready_at`, and the remaining domain-event work.

## Integration Service changes

### Provider-neutral contract

Changed:

```text
services/integration-service/src/main/java/in/craves/integration/delivery/provider/DeliveryProviderAdapter.java
```

The provider-neutral quote request now contains:

```text
totalWeightGrams
```

rather than:

```text
totalWeightKg
```

Craves therefore keeps precise gram values until a specific provider adapter needs another unit.

### Borzo conversion

Changed:

```text
services/integration-service/src/main/java/in/craves/integration/delivery/borzo/BorzoApiClient.java
```

Borzo accepts whole kilograms. Its adapter performs conservative rounding upward:

```text
1–1000 g    → 1 kg
1001–2000 g → 2 kg
2001–3000 g → 3 kg
```

The adapter rejects motorbike requests outside `1..20000` grams before calling Borzo.

### Integration tests

Updated:

```text
services/integration-service/src/test/java/in/craves/integration/delivery/borzo/BorzoApiClientTest.java
services/integration-service/src/test/java/in/craves/integration/delivery/command/DeliveryCommandSchedulerTest.java
services/integration-service/src/test/java/in/craves/integration/delivery/command/DeliveryCommandWorkerTest.java
services/integration-service/src/test/java/in/craves/integration/delivery/command/DeliveryCommandCompletionServiceTest.java
services/integration-service/src/test/java/in/craves/integration/delivery/command/DeliveryProviderRouterTest.java
```

The Borzo request test verifies that `1250` grams becomes `2` kilograms in the provider payload.

## Deployment order

After the pull request is merged, deploy in this exact order:

```text
1. Catalog Service
2. Order Service
3. Integration Service
```

Reason:

- Catalog Service must expose the new response fields first.
- Order Service then consumes and snapshots those fields.
- Integration Service finally receives the renamed gram-based domain-event field.

Do not deploy Order Service before Catalog Service.

## Azure safety state

Keep these deployed Integration Service settings unchanged during these deployments:

```text
CRAVES_DELIVERY_COMMAND_ENABLED=false
CRAVES_DELIVERY_INTELLIGENCE_ENABLED=true
BORZO_API_ENABLED=false
```

This change does not require a new Azure resource, Service Bus entity, secret, Key Vault entry, or paid SKU.

## Manual steps required

### Azure DevOps

Run the existing pipelines after merge, in order:

```text
azure-pipelines-catalog-service.yml
azure-pipelines-order-service.yml
azure-pipelines-integration-service.yml
```

For each pipeline verify:

- Java 21 selected;
- `mvn -B clean package` passed;
- Flyway migration succeeded;
- new Container App revision became ready;
- `/actuator/health` returned `UP`.

### Chef test data

Existing menu items may become unavailable because Craves deliberately refuses to invent package details. For each test menu item, submit a real value through the chef menu-item update API:

```json
{
  "unitPackageWeightGrams": 650,
  "thermoboxRequired": false
}
```

Include all other required menu-item fields in the existing PUT request.

### No secret action

No new secret or credential is needed for this module.

## Local verification

```bash
cd services/catalog-service
mvn -B clean test

cd ../order-service
mvn -B clean test

cd ../integration-service
mvn -B clean test
```

The Azure DevOps Maven stages remain the authoritative build gates when Maven is unavailable in the working environment.

## Pending next module

Dynamic package metadata is complete, but automatic delivery publication is deliberately not enabled yet.

The next module is:

```text
Customer chooses saved deliveryAddressId at checkout
    ↓
Order Service snapshots customer drop-off details
    ↓
Order Service snapshots chef pickup details
    ↓
Chef accepts order and ready_at is persisted
    ↓
Order status + CHEF_ACCEPTED_ORDER outbox row commit atomically
    ↓
Outbox dispatcher publishes to craves-domain-events
    ↓
Integration Service schedules the delivery command
```

The remaining implementation must include:

- saved customer address ID in checkout;
- immutable customer drop-off snapshot;
- immutable kitchen pickup snapshot;
- transactional domain-event outbox;
- managed-identity Service Bus publisher;
- duplicate-safe publication;
- controlled end-to-end sandbox test;
- processors and Borzo remaining disabled until that test window.
