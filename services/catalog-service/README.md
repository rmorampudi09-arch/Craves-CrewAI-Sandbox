# Craves Catalog Service

Catalog Service owns kitchen profiles, menu items, public media metadata, package-handling metadata, and coordinate-based nearby discovery for Craves.

## Current scope

- Approved `CHEF` users can create or update one kitchen profile.
- Chefs can create, update, publish, and temporarily disable menu items.
- Every menu item has an explicit packaged weight in grams.
- Every menu item has an explicit thermobox requirement.
- Public discovery returns active kitchens with at least one active and available menu item.
- Nearby discovery uses PostGIS geography queries and sorts results nearest first.
- Nearby kitchen and menu-item endpoints support page and size parameters.
- Existing `/api/v1/catalog/**` endpoints remain available for compatibility.

## App-launch discovery flow

```text
Customer opens app
    -> app obtains active latitude and longitude
    -> active location may be SAVED_ADDRESS or LIVE_GPS
    -> app supplies radiusMeters
    -> Catalog Service applies PostGIS ST_DWithin
    -> results are ordered by ST_Distance
    -> only active kitchens and sellable menu items are returned
```

`radiusMeters` is a browsing-query radius supplied by the caller. It does not confirm final delivery serviceability, determine delivery fees, or define a permanent chef delivery radius.

## Nearby discovery endpoints

### Nearby kitchens

```http
GET /api/v1/discovery/kitchens
    ?latitude=17.4483
    &longitude=78.3915
    &radiusMeters=5000
    &page=0
    &size=20
```

Example response shape:

```json
{
  "latitude": 17.4483,
  "longitude": 78.3915,
  "radiusMeters": 5000,
  "page": {
    "page": 0,
    "size": 20,
    "totalElements": 3,
    "totalPages": 1,
    "hasNext": false
  },
  "kitchens": [
    {
      "id": "kitchen-uuid",
      "kitchenName": "Home Kitchen",
      "displayName": "Home Kitchen",
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

### Nearby menu items

```http
GET /api/v1/discovery/menu-items
    ?latitude=17.4483
    &longitude=78.3915
    &radiusMeters=5000
    &page=0
    &size=20
```

The menu response includes:

```text
menu-item identity and display fields
price and currency
food type and category
serves count and preparation time
package weight in grams
thermobox requirement
primary image URL
kitchen identity and display fields
kitchen coordinates
distance in metres
```

The query retrieves one primary image URL directly in SQL. It does not issue a separate image query for every menu item.

## Discovery validation

Required:

```text
latitude
longitude
radiusMeters
```

Rules:

```text
latitude      = -90 through 90
longitude     = -180 through 180
radiusMeters  = greater than zero
page          = zero or greater
size          = 1 through configured maximum
```

Technical query guards:

```text
CRAVES_DISCOVERY_MAX_QUERY_RADIUS_METERS default = 50000
CRAVES_DISCOVERY_MAX_PAGE_SIZE default = 100
```

These are abuse and database-protection limits. They are not product serviceability rules. The normal app request may use `radiusMeters=5000`, as agreed, while mobile and web configuration can later change that request value.

## PostGIS migration

Flyway migration:

```text
V3__kitchen_geography_discovery.sql
```

It adds:

```text
catalog_schema.kitchen_profile.location geography(Point, 4326)
partial GiST index for active geocoded kitchens
partial menu-item discovery index
coordinate-pair check
latitude-range check
longitude-range check
```

The `location` column is generated from:

```text
longitude + latitude
    -> PostGIS point with SRID 4326
    -> geography(Point, 4326)
