# Craves Application Module Roadmap

This document captures the product-critical application flows that must be built after the Azure foundation is deployed.

The Azure foundation creates the platform base only. It must not create dummy catalog/menu/chef data. Chef profile data, menu data, availability, pricing, images, and delivery metadata must come from real approved chef uploads through the Craves application.

## Build order after infrastructure

Recommended module order:

1. Auth service foundation
2. User/Chef service
3. Catalog service
4. Media upload flow
5. Nearby discovery/location search
6. Order service
7. Integration service for Cashfree and delivery partners
8. Notification service
9. CDN/Azure Front Door for public media and web static assets

## Core chef upload to nearby customer flow

```text
Chef app / web portal
  -> Firebase Phone OTP verifies phone possession
  -> Auth service issues Craves session/JWT and roles
  -> User/Chef service checks chef approval status
  -> Chef creates or updates profile, kitchen address, service radius, and availability
  -> Catalog service creates or updates menu items
  -> Media upload flow stores images/videos in Azure Blob Storage
  -> Catalog service stores media URL and menu metadata
  -> PostgreSQL + PostGIS stores chef location and serviceability rules
  -> Customer app sends current address/location to catalog-service
  -> Catalog-service returns only nearby approved chefs and active menu items
```

## No dump data rule

Do not create dummy chef/menu/catalog records as part of infrastructure deployment.

Allowed seed/reference data:

- Static category names such as Biryani, Tiffins, Meals, Curries, Snacks, Desserts
- Static system roles
- Static enum/reference values

Not allowed as infra seed data:

- Fake chef profiles
- Fake menu items
- Fake prices
- Fake chef availability
- Fake delivery radius rules
- Fake ratings/reviews

These must come from real application flows and admin approvals.

## Nearby discovery requirement

Nearby discovery must be dynamic and location-aware.

Customer app request should include:

- Customer latitude
- Customer longitude
- Delivery address ID
- Optional filters such as cuisine, category, vegetarian/non-vegetarian, time slot, price range

Catalog/location search must return:

- Approved chefs only
- Active chefs only
- Available menu items only
- Items serviceable to the customer's location
- Items available for the selected day/time slot

PostgreSQL should use PostGIS for distance and service radius checks.

Example logical filter:

```sql
SELECT *
FROM chef_profile cp
JOIN menu_item mi ON mi.chef_profile_id = cp.id
WHERE cp.status = 'APPROVED'
  AND cp.is_accepting_orders = true
  AND mi.status = 'ACTIVE'
  AND mi.available_today = true
  AND ST_DWithin(
        cp.location_geography,
        ST_MakePoint(:customerLongitude, :customerLatitude)::geography,
        cp.delivery_radius_meters
      );
```

## Media upload and CDN requirement

Chef media uploads should go to Azure Blob Storage first. CDN/Azure Front Door should be added after upload rules are implemented.

Media flow:

```text
Chef selects image/video
  -> App requests upload permission from backend
  -> Backend verifies chef identity and item ownership
  -> Backend returns limited upload URL/SAS or controlled upload path
  -> File is uploaded to Blob Storage
  -> Backend stores blob path and metadata
  -> Moderation/validation marks media approved or rejected
  -> Public approved media is served through CDN/Azure Front Door later
```

CDN/Azure Front Door should cache:

- Approved dish images
- Approved chef profile photos
- Approved public banners
- Web static assets

CDN/Azure Front Door must not cache:

- Authenticated customer APIs
- Authenticated chef dashboard APIs
- Cart
- Orders
- Payments
- Delivery tracking responses
- Profile data

## Delivery partner integration requirement

Delivery partner integration must live only inside `integration-service`.

Expected delivery flow:

```text
Customer places order
  -> order-service creates pending order
  -> integration-service handles Cashfree payment request if online payment is used
  -> Cashfree webhook confirms payment status
  -> order-service marks order payment success
  -> order-service publishes delivery command/event to Service Bus
  -> integration-service creates delivery request with selected partner
  -> delivery partner returns tracking/reference ID
  -> integration-service stores partner response
  -> notification-service notifies customer and chef
```

Initial delivery provider priority must be confirmed before implementation. Current intended provider is Shiprocket/Shiprocket Quick or hyperlocal model. Porter/Dunzo/other partners should not be hardcoded until business/vendor confirmation.

## Integration secret boundary

Provider secrets must never be exposed to mobile or web.

Secret ownership:

- Firebase private/admin credentials: auth-service only
- Cashfree app ID/secret/webhook secret: integration-service only
- Shiprocket credentials/webhook secret: integration-service only
- ACS email connection string/sender: notification-service only
- Database credentials: backend services only through Key Vault/managed identity patterns

## Business decisions still required before coding

Do not invent the following rules in code:

- Chef approval/KYC rules
- Chef service radius rules
- Menu moderation rules
- Dish pricing and Craves commission rules
- Delivery fee rules
- Minimum order value
- Order cutoff time
- Refund/cancellation rules
- Delivery partner priority and fallback rules
- Food safety/FSSAI compliance rules

These must come from CRV-ARCH-HLD-002, CRV-FUNC-001, or explicit product decisions.
