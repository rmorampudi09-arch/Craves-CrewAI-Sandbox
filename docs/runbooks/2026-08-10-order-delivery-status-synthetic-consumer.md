# Order DELIVERY_STATUS_CHANGED Synthetic Consumer Validation

Date: 2026-08-10  
Environment: prod-low  
Scope: Order Service downstream delivery-status consumer only

## Purpose

This runbook validates the already-enabled Order Service `DELIVERY_STATUS_CHANGED` consumer using isolated synthetic records and synthetic Service Bus events.

It deliberately does **not** use a real Craves customer order, real chef order, real customer/chef identity, address, phone number or other PII.

It also deliberately does **not** enable Integration Service delivery publication, delivery command execution, reconciliation, webhook processing, tracking reconciliation or Borzo.

## Files

- `scripts/release/validate-order-delivery-status-synthetic-consumer.sh`
- `azure-pipelines-order-delivery-status-synthetic-consumer.yml`
- `docs/runbooks/2026-08-10-order-delivery-status-synthetic-consumer.md`

## Why real orders are excluded

Earlier Craves delivery operational guidance treats accidental use of a real customer order or PII as a stop condition. The validator therefore generates all identifiers at runtime and inserts one temporary `order_schema.customer_order` row marked by the kitchen snapshot `Craves Synthetic Delivery Validator`.

No existing Order row is selected or modified.

## Required starting state

The script fails before creating test data unless all of the following are true:

- Order latest revision equals latest-ready revision and is `Running`;
- Order `CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED=true`;
- Order notification outbox dispatcher is false/absent;
- Order direct notification dispatch is false/absent;
- Integration latest revision equals latest-ready revision and is `Running`;
- Integration delivery-status publisher is false/absent;
- Integration delivery command worker is false/absent;
- Integration delivery reconciliation is false/absent;
- Integration delivery webhook processing is false/absent;
- Integration delivery tracking reconciliation is false/absent;
- `BORZO_API_ENABLED` is false/absent;
- Service Bus subscription `order-service-delivery-status-changed` is `Active`;
- subscription active message count is zero;
- subscription dead-letter count is zero;
- exactly one approved SQL filter exists;
- active Order `secretRef` bindings are Key Vault-backed with managed identity;
- Order PostgreSQL is reachable from the ephemeral Azure DevOps agent;
- required V9 delivery-status tables/columns exist;
- the live `customer_order` schema has no unexpected required column without a default.

No firewall rule or network setting is changed when database connectivity is unavailable.

## Confirmation guard

The pipeline parameter below defaults to false:

```text
confirmSyntheticValidation=false
```

With the default value, the pipeline only runs the shell syntax check and prints that execution is blocked.

Synthetic database/message execution occurs only when the operator explicitly selects:

```text
confirmSyntheticValidation=true
```

## Synthetic data

The script generates fresh UUIDs for:

- checkout/correlation ID;
- customer identity ID;
- chef sub-order ID;
- kitchen ID;
- delivery job ID;
- five event IDs.

The temporary Order row uses:

```text
status = CHEF_ACCEPTED
kitchen_name_snapshot = Craves Synthetic Delivery Validator
currency = INR
food_subtotal = 1.00
platform_fee = 0.00
tax_amount = 0.00
delivery_fee = 0.00
grand_total = 1.00
prep_time_minutes = 30
accepted_at = now - 10 minutes
ready_at = now + 20 minutes
```

Address, phone, email, pickup and dropoff snapshot fields remain null. These numbers are synthetic database-shape values only; they do not define Craves commercial pricing or fee policy.

## Service Bus sender credentials

The script does not create a role assignment or authorization rule.

It selects an already-existing namespace authorization rule that has `Send` or `Manage`, reads its connection string into an in-memory shell variable with `set +x`, sends the synthetic messages, then unsets the value. The connection string is never printed.

If no existing sender authorization is available, the test fails without changing Service Bus authorization.

## Event sequence

The test publishes provider-neutral v1 events with:

```text
eventType = DELIVERY_STATUS_CHANGED
eventVersion = 1.0
source = integration-service
subject = delivery-job/{syntheticDeliveryJobId}
providerId = synthetic-craves-validation
providerDeliveryId = synthetic-{deliveryJobId}
trackingUrl = null
```

