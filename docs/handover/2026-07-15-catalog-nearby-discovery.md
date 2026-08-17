# Craves Catalog Nearby Discovery Handover

Date: 2026-07-15  
Branch: `feature/catalog-nearby-discovery`  
Primary service: Catalog Service  
Stack: Spring Boot 3, Java 21, PostgreSQL, PostGIS, Azure Container Apps

## Product decision implemented

Krishna approved the recommended coordinate-based discovery contract:

```http
GET /api/v1/discovery/kitchens
    ?latitude={latitude}
    &longitude={longitude}
    &radiusMeters={radiusMeters}
    &page={page}
    &size={size}
```

The agreed normal example is:

```http
GET /api/v1/discovery/kitchens
    ?latitude=17.4483
    &longitude=78.3915
    &radiusMeters=5000
    &page=0
    &size=20
```

The caller supplies the browsing radius rather than Catalog Service permanently hard-coding one product radius.

## Important business separation

The request field:

```text
radiusMeters
```

means only:

```text
how far Catalog Service should search for browsing results
```

It does not mean:

```text
confirmed delivery serviceability
chef delivery commitment
delivery fee distance
provider assignment radius
pricing zone
commission zone
```

Those remain separate product and operational decisions.

## End-to-end location flow

```text
Customer opens Craves
    ↓
Mobile or web obtains live GPS
    ↓
User-Chef Service recommends SAVED_ADDRESS or LIVE_GPS
    ↓
The selected active latitude and longitude are sent to Catalog Service
    ↓
The client supplies radiusMeters
    ↓
Catalog Service uses PostGIS ST_DWithin
    ↓
Only active geocoded kitchens inside the radius remain
    ↓
Kitchens without active sellable menu items are removed
    ↓
PostGIS ST_Distance calculates metres
    ↓
Results are sorted nearest first
    ↓
Pagination limits the response
```

## APIs added

### Nearby kitchens

```http
GET /api/v1/discovery/kitchens
```

Required query parameters:

```text
latitude
longitude
radiusMeters
```

Optional query parameters with defaults:

```text
page=0
size=20
```

### Nearby menu items

```http
GET /api/v1/discovery/menu-items
```

It uses the same required coordinates, radius, and pagination model.

The menu-item result contains:

```text
menu item ID
kitchen ID
kitchen name and display name
kitchen area, city, and state
kitchen coordinates
distance in metres
item name and description
category and food type
price and currency
serves count
preparation time
spice level
packaged weight in grams
thermobox requirement
primary image URL
```

## Existing APIs preserved

The module does not remove or change the existing compatibility routes:

```http
GET /api/v1/catalog/kitchens
GET /api/v1/catalog/kitchens/{kitchenId}
GET /api/v1/catalog/kitchens/{kitchenId}/menu-items
GET /api/v1/catalog/menu-items/{menuItemId}
```

New app-launch browsing should use:

```text
/api/v1/discovery/**
```

## Response contracts

Added file:

```text
services/catalog-service/src/main/java/in/craves/catalog/web/DiscoveryDtos.java
```

Records:

```text
PageMetadata
NearbyKitchenSummaryResponse
NearbyKitchenDiscoveryResponse
NearbyMenuItemSummaryResponse
NearbyMenuItemDiscoveryResponse
```

### Pagination response

```json
{
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3,
  "hasNext": true
}
```

### Kitchen response example

```json
{
  "latitude": 17.4483,
  "longitude": 78.3915,
  "radiusMeters": 5000,
  "page": {
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1,
    "hasNext": false
  },
  "kitchens": [
    {
      "id": "kitchen-uuid",
      "kitchenName": "Home Kitchen",
      "displayName": "Home Kitchen",
      "description": "Fresh home food",
      "areaName": "Madhapur",
      "city": "Hyderabad",
      "state": "Telangana",
      "latitude": 17.4480,
      "longitude": 78.3920,
      "distanceMeters": 74,
      "activeMenuItemCount": 8
    }
  ]
}
```

## Service implementation

Added file:

```text
services/catalog-service/src/main/java/in/craves/catalog/service/NearbyDiscoveryService.java
```

Responsibilities:

```text
validate coordinates
validate radius
validate page and size
count matching kitchens
query one kitchen page
count matching menu items
query one menu-item page
calculate pagination metadata
map SQL results into API records
```

## Kitchen query rules

A kitchen is returned only when:

```text
kitchen status = ACTIVE
kitchen has generated PostGIS location
kitchen is inside radiusMeters
kitchen has at least one active and available menu item
sellable menu item has package weight
sellable menu item has thermobox decision
```

Sorting:

```text
1. distance ascending
2. kitchen UUID ascending
```

The UUID tie-breaker keeps pagination stable when two kitchens have the same rounded distance.

## Menu-item query rules

A menu item is returned only when:

```text
kitchen status = ACTIVE
kitchen has generated PostGIS location
kitchen is inside radiusMeters
menu status = ACTIVE
menu is_available = true
unit package weight is present
thermobox decision is present
```

