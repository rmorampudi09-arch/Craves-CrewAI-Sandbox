# Catalog Discovery APIM Handover

Date: 2026-07-16  
Service: Catalog Service  
Azure API Management: `apim-craves-prodlow-l3ing6`

## Confirmed backend state

The Catalog Container App direct endpoint returned HTTP 200:

```http
GET /api/v1/discovery/kitchens?latitude=17.4483&longitude=78.3915&radiusMeters=5000&page=0&size=20
```

An empty result is valid when no active geocoded kitchens with sellable menu items exist.

## APIM issue

Calling the same path through APIM returned:

```text
404 Resource Not Found
```

This meant the backend was healthy but APIM did not contain matching API operations.

## APIM design

API identifier:

```text
craves-catalog-discovery-v1
```

Public APIM path:

```text
api/v1/discovery
```

Backend service URL:

```text
https://<catalog-container-app-fqdn>/api/v1/discovery
```

Subscription key requirement:

```text
false
```

Operations:

```text
GET /kitchens
GET /menu-items
```

Query strings such as `latitude`, `longitude`, `radiusMeters`, `page`, and `size` are forwarded to the Catalog backend.

## Repeatable configuration script

Added:

```text
scripts/configure-catalog-discovery-apim.sh
```

The script:

1. Resolves the existing Catalog Container App FQDN.
2. Verifies the Catalog health endpoint.
3. Creates or updates the APIM API.
4. Creates or replaces the two GET operations.
5. Lists the resulting API and operations for verification.

The script is idempotent and can be rerun after APIM drift or recreation.

## Manual execution

From a clone of the repository:

```bash
bash scripts/configure-catalog-discovery-apim.sh
```

Defaults:

```text
RG=rg-craves-prodlow-centralindia
APIM=apim-craves-prodlow-l3ing6
CATALOG_APP=ca-craves-catalog-service-prodlo
```

These can be overridden with environment variables.

## Smoke test

```bash
APIM_URL="https://api.craves.in"

curl -sS -i \
  "$APIM_URL/api/v1/discovery/kitchens?latitude=17.4483&longitude=78.3915&radiusMeters=5000&page=0&size=20"

curl -sS -i \
  "$APIM_URL/api/v1/discovery/menu-items?latitude=17.4483&longitude=78.3915&radiusMeters=5000&page=0&size=20"
```

Expected status:

```text
HTTP 200
```

Empty arrays are valid until suitable active kitchen and menu test data exists.

## Security boundary

The discovery endpoints are intentionally public for browsing. Chef management endpoints remain protected and are not added to this public APIM API.

## Excluded decisions

This configuration does not define delivery serviceability, delivery fees, provider assignment radius, pricing zones, commissions, or compliance rules.

## Azure impact

No new paid Azure resource is created. The change only updates configuration inside the existing APIM instance.