Both Service Bus application properties are set for the current compatibility filter:

```text
event_type = DELIVERY_STATUS_CHANGED
eventType = DELIVERY_STATUS_CHANGED
```

The sequence is:

1. `PENDING` with event A -> expect `PROCESSED`, projection applied, one history row.
2. exact duplicate event A -> expect inbox/history cardinality unchanged.
3. newer `PENDING` event B -> expect `NO_CHANGE`, no new history.
4. older `SEARCHING` event C -> expect `STALE`, no new history.
5. newer `DELIVERED` event D -> expect `PROCESSED`, delivery projection becomes terminal, second history row, one pending notification-outbox row.
6. newer `IN_TRANSIT` event E -> expect `TERMINAL_PROTECTED`; projection remains `DELIVERED`, no additional history/notification.

The commercial `customer_order.status` must remain `CHEF_ACCEPTED` throughout. Provider delivery status is maintained only in the dedicated `delivery_*` projection columns.

## Notification safety

`DELIVERED` is intentionally included to verify the existing Order notification-outbox integration.

The pipeline requires the notification dispatcher to be disabled before testing. Therefore the synthetic notification must remain `PENDING` and is removed during cleanup; it is not externally dispatched.

## Success cleanup

After all assertions and a final zero-DLQ check, the script deletes only records connected to the generated synthetic chef sub-order ID:

1. synthetic delivery notification outbox record;
2. synthetic delivery history rows;
3. synthetic delivery-status inbox rows;
4. synthetic `customer_order` row.

It then verifies that zero matching synthetic rows remain.

No checkout row is created, so no checkout cleanup is required.

## Failure behavior

Before the first Service Bus message is sent, a failure removes the temporary synthetic Order row automatically.

After any Service Bus event has been sent, a failure preserves the generated synthetic rows rather than deleting an Order while a message may still be in flight. The pipeline prints only the generated synthetic UUIDs required for controlled forensic cleanup. It does not print credentials.

Do not rerun blindly after such a failure. Review the printed synthetic IDs, subscription active/DLQ counts and Order processor logs first.

## Expected success footer

```text
ORDER DELIVERY STATUS SYNTHETIC CONSUMER VALIDATION: PASS
Real customer order used:                 NO
Real PII used:                            NO
Initial status APPLY:                     PASS
Duplicate event idempotency:              PASS
NO_CHANGE protection:                     PASS
STALE protection:                         PASS
DELIVERED terminal projection:            PASS
TERMINAL_PROTECTED behavior:              PASS
Commercial Order status mutated:          NO
Synthetic notification externally sent:   NO
Delivery-status DLQ:                      0
Integration delivery-status publisher:    DISABLED
Borzo/provider execution:                 DISABLED
Synthetic database rows remaining:        0
Credential values printed:                NO
Credential rotation performed:            NO
Azure firewall/networking changed:        NO
New Service Bus role/auth rule created:   NO
```

## Manual steps required

Azure DevOps pipeline registration is required once after this change is merged:

1. Open project `Craves Full Build` -> Pipelines -> New pipeline.
2. Select GitHub and repository `rmorampudi09-arch/Craves-Build-platform`.
3. Choose an existing Azure Pipelines YAML file.
4. Select branch `main`.
5. YAML path: `/azure-pipelines-order-delivery-status-synthetic-consumer.yml`.
6. Suggested pipeline name: `Order Delivery Status - Synthetic Consumer Validation`.
7. Ensure the existing non-secret variable `AZURE_SERVICE_CONNECTION=Craves-Dev-Service-Connection` is available.
8. Queue the first execution and explicitly set `confirmSyntheticValidation=true` only after the post-activation rollout-status check remains clean.

No Azure Portal resource creation, DNS change, Key Vault mutation, credential rotation, provider configuration or billing-sensitive resource provisioning is required.

## Next step after PASS

Do not enable Borzo, webhook/tracking workers or delivery command execution.

A successful synthetic Order consumer validation provides evidence to consider the separately guarded Integration delivery-status publisher activation. That publisher activation remains a distinct controlled step and must still verify the Order consumer, filter and DLQ state before changing its own runtime flag.
