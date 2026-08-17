# Craves Order Internal Address Runtime Configuration Hotfix

Date: 2026-07-16  
Branch: `feature/order-internal-address-runtime-config`  
Affected service: Order Service  
Affected dependency: User-Chef Service internal customer-address endpoint

## Incident summary

The Order checkout address-snapshot code was deployed successfully and the negative checkout test passed:

```json
{
  "error": "DELIVERY_ADDRESS_REQUIRED",
  "message": "Save the current location or select a saved delivery address before placing the order."
}
```

A complete saved customer address, active kitchen, and sellable menu item were then prepared. The cart accepted the item successfully, but valid checkout returned:

```json
{
  "error": "DELIVERY_ADDRESS_LOOKUP_UNAVAILABLE",
  "message": "Delivery address verification is temporarily unavailable."
}
```

## Runtime diagnosis

The deployed Order Container App did not contain either required setting:

```text
CRAVES_USER_CHEF_INTERNAL_BASE_URL
CRAVES_INTERNAL_SERVICE_SECRET
```

The running User-Chef Container App was confirmed as:

```text
ca-craves-user-chef-service-prod
https://ca-craves-user-chef-service-prod.happysand-aedc7165.centralindia.azurecontainerapps.io
```

User-Chef already used the shared Azure DevOps variable:

```text
CRAVES_INTERNAL_SERVICE_SECRET
```

and exposed it through this Container Apps secret reference:

```text
craves-internal-service-secret
```

The Order pipeline previously updated only the container image. It did not create the matching Order secret reference or bind the two internal-address environment variables.

## Root cause

This was a deployment configuration gap, not a checkout business-logic failure.

The Order Service code correctly failed closed when its internal address client had no usable service URL or shared credential. The checkout transaction did not create a checkout or clear the cart.

## Files changed

```text
azure-pipelines-order-service.yml
docs/handover/2026-07-16-order-internal-address-runtime-config.md
```

## Pipeline changes

The Order pipeline now:

1. reads the existing secret Azure DevOps variable `CRAVES_INTERNAL_SERVICE_SECRET` through a task environment variable;
2. fails explicitly when the variable is missing or unavailable;
3. writes the value into the Order Container App secret named `craves-internal-service-secret`;
4. sets `CRAVES_USER_CHEF_INTERNAL_BASE_URL` to the deployed User-Chef service root;
5. sets `CRAVES_INTERNAL_SERVICE_SECRET=secretref:craves-internal-service-secret`;
6. deploys the Order image;
7. waits until the latest revision is both ready and healthy;
8. prints only safe runtime configuration metadata: the service URL and secret-reference name.

The pipeline never prints the shared secret value.

## Security handling

The secret is not committed to GitHub.

The secret must remain stored as the Azure DevOps secret variable:

```text
CRAVES_INTERNAL_SERVICE_SECRET
```

The Order and User-Chef services must receive the same value.

Do not paste the value into chat, logs, documentation, source control, or shell history.

## Deployment procedure

After merging the pull request, run:

```text
azure-pipelines-order-service.yml
```

Use:

```text
branch = main
existing default parameters
```

Expected pipeline stages:

```text
Use Java 21
Maven build and tests
Build and push image to ACR
Configure Order internal service secret
Deploy and verify Container App revision
```

Expected safe runtime configuration output:

```json
[
  {
    "name": "CRAVES_USER_CHEF_INTERNAL_BASE_URL",
    "value": "https://ca-craves-user-chef-service-prod.happysand-aedc7165.centralindia.azurecontainerapps.io",
    "secretRef": null
  },
  {
    "name": "CRAVES_INTERNAL_SERVICE_SECRET",
    "value": null,
    "secretRef": "craves-internal-service-secret"
  }
]
```

## Post-deployment checkout test

Use the existing customer token locally without sharing it.

```bash
APIM_URL="https://api.craves.in"
DELIVERY_ADDRESS_ID="b8c25f7c-8535-4765-ab2b-f3ba930124a6"

curl -sS -i \
  --connect-timeout 10 \
  --max-time 45 \
  -X POST "$APIM_URL/api/v1/checkout" \
  -H "Authorization: Bearer $CRAVES_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"deliveryAddressId\": \"$DELIVERY_ADDRESS_ID\",
    \"note\": \"Order address snapshot production smoke test\"
  }"
```

Expected successful behavior:

```text
HTTP 200
checkout status = PAYMENT_PENDING
food subtotal = 220.00 INR
deliveryAddressId is present
customer delivery snapshot is present
one kitchen-specific order is present
pickup private details are absent from the customer JSON response
cart is cleared only after successful checkout
```

## Failure interpretation

```text
DELIVERY_ADDRESS_LOOKUP_UNAUTHORIZED
    The two services do not have the same shared secret value.

DELIVERY_ADDRESS_LOOKUP_UNAVAILABLE
    User-Chef URL is unreachable, the setting is missing, or the downstream service failed.

DELIVERY_ADDRESS_NOT_AVAILABLE
    Address is inactive or does not belong to the authenticated customer.

DELIVERY_ADDRESS_INCOMPLETE
    Saved address is missing required delivery fields.

KITCHEN_PICKUP_ADDRESS_INCOMPLETE
    Kitchen profile is missing required pickup fields.
```

## Manual steps required

### Azure DevOps

Confirm the Order pipeline can access the existing secret variable:

```text
CRAVES_INTERNAL_SERVICE_SECRET
```

Do not create a different value for Order Service. It must match User-Chef Service.

Run the Order pipeline after merge.

### Azure Portal

No resource creation is required.

No paid SKU is added.

No manual Container App secret editing is required when the pipeline succeeds.

### Local smoke test

Load a fresh Craves access token into the shell and rerun the valid checkout request.

## Pending work

After successful checkout verification:

```text
Order Service chef acceptance
    -> transactional CHEF_ACCEPTED_ORDER domain outbox
    -> managed-identity Service Bus publication
    -> Integration Service scheduling near ready_at
```

External delivery creation remains disabled until the controlled end-to-end delivery test is approved.
