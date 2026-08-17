# Craves Customer Address and App-Launch Location Handover

Date: 2026-07-15  
Branch: `feature/customer-address-location`  
Primary service: User-Chef Service  
Stack: Spring Boot 3, Java 21, PostgreSQL, PostGIS, Azure Container Apps

## Product decisions implemented

Krishna confirmed the following behavior:

1. Customer location is selected when the customer opens the app, not only when checkout opens.
2. The app first obtains live GPS coordinates.
3. Craves compares live GPS with the customer's active saved addresses.
4. When a saved address is close enough, that address becomes the active browsing location.
5. When no saved address is close enough, the customer may browse using temporary live GPS coordinates.
6. The customer can manually change the browsing location from the home screen.
7. Checkout shows the active saved address by default when one is selected.
8. Checkout also lets the customer choose a different saved address.
9. A temporary live GPS location cannot be used directly to place an order.
10. Before checkout completes, the customer must save the live location or select an existing saved address.
11. The future Order Service checkout request will contain an explicit `deliveryAddressId`.
12. Delivery radius, pricing, charges, commissions, and compliance rules are not defined in this module.

## Important separation

Craves now models two related but different concepts.

### Active browsing location

Used for nearby-chef and menu discovery.

```text
SAVED_ADDRESS
or
LIVE_GPS
```

### Final delivery address

Used for order placement and delivery.

```text
active saved customer_address row
identified by deliveryAddressId
```

The final delivery address will be copied into an immutable Order Service snapshot in the next checkout module.

## End-to-end app-launch flow

```text
Customer opens app
    ↓
App requests GPS permission
    ↓
App reads live latitude and longitude
    ↓
App calls User-Chef recommendation endpoint
    ↓
PostGIS calculates nearest active saved address
    ↓
Nearest distance <= matchRadiusMeters?
    ├── Yes
    │     locationType = SAVED_ADDRESS
    │     selectedSavedAddress is returned
    │     saved coordinates become active browsing location
    │
    └── No
          locationType = LIVE_GPS
          live coordinates remain active browsing location
    ↓
Catalog discovery uses active coordinates
    ↓
Customer may change location manually
    ↓
Checkout requires an active saved address ID
```

## Why matchRadiusMeters is a request parameter

The user confirmed the behavior but did not define the exact number of metres that counts as a nearby saved address.

The backend therefore does not invent a hard-coded product value. The caller supplies:

```text
matchRadiusMeters
```

This parameter is only used for matching the current GPS reading to an existing saved address. It is not:

- a chef delivery radius;
- an order serviceability radius;
- a delivery-fee distance;
- a provider assignment radius.

A later mobile/web configuration decision can standardize one value without changing the database or distance algorithm.

## Existing implementation discovered

The User-Chef Service already contained:

```text
customer_profile
customer_address
basic address CRUD
is_default support
```

The module extends that implementation instead of introducing a duplicate address table or a second ownership model.

## Files changed

### API contracts

Changed:

```text
services/user-chef-service/src/main/java/in/craves/userchef/web/ApiDtos.java
```

Added:

```text
ActiveLocationType
CustomerLocationRecommendationResponse
```

Expanded customer address request and response with:

```text
areaName
active
mandatory latitude
mandatory longitude
```

Validation:

```text
latitude  = -90 to 90
longitude = -180 to 180
areaName  = required
```

### Customer address service

Changed:

```text
services/user-chef-service/src/main/java/in/craves/userchef/service/CustomerProfileService.java
```

Behavior implemented:

- list active addresses only;
- read one active address owned by the authenticated customer;
- require area and coordinates for create/update;
- make the first active saved address the default;
- clear the old default when another address becomes default;
- keep one default when active addresses exist;
- soft-delete addresses rather than physically deleting rows;
- promote another active default after deleting the default;
- calculate nearest saved address through PostGIS;
- return `LIVE_GPS` when no saved address is inside the supplied radius;
- return `SAVED_ADDRESS` when the nearest address is inside the supplied radius;
- expose an ownership-safe internal lookup for Order Service.

