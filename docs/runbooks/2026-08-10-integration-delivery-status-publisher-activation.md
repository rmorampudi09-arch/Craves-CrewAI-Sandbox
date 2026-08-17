# Integration DELIVERY_STATUS_CHANGED Publisher Activation

Date: 2026-08-10
Environment: prodlow

## Purpose

Enable only the Integration Service `DELIVERY_STATUS_CHANGED` outbox publisher after the Order delivery-status consumer and isolated synthetic consumer validation have passed.

## Files

- `azure-pipelines-integration-delivery-status-publisher-enable.yml`
- `scripts/release/integration-delivery-status-publisher-enable-v2.sh`

## Required preconditions

The activation helper fails closed unless all of the following are true:

- Order Container App is Running and latest revision is Ready.
- Order `CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED=true`.
- Integration Container App is Running and latest revision is Ready.
- Integration delivery command remains false/absent.
- Integration create reconciliation remains false/absent.
- Integration webhook processing remains false/absent.
- Integration tracking reconciliation remains false/absent.
- Borzo remains false/absent.
- Integration `SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE` already points to the approved Service Bus namespace.
- Integration `SERVICE_BUS_TOPIC_NAME`, if explicitly present, points to `craves-domain-events`.
- Order delivery-status subscription is Active.
- Order delivery-status subscription has zero active messages and zero dead letters before activation.
- Exactly one approved `delivery-status-changed-only` SQL filter exists.
- All active Integration secret references are Azure Key Vault-backed.
- Integration system-assigned managed identity has `Azure Service Bus Data Sender` at the topic scope or an inherited parent scope.

## Explicit confirmation

The Azure DevOps parameter `confirmPublisherActivation` defaults to `false`. Set it to `true` only for the controlled activation run.

## Runtime mutation

The activation changes only:

```text
CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED=true
```

It does not rewrite Service Bus configuration, provider flags, delivery workers, credentials, secrets, ingress or scaling.

## Preservation checks

Before activation the helper fingerprints:

- all Integration environment variables except the publisher flag;
- Container Apps configuration excluding Azure-managed ingress traffic revision bookkeeping;
- template/image/resources/scaling/probes excluding env and revision suffix;
- managed identity;
- Container App Key Vault secret metadata.

The same fingerprints are recomputed after the new revision is healthy. Any unrelated drift triggers publisher rollback.

## RBAC check

The helper queries the Service Bus topic scope using `--include-inherited`, so a valid `Azure Service Bus Data Sender` assignment at the topic, namespace, resource-group or subscription parent scope can satisfy the check.

If Sender RBAC is missing, the pipeline exits before changing the publisher and prints:

```text
PRINCIPAL_ID=...
TOPIC_SCOPE=...
```

No role assignment is created automatically.

## Rollback

If the new Integration revision does not become healthy or any preservation check fails, the helper restores the publisher flag to its exact previous state (previous value or absent).

## Explicitly unchanged

- Borzo/provider execution remains disabled.
- Delivery command remains disabled.
- Delivery create reconciliation remains disabled.
- Delivery webhook processing remains disabled.
- Delivery tracking reconciliation remains disabled.
- No credential rotation occurs.
- No Key Vault secret value is changed.
- No Azure resource or RBAC assignment is created.
- No pricing, commission, serviceability or compliance rule is changed.

## Next step after PASS

Rerun the read-only delivery-status rollout status pipeline. Then perform the separate controlled end-to-end synthetic publication validation before any provider webhook/tracking/Borzo activation.
