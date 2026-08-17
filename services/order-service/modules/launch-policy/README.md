# Order launch policy enforcement

This module makes launch serviceability and minimum-order behavior explicit, versioned and auditable without inventing Craves product values.

## API

```text
GET  /api/v1/admin/launch-policies
POST /api/v1/admin/launch-policies
POST /api/v1/admin/launch-policies/{policyId}/activate
```

Only an authenticated `ADMIN` may create or activate a policy. Creating a policy never activates it. Activation requires a reason and deactivates the previous policy transactionally.

## Checkout gate

The gate is disabled by default:

```text
CRAVES_LAUNCH_POLICY_ENFORCEMENT_ENABLED=false
```

When enabled, checkout fails closed unless exactly one active policy exists. It validates:

- backend cart subtotal against the configured minimum;
- complete customer and kitchen coordinates;
- every kitchen-to-drop-off distance against the configured maximum radius;
- INR as the currently supported checkout currency.

Charge calculation remains owned by the existing `charge_policy` table. This module does not choose fee, tax, cancellation or SLA values.

## Local test

```bash
cd services/order-service
mvn -B -ntp verify
```

## Manual activation later

1. Deploy the merged Order image with enforcement still false.
2. Create a policy through the ADMIN API using approved business values.
3. Activate the exact policy with an operational reason.
4. Test an inside-radius and outside-radius checkout.
5. Set `CRAVES_LAUNCH_POLICY_ENFORCEMENT_ENABLED=true` through the controlled Order deployment process.
6. Confirm the new revision and repeat both tests.

No default business values are supplied by source code.
