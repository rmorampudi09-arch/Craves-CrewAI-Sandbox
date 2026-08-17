# Craves User & Chef Service

Spring Boot service for customer profiles, saved delivery addresses, GPS-aware address recommendation, chef applications, KYC document upload, and backoffice chef review.

## Responsibilities

- Customer profile create, update, and read.
- Customer saved-address CRUD.
- Mandatory geocoding for newly created or edited saved addresses.
- PostGIS nearest-saved-address calculation from live GPS coordinates.
- Temporary live-GPS browsing when no saved address is close enough.
- Soft deletion so removed addresses cannot be used for future orders.
- One active default address whenever at least one active address exists.
- Protected internal address lookup for the future Order Service checkout flow.
- Chef application, KYC, approval, rejection, and notification-outbox workflows.

## Customer location flow

```text
Customer opens app
    -> app obtains live latitude and longitude
    -> User-Chef Service checks active saved addresses
    -> nearest address is inside matchRadiusMeters?
       -> yes: SAVED_ADDRESS
       -> no: LIVE_GPS
    -> returned coordinates are used for nearby-chef discovery
```

A live GPS location can be used for browsing, but checkout must later receive an active saved `deliveryAddressId`.

`matchRadiusMeters` is only the distance used to match live GPS to a saved address. It is not the chef delivery radius, pricing radius, or serviceability policy.

## Database

The service uses `craves_business_db`.

Flyway migrations:

```text
V1__user_chef_schema.sql
V2__notification_outbox.sql
V3__customer_address_location.sql
```

V3 adds:

```text
customer_address.area_name
customer_address.is_active
customer_address.location geography(Point, 4326)
```

`location` is generated from longitude and latitude. A partial GiST index supports nearest-address lookup for active geocoded addresses.

Legacy rows are not assigned invented area names or coordinates. New create and update requests require complete geocoding and complete recipient details.

## Local setup

```bash
cd services/user-chef-service
mvn spring-boot:run
```

Required environment variables:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/craves_business_db
SPRING_DATASOURCE_USERNAME=cravesadmin
SPRING_DATASOURCE_PASSWORD=
CRAVES_JWT_VERIFICATION_PEM_BASE64=
CRAVES_STORAGE_ENDPOINT_VALUE=
CRAVES_STORAGE_DOCUMENTS_CONTAINER=documents
CRAVES_INTERNAL_SERVICE_SECRET=
```

## Customer APIs

```text
GET    /api/v1/customer/profile
PUT    /api/v1/customer/profile
GET    /api/v1/customer/addresses
GET    /api/v1/customer/addresses/{addressId}
POST   /api/v1/customer/addresses
PUT    /api/v1/customer/addresses/{addressId}
DELETE /api/v1/customer/addresses/{addressId}
GET    /api/v1/customer/addresses/recommendation
```

Example saved-address request:

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

Rules:

- Recipient name, phone, address line 1, area, city, state, postal code, latitude, and longitude are required.
- Latitude must be between -90 and 90.
- Longitude must be between -180 and 180.
- The first active address automatically becomes default.
- Selecting another default clears the earlier default.
- Deleting the default promotes the most recently updated remaining active address.
- Delete is a soft delete.

Recommendation request:

```text
GET /api/v1/customer/addresses/recommendation
    ?latitude=17.4483
    &longitude=78.3915
    &matchRadiusMeters=500
```

Saved-address result:

```json
{
  "locationType": "SAVED_ADDRESS",
  "latitude": 17.4483,
  "longitude": 78.3915,
  "distanceMeters": 42,
  "matchRadiusMeters": 500
}
```

Live-GPS result:

```json
{
  "locationType": "LIVE_GPS",
  "latitude": 17.5000,
  "longitude": 78.5000,
  "selectedSavedAddress": null,
  "matchRadiusMeters": 500
}
```

## Internal address lookup

```text
GET /internal/v1/customer-addresses/{addressId}?identityId={customerIdentityId}
Header: X-Craves-Internal-Secret
```

The endpoint uses the existing internal service credential, validates address ownership, and returns active addresses only. It fails closed when internal authentication is not configured. Order Service will consume it in the checkout snapshot module.

## Chef APIs

```text
GET  /api/v1/chef/application
POST /api/v1/chef/application
POST /api/v1/chef/application/proof-files
```

## Backoffice APIs

```text
GET  /api/v1/backoffice/chef-reviews?status=PENDING
GET  /api/v1/backoffice/chef-reviews/{applicationId}
POST /api/v1/backoffice/chef-reviews/{applicationId}/approve
POST /api/v1/backoffice/chef-reviews/{applicationId}/reject
```

Backoffice endpoints require the `ADMIN` role.

## Build and deployment

Pipeline:

```text
azure-pipelines-user-chef-service.yml
```

Build locally:

```bash
mvn -B clean test
mvn -B clean package
```

No new Azure resource or new credential name is required. The pipeline already maps the internal service credential into the Container App.

After deployment verify:

```text
Flyway V3 completed
PostGIS is available
Container App revision is Ready
/actuator/health returns UP
```

Runtime API testing may wait until a fresh Craves access token is available.

## Pending next module

```text
Catalog Service
    coordinate-based nearby kitchen and menu discovery

Order Service
    mandatory deliveryAddressId at checkout
    internal ownership lookup
    immutable customer drop-off snapshot
    immutable kitchen pickup snapshot
```

Delivery radius, delivery charges, commissions, pricing, and compliance rules remain outside this module until Product defines them.