### Customer controller

Changed:

```text
services/user-chef-service/src/main/java/in/craves/userchef/web/CustomerProfileController.java
```

New endpoints:

```text
GET /api/v1/customer/addresses/{addressId}
GET /api/v1/customer/addresses/recommendation
```

Recommendation query parameters:

```text
latitude
longitude
matchRadiusMeters
```

### Internal authentication

Added:

```text
services/user-chef-service/src/main/java/in/craves/userchef/security/InternalRequestAuthorizer.java
```

It:

- reuses the existing internal service credential configuration;
- compares credentials using constant-time byte comparison;
- returns 503 when server-side internal authentication is absent;
- returns 401 when the supplied credential is missing or invalid.

### Internal address controller

Added:

```text
services/user-chef-service/src/main/java/in/craves/userchef/web/InternalCustomerAddressController.java
```

Endpoint:

```text
GET /internal/v1/customer-addresses/{addressId}?identityId={customerIdentityId}
```

Expected header:

```text
X-Craves-Internal-Secret
```

The endpoint validates both:

```text
address ID
customer identity ID
```

It returns only an active address matching both values.

### Security configuration

Changed:

```text
services/user-chef-service/src/main/java/in/craves/userchef/config/SecurityConfig.java
```

Only this exact internal route is permitted past JWT authentication:

```text
/internal/v1/customer-addresses/**
```

The controller then enforces internal service authentication. Other service routes continue to require Craves JWT authentication.

### Flyway migration

Added:

```text
services/user-chef-service/src/main/resources/db/migration/V3__customer_address_location.sql
```

The migration:

1. enables PostGIS if it is not already installed;
2. adds `area_name`;
3. adds `is_active` with default `true`;
4. adds generated geography column `location`;
5. adds coordinate-pair validation;
6. adds latitude-range validation;
7. adds longitude-range validation;
8. adds an active-address lookup index;
9. adds a partial GiST geography index.

Generated location expression:

```text
longitude + latitude
    ↓
PostGIS Point with SRID 4326
    ↓
geography(Point, 4326)
```

Legacy data policy:

- old area names are not guessed;
- old coordinates are not guessed;
- legacy rows remain stored;
- new API create/update requests require complete values;
- recommendation excludes rows without generated geography.

### Tests

Added:

```text
services/user-chef-service/src/test/java/in/craves/userchef/web/CustomerAddressRequestValidationTest.java
services/user-chef-service/src/test/java/in/craves/userchef/security/InternalRequestAuthorizerTest.java
```

Coverage includes:

- valid complete geocoded address;
- missing area rejection;
- missing latitude rejection;
- invalid longitude rejection;
- matching internal credential acceptance;
- invalid internal credential rejection;
- fail-closed behavior when internal authentication is not configured.

### README

Changed:

```text
services/user-chef-service/README.md
```

It documents:

- browsing versus checkout location;
- saved-address request contract;
- recommendation endpoint;
- live-GPS response;
- internal Order Service lookup;
- migration and deployment checks;
- remaining Product decisions.

## Public API examples

### Create saved address

```http
POST /api/v1/customer/addresses
Authorization: Bearer <Craves customer token>
Content-Type: application/json
```

```json
{
  "addressLabel": "HOME",
  "recipientName": "Customer Name",
  "contactPhoneNumber": "+919876543210",
  "addressLine1": "Flat 101, Test Residency",
  "addressLine2": "Road No. 1",
  "landmark": "Near Metro",
  "areaName": "Madhapur",
  "city": "Hyderabad",
  "state": "Telangana",
  "postalCode": "500081",
  "latitude": 17.4483,
  "longitude": 78.3915,
  "isDefault": true
}
```

### Recommend app-launch location

```http
GET /api/v1/customer/addresses/recommendation?latitude=17.4483&longitude=78.3915&matchRadiusMeters=500
Authorization: Bearer <Craves customer token>
```