```

Legacy kitchens without coordinates remain stored, but they are excluded from coordinate-based discovery. No coordinates are guessed or backfilled.

## Menu delivery metadata

Every menu-item create or update request must include:

```json
{
  "unitPackageWeightGrams": 650,
  "thermoboxRequired": false
}
```

`unitPackageWeightGrams` is the packaged weight of one sellable unit. `thermoboxRequired` must be explicitly sent as `true` or `false`.

Existing incomplete legacy items remain unavailable until the chef supplies real package metadata.

## Chef endpoints

```http
GET    /api/v1/kitchens/me
PUT    /api/v1/kitchens/me
GET    /api/v1/kitchens/me/menu-items
POST   /api/v1/kitchens/me/menu-items
PUT    /api/v1/kitchens/me/menu-items/{menuItemId}
PATCH  /api/v1/kitchens/me/menu-items/{menuItemId}/availability
POST   /api/v1/kitchens/me/menu-items/{menuItemId}/images
```

These require a Craves access token containing the `CHEF` role.

Example menu-item request:

```json
{
  "itemName": "Home-style veg meal",
  "description": "Rice, dal, curry and curd",
  "category": "MEALS",
  "foodType": "VEG",
  "price": 199.00,
  "currency": "INR",
  "servesCount": 1,
  "preparationTimeMinutes": 30,
  "spiceLevel": "MEDIUM",
  "unitPackageWeightGrams": 650,
  "thermoboxRequired": false,
  "available": true,
  "status": "ACTIVE"
}
```

## Existing public catalog endpoints

```http
GET /api/v1/catalog/kitchens
GET /api/v1/catalog/kitchens/{kitchenId}
GET /api/v1/catalog/kitchens/{kitchenId}/menu-items
GET /api/v1/catalog/menu-items/{menuItemId}
```

These routes are retained so existing clients do not break. New app-launch location browsing should use `/api/v1/discovery/**`.

## Local run

```bash
cd services/catalog-service
mvn -B clean test
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Required environment variables:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
CRAVES_JWT_VERIFICATION_PEM_BASE64
CRAVES_STORAGE_ENDPOINT_VALUE
CRAVES_STORAGE_MEDIA_CONTAINER
CRAVES_MEDIA_PUBLIC_BASE_URL
```

Optional discovery configuration:

```text
CRAVES_DISCOVERY_DEFAULT_RADIUS_KM
CRAVES_DISCOVERY_MAX_RADIUS_KM
CRAVES_DISCOVERY_MAX_QUERY_RADIUS_METERS
CRAVES_DISCOVERY_MAX_PAGE_SIZE
```

The first two variables support the older compatibility endpoint. The metre-based variables protect the new `/api/v1/discovery/**` queries.

## Redis token-revocation health behavior

Catalog contains Redis-backed access-token revocation support, but the runtime feature remains disabled unless `CRAVES_TOKEN_REVOCATION_ENABLED=true` is explicitly configured.

Because `spring-boot-starter-data-redis` is present for that security capability, Spring Boot Actuator would otherwise auto-detect Redis and probe the default `localhost:6379` even when token revocation is disabled. Catalog therefore keeps the Redis Actuator health contribution disabled by default:

```text
CRAVES_REDIS_HEALTH_ENABLED=false
```

This prevents an unused Redis dependency from making the aggregate `/actuator/health` endpoint report `DOWN` while the application, PostgreSQL and readiness/liveness probes are healthy.

When Redis-backed token revocation is intentionally activated later, configure the real Azure Redis connection first and then enable both controls together:

```text
CRAVES_TOKEN_REVOCATION_ENABLED=true
CRAVES_REDIS_HEALTH_ENABLED=true
```

Do not enable the Redis health indicator before a reachable Redis endpoint is configured. This setting does not provision Redis, enable caching, rotate credentials, or change Catalog business behavior.

## Media design

Images are uploaded to Azure Blob Storage under paths such as:

```text
public/dishes/{kitchenId}/{menuItemId}/{assetId}-{filename}
```

PostgreSQL stores image metadata. Public discovery returns the stored public URL or the configured CDN/base URL.

## Deployment

Pipeline:

```text
azure-pipelines-catalog-service.yml
```

The Catalog deployment pipeline is image-only after the build. It does not read database, JWT, Firebase, provider, or other application credential values and it does not recreate local Container App secrets. Before deployment it fingerprints the current Container App environment and Key Vault secret metadata. After the new revision is Ready, it requires both fingerprints to match exactly and verifies that the datasource password remains Key Vault-backed. If readiness, health, environment preservation, or secret-metadata preservation fails, the pipeline restores the previous image and verifies the rollback fingerprints.

The only Azure DevOps connection variable required by this pipeline is:

```text
AZURE_SERVICE_CONNECTION=Craves-Dev-Service-Connection
```

Do not add `POSTGRES_BUSINESS_DB_PASSWORD` or `CRAVES_JWT_VERIFICATION_PEM_BASE64` merely for this pipeline; the running Container App already owns its secure runtime bindings and the deployment preserves them.

After deployment verify:

```text
Java 21 build passed
Maven tests passed
Flyway V3 completed
PostGIS is available
new Container App revision is Ready
runtime environment fingerprint is unchanged
Key Vault secret metadata fingerprint is unchanged
/actuator/health returns UP
/actuator/health/liveness returns UP
/actuator/health/readiness returns UP
```

No new Azure resource, secret, Key Vault entry, Redis instance, or Service Bus entity is required for this health correction.

## Deliberately excluded

This module does not define:

```text
final order serviceability
chef delivery radius
pricing or delivery fees by distance
commission rules
GST or invoice rules
FSSAI compliance rules
Redis caching
personalized ranking
sponsored ranking
```

The next module will make Order Service require a saved `deliveryAddressId` and create immutable pickup and drop-off snapshots.
