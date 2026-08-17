# Order Delivery Status RBAC CLI Correction

Date: 2026-08-07

## Problem

The Order delivery-status consumer activation pipeline failed during the managed-identity RBAC check with:

```text
ERROR: group or scope are not required when --all is used
```

The pipeline combined `az role assignment list --scope ...` with `--all`.

## Correction

The RBAC check now uses a scoped query with `--include-inherited` and does not pass `--all`:

```text
az role assignment list
  --assignee-object-id <order-principal-id>
  --scope <service-bus-subscription-resource-id>
  --role "Azure Service Bus Data Receiver"
  --include-inherited
```

This checks direct assignments at the Service Bus subscription scope and inherited assignments from parent scopes without requesting subscription-wide enumeration.

## Safety

The activation pipeline still:

- leaves the Service Bus subscription Disabled when receiver RBAC is absent;
- keeps Integration delivery-status publication disabled;
- keeps delivery command/webhook/tracking execution disabled;
- keeps Borzo disabled;
- reads no credential values;
- changes no Key Vault secrets;
- verifies all active Order secret references are Key Vault-backed;
- changes only the four Order delivery-status consumer controls;
- validates unrelated Order runtime configuration and secret metadata remain unchanged;
- validates Order liveness/readiness after activation.

No new Azure namespace, SKU, credential, provider activation or paid resource is introduced by this correction.