Sorting:

```text
1. kitchen distance ascending
2. category ascending
3. item name ascending
4. menu-item UUID ascending
```

## Image-query design

Nearby menu discovery does not call the existing image-list query once per menu item.

Instead, the SQL selects one image URL through a correlated subquery:

```text
primary image first
then sort order
then creation time
limit 1
```

This prevents an N+1 image-query pattern for discovery pages.

Future high-volume optimization may denormalize the primary image URL or use a materialized discovery projection, but the current query is safe for the present low-capacity rollout.

## Validation rules

Coordinates:

```text
latitude required
latitude between -90 and 90
longitude required
longitude between -180 and 180
```

Radius:

```text
radiusMeters required
radiusMeters greater than zero
radiusMeters not above technical query maximum
```

Pagination:

```text
page >= 0
size >= 1
size <= configured maximum
```

Error codes:

```text
LATITUDE_REQUIRED
LONGITUDE_REQUIRED
INVALID_LATITUDE
INVALID_LONGITUDE
INVALID_RADIUS
RADIUS_TOO_LARGE
INVALID_PAGE
INVALID_PAGE_SIZE
```

## Technical query guards

Updated:

```text
services/catalog-service/src/main/java/in/craves/catalog/config/CatalogDiscoveryProperties.java
services/catalog-service/src/main/resources/application.yml
```

Added configuration:

```text
CRAVES_DISCOVERY_MAX_QUERY_RADIUS_METERS
CRAVES_DISCOVERY_MAX_PAGE_SIZE
```

Defaults:

```text
max query radius = 50000 metres
max page size = 100
```

These values protect the database from unusually broad or oversized requests.

They are not customer delivery rules.

The standard app request can use:

```text
radiusMeters=5000
size=20
```

without changing the technical guard values.

## Controller

Added:

```text
services/catalog-service/src/main/java/in/craves/catalog/web/NearbyDiscoveryController.java
```

Controller mapping:

```text
/api/v1/discovery
```

Methods:

```text
GET /kitchens
GET /menu-items
```

## Security

Updated:

```text
services/catalog-service/src/main/java/in/craves/catalog/security/SecurityConfig.java
```

Public paths now include:

```text
/api/v1/catalog/**
/api/v1/discovery/**
```

Chef management endpoints remain authenticated.

Public discovery is intentionally available without a Craves access token so the app can show food discovery before or during customer sign-in.

## Flyway migration

Added:

```text
services/catalog-service/src/main/resources/db/migration/V3__kitchen_geography_discovery.sql
```

The migration:

```text
ensures PostGIS is installed
adds generated geography column
adds coordinate-pair validation
adds latitude-range validation
adds longitude-range validation
adds partial GiST location index
adds partial sellable-menu discovery index
```

### Generated column

```text
catalog_schema.kitchen_profile.location
```

Type:

```text
public.geography(Point, 4326)
```

Generation:

```text
longitude + latitude
    ↓
public.ST_MakePoint
    ↓
public.ST_SetSRID(..., 4326)
    ↓
public.geography
```

PostGIS objects are explicitly qualified with the `public` schema so Flyway's Catalog default schema does not interfere with type or function resolution.

### Coordinate constraints

The coordinate-pair constraint allows only:

```text
both latitude and longitude absent
or
both latitude and longitude present
```

Range constraints:

```text
latitude  -90 to 90
longitude -180 to 180
```

The constraints are added `NOT VALID` so unknown legacy rows do not block deployment. PostgreSQL still enforces them for new and updated rows.

### Geography index

```text
idx_catalog_kitchen_active_location
```

Type:

```text
partial GiST
```

Included rows:

```text
status = ACTIVE
location is not null
```

This index supports `ST_DWithin` radius filtering.

### Menu discovery index

```text
idx_catalog_menu_item_discovery
```

Included rows:

```text
status = ACTIVE
is_available = true
package weight is present
thermobox decision is present
```

## Legacy-data behavior

No kitchen coordinate is guessed.

No area is guessed.

Existing kitchens without coordinates remain stored but do not appear in coordinate-based discovery.

Existing APIs can still read them where allowed.

A chef must provide real latitude and longitude before the kitchen can appear in the new nearby results.

## Tests

Added:

```text
services/catalog-service/src/test/java/in/craves/catalog/service/NearbyDiscoveryServiceValidationTest.java
```

Coverage:

```text
missing latitude
invalid longitude
zero radius
radius beyond technical maximum
negative page
page size beyond maximum
```

The tests invoke validation before a database query is made.

## README

Updated:

```text
services/catalog-service/README.md
```

It now documents:

```text
app-launch discovery flow
nearby kitchen endpoint
nearby menu-item endpoint
response examples
validation rules
technical guards
PostGIS migration
legacy compatibility
local setup
deployment checks
excluded business rules
```

## Files changed

