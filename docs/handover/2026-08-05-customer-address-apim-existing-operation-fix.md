# Customer Address APIM Existing-Operation Repair

Date: 2026-08-05  
Scope: existing Craves customer-address APIM operations

## Reported pipeline failure

The guarded customer-address APIM pipeline failed with:

```text
Operation with the same method and URL template already exists
```

This proves that at least one matching address operation already existed in the live APIM API. The failure did not indicate that the User/Chef address backend was absent. It showed that the configuration script attempted to create its preferred operation ID while APIM already owned the same HTTP method and URL template under another operation ID.

## Root cause

The script used fixed operation IDs such as:

```text
list-customer-addresses
create-customer-address
```

Azure API Management enforces uniqueness by HTTP method plus URL template. An existing operation may therefore be valid even when its operation ID differs from the new preferred ID. A PUT to a new ID then fails with a duplicate method/template validation error.

## Repair

The APIM script now:

1. Lists existing operations in the selected customer API.
2. Matches operations by HTTP method and normalized URL template.
3. Reuses the existing operation ID when exactly one matching operation exists.
4. Creates the preferred operation ID only when no matching operation exists.
5. Fails closed when multiple operations unexpectedly match.
6. Fails closed when a preferred operation ID already belongs to another route.
7. Updates the operation and policy in place.
8. Never deletes an API or operation.
9. Verifies policies using the actual configured operation IDs.

## Safety

- No new Azure resource is created.
- No API or operation is deleted.
- Existing matching operation IDs are preserved.
- Existing unrelated operations are not overwritten.
- No secret or environment variable is added.
- The same pipeline parameters remain valid.

## Required execution after merge

Run:

```text
/azure-pipelines-customer-addresses-apim.yml
```

Branch:

```text
main
```

Parameters:

```text
confirmConfigureCustomerAddresses: true
resourceGroupName: rg-craves-prodlow-centralindia
apimServiceName: apim-craves-prodlow-l3ing6
userChefContainerAppName: ca-craves-user-chef-service-prod
```

Expected logs may contain:

```text
Reusing existing APIM operation <existing-id> for GET /addresses
```

The successful final probes remain:

```text
Unauthenticated address probes: GET=401 POST=401
SUCCESS: Customer address GET/POST routes exist in APIM and enforce Bearer authentication.
```

## Next step after success

Run the existing customer web deployment pipeline to publish the approved logo:

```text
/azure-pipelines-customer-web-next-delivery-tracking.yml
```

Use:

```text
confirmReplaceCurrentCustomerWeb: true
cashfreeMode: sandbox
```