Saved result:

```json
{
  "locationType": "SAVED_ADDRESS",
  "latitude": 17.4483,
  "longitude": 78.3915,
  "selectedSavedAddress": {
    "id": "address-uuid",
    "areaName": "Madhapur",
    "active": true
  },
  "distanceMeters": 42,
  "matchRadiusMeters": 500
}
```

Live result:

```json
{
  "locationType": "LIVE_GPS",
  "latitude": 17.5000,
  "longitude": 78.5000,
  "selectedSavedAddress": null,
  "distanceMeters": 12750,
  "matchRadiusMeters": 500
}
```

## Default-address behavior

```text
No active addresses
    + create first address
    = first address becomes default

Create/update address with isDefault=true
    = previous default cleared
    = selected address becomes default

Delete default address
    = row becomes inactive
    = another active address is promoted

No remaining active address
    = no default address
```

## Soft-delete behavior

`DELETE` does not remove the database row.

It sets:

```text
is_active = false
is_default = false
```

Inactive addresses are excluded from:

- customer address lists;
- recommendation results;
- customer single-address lookup;
- internal Order Service lookup;
- future checkout selection.

## Azure and deployment impact

No new Azure resource is created.

No new credential name is introduced.

The existing User-Chef pipeline already maps:

```text
CRAVES_INTERNAL_SERVICE_SECRET
```

The migration requires PostGIS in `craves_business_db`. If Flyway reports that the extension is unavailable or not allow-listed, PostgreSQL server extension settings must be corrected before rerunning deployment.

No delivery processors or provider calls should be enabled during this deployment.

Keep Integration Service safety settings unchanged:

```text
CRAVES_DELIVERY_COMMAND_ENABLED=false
CRAVES_DELIVERY_INTELLIGENCE_ENABLED=true
BORZO_API_ENABLED=false
```

## Build commands

```bash
cd services/user-chef-service
mvn -B clean test
mvn -B clean package
```

## Deployment procedure

Run:

```text
azure-pipelines-user-chef-service.yml
```

Verify:

```text
1. Java 21 selected.
2. Maven tests passed.
3. Maven package passed.
4. Container image pushed.
5. Flyway V3 completed.
6. PostGIS extension is available.
7. New Container App revision is Ready.
8. /actuator/health returns UP.
```

Runtime customer API testing can wait until a fresh Craves access token is available.

## Manual steps required

### Azure DevOps

Run the User-Chef Service pipeline after the pull request is merged.

### PostgreSQL extension check

Only when Flyway reports a PostGIS extension restriction:

- inspect the Azure PostgreSQL Flexible Server extension allow-list;
- allow PostGIS for the server;
- rerun the pipeline.

This is a configuration action, not a new paid resource.

### Secrets

No new value is required if `CRAVES_INTERNAL_SERVICE_SECRET` is already configured in Azure DevOps and the Container App.

Do not paste credential values into chat or commit them to GitHub.

## Known pending decisions

The following values remain intentionally undefined:

- standard GPS-to-saved-address match radius used by mobile and web;
- chef delivery/serviceability radius;
- delivery fee by distance;
- location-based pricing;
- maximum number of saved addresses per customer;
- address verification provider;
- reverse-geocoding provider;
- customer address naming beyond HOME, WORK, and OTHER.

## Next engineering module

After User-Chef deployment succeeds:

```text
Catalog Service
    ↓
Accept active browsing coordinates
    ↓
Return nearby active kitchens and menu items
    ↓
Do not invent a final serviceability radius
```

Then:

```text
Order Service checkout
    ↓
Require deliveryAddressId
    ↓
Call internal User-Chef lookup
    ↓
Verify ownership and active status
    ↓
Snapshot customer drop-off fields
    ↓
Snapshot kitchen pickup fields
    ↓
Create immutable order delivery data
```

Transactional `CHEF_ACCEPTED_ORDER` outbox publication remains after the checkout snapshot work.