```text
services/catalog-service/src/main/java/in/craves/catalog/config/CatalogDiscoveryProperties.java
services/catalog-service/src/main/java/in/craves/catalog/security/SecurityConfig.java
services/catalog-service/src/main/java/in/craves/catalog/service/NearbyDiscoveryService.java
services/catalog-service/src/main/java/in/craves/catalog/web/DiscoveryDtos.java
services/catalog-service/src/main/java/in/craves/catalog/web/NearbyDiscoveryController.java
services/catalog-service/src/main/resources/application.yml
services/catalog-service/src/main/resources/db/migration/V3__kitchen_geography_discovery.sql
services/catalog-service/src/test/java/in/craves/catalog/service/NearbyDiscoveryServiceValidationTest.java
services/catalog-service/README.md
docs/handover/2026-07-15-catalog-nearby-discovery.md
```

## Build commands

```bash
cd services/catalog-service
mvn -B clean test
mvn -B clean package
```

## Deployment procedure

Run:

```text
azure-pipelines-catalog-service.yml
```

Use:

```text
branch = main
existing default pipeline parameters
```

Expected stages:

```text
Use Java 21
Maven build and tests
Build and push image to ACR
Update Container App image
```

## Deployment verification

Confirm:

```text
Maven tests passed
Maven package passed
Flyway V3 completed
PostGIS extension available
new Container App revision Ready
/actuator/health returns UP
```

Runtime endpoint testing can wait until suitable kitchen and menu test data exists.

Because discovery endpoints are public, they do not require a Craves access token after deployment.

## Runtime smoke-test commands

After deployment, a public request can be tested through APIM:

```bash
APIM_URL="https://api.craves.in"

curl -sS -i \
  "$APIM_URL/api/v1/discovery/kitchens?latitude=17.4483&longitude=78.3915&radiusMeters=5000&page=0&size=20"
```

Menu discovery:

```bash
curl -sS -i \
  "$APIM_URL/api/v1/discovery/menu-items?latitude=17.4483&longitude=78.3915&radiusMeters=5000&page=0&size=20"
```

An empty list is valid when no active geocoded kitchen with a sellable menu item exists inside the supplied radius.

## APIM consideration

The backend route is added by this module.

If APIM uses explicit operations rather than a catch-all backend route, these operations may need to be added manually:

```text
GET /api/v1/discovery/kitchens
GET /api/v1/discovery/menu-items
```

Do not create new APIM resources. Add operations only when the current APIM configuration does not forward these paths automatically.

## Azure impact

No new Azure resource is required.

No new paid SKU is required.

No new secret is required.

No Key Vault value is required.

No Service Bus entity is required.

Optional non-secret Container App settings may be added later:

```text
CRAVES_DISCOVERY_MAX_QUERY_RADIUS_METERS
CRAVES_DISCOVERY_MAX_PAGE_SIZE
```

The application defaults work without adding them.

## Safety settings

Keep Integration Service settings unchanged:

```text
CRAVES_DELIVERY_COMMAND_ENABLED=false
CRAVES_DELIVERY_INTELLIGENCE_ENABLED=true
BORZO_API_ENABLED=false
```

This Catalog deployment does not enable provider calls or automatic delivery booking.

## Scalability notes

Current design is appropriate for the present low-capacity rollout because:

```text
PostGIS GiST index limits geography scanning
partial menu index limits sellable-item scans
responses are paginated
page size is bounded
query radius is bounded
menu image selection avoids one query per row
```

Before approaching the stated long-term million-concurrent-user target, Craves will require:

```text
load testing by city and density
read replicas or dedicated read paths
cache strategy using Azure Managed Redis after cost review
precomputed discovery projections
hot-area partitioning strategy
CDN image optimization
request throttling at APIM or Front Door
cursor pagination for very deep result sets
observability for query latency and index hit ratio
```

Offset pagination is acceptable now but becomes inefficient at very deep pages. App browsing usually reads early pages, so this is not an immediate blocker.

## Deliberately excluded

No rule was invented for:

```text
chef delivery radius
final checkout serviceability
delivery fee calculation
surge pricing
commission
sponsored ranking
personalized recommendations
FSSAI compliance
GST or invoicing
maximum kitchen distance commitment
```

## Next engineering module

After Catalog deployment succeeds:

```text
Order Service checkout
    ↓
Require deliveryAddressId
    ↓
Call User-Chef internal address lookup
    ↓
Verify address belongs to authenticated customer
    ↓
Verify address is active
    ↓
Snapshot customer drop-off details
    ↓
Snapshot kitchen pickup details
    ↓
Create order using immutable delivery addresses
```

After checkout snapshots:

```text
chef accepts order
    ↓
order status and CHEF_ACCEPTED_ORDER outbox commit together
    ↓
managed-identity publisher sends domain event
    ↓
Integration Service schedules delivery near ready_at
```

Automatic delivery booking must remain disabled until the controlled end-to-end test window.
