# Catalog PostGIS Azure Hotfix

Date: 2026-07-16  
Service: Catalog Service  
Branch: `hotfix/catalog-postgis-public-schema`

## Incident

Catalog revision `ca-craves-catalog-service-prodlo--0000006` did not become healthy after deployment.

Initial failure:

```text
ERROR: extension "postgis" is not allow-listed for "azure_pg_admin" users
```

Manual Azure configuration added `POSTGIS` to PostgreSQL Flexible Server parameter:

```text
azure.extensions = POSTGIS
```

After restart, Flyway reached the next failure:

```text
SQL State: 42P01
ERROR: relation "spatial_ref_sys" does not exist
```

## Root cause

Catalog Service configures Flyway with:

```text
default-schema: catalog_schema
schemas: catalog_schema
```

Migration V3 originally used:

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
```

Without an explicit extension schema, PostgreSQL uses the current default object-creation schema. Under the Catalog Flyway connection this was `catalog_schema`.

PostGIS core objects such as `spatial_ref_sys` must be installed consistently in the shared database `public` schema so every service can reference `public.geography` and `public.ST_*` objects.

## Code correction

Updated:

```text
services/catalog-service/src/main/resources/db/migration/V3__kitchen_geography_discovery.sql
```

Changed:

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
```

To:

```sql
CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA public;
```

The remainder of the migration already schema-qualifies PostGIS types and functions:

```text
public.geography
public.ST_SetSRID
public.ST_MakePoint
```

Catalog-owned tables, constraints, and indexes remain in:

```text
catalog_schema
```

## Flyway safety

Migration V3 failed transactionally and the logs confirmed:

```text
Changes successfully rolled back
```

Therefore V3 was not successfully recorded or partially applied, and correcting the unapplied migration file is safe for this environment.

## Required manual state

PostgreSQL Flexible Server:

```text
Server: pg-craves-prodlow-l3ing6
Parameter: azure.extensions
Value includes: POSTGIS
```

No new Azure resource, database, secret, or paid service is required.

## Deployment

Run:

```text
azure-pipelines-catalog-service.yml
```

Branch:

```text
main
```

After the new revision becomes healthy, verify direct Container App endpoints before adding APIM operations.

## Verification

Expected Flyway result:

```text
Migration V3__kitchen_geography_discovery.sql succeeded
```

Expected revision state:

```text
Health: Healthy
Running: RunningAtMaxScale
```

Direct smoke test:

```http
GET /api/v1/discovery/kitchens?latitude=17.4483&longitude=78.3915&radiusMeters=5000&page=0&size=20
```

Expected HTTP status:

```text
200
```

An empty `kitchens` array is valid when no active geocoded kitchens with sellable menu items exist.

## Pending after direct success

APIM still needs explicit operations for:

```text
GET /api/v1/discovery/kitchens
GET /api/v1/discovery/menu-items
```

Do not treat the browsing radius as delivery serviceability, pricing distance, or provider assignment radius.
