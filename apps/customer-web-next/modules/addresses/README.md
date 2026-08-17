# Customer Web Address Management

Authenticated address management now provides a food-delivery-style current-location experience instead of exposing raw coordinates.

## Active browsing location on customer home

After sign-in, the customer home screen attempts high-accuracy live GPS automatically.

```text
saved default address (safe fallback)
  -> request current GPS
  -> compare GPS with saved addresses through PostGIS
  -> nearby saved address? use it as active browsing location
  -> otherwise reverse-geocode live GPS and use a temporary CURRENT LOCATION
  -> discover nearby kitchens/dishes from the active coordinates
```

The active browsing location may change as the customer moves. The permanent saved `isDefault` Home/Work/Other row is not silently overwritten. If location permission is denied or unavailable, Craves falls back to the saved address.

## Customer experience

When the customer chooses **Use my current location** while adding/editing an address:

1. the browser obtains a high-accuracy GPS point;
2. Craves checks the existing PostGIS saved-address recommendation where applicable;
3. when a nearby saved address matches, that saved address is selected/prefilled;
4. otherwise the same-origin Next.js BFF reverse-geocodes the GPS point with Azure Maps;
5. Craves prefills the best available flat/house/building, street, area, district, city, state and pincode;
6. the customer can correct any written field before saving;
7. latitude/longitude remain internal and are not rendered as editable customer fields.

The provider may not know a private apartment/unit number. Craves never invents one; it fills the most precise available building/street address and keeps the field editable.

## Web routes

- signed-in customer home discovery
- `/addresses`
- checkout address dialog
- public delivery-location modal
- nearby discovery browser

## Same-origin BFF

- `GET|POST /api/customer/addresses`
- `GET|PUT|DELETE /api/customer/addresses/{addressId}`
- `GET /api/customer/addresses/recommendation`
- `POST /api/location/reverse-geocode`

## Service contract

User/Chef Service remains the customer saved-address source of truth under `/api/v1/customer/addresses`.

Customer addresses now also persist:

```text
districtName
```

Flyway migration:

```text
services/user-chef-service/src/main/resources/db/migration/V4__customer_address_district.sql
```

The new web UI requires and sends district for new/edited addresses. The backend keeps `districtName` nullable during the rolling deployment so the currently deployed older web/mobile contract cannot be broken while the new frontend is being released. Legacy rows therefore remain readable with a null district until the customer edits them.

## Security

- HTTP-only Craves access-token cookie for customer saved-address APIs.
- Mutation requests require same-origin browser headers.
- Reverse geocoding is a same-origin POST through the Next.js BFF.
- Azure Maps is called server-side using the Container App managed identity.
- Azure Maps shared/local authentication is disabled in production.
- No Azure Maps key, managed-identity token or provider secret reaches browser code.
- Customer identity IDs are removed from browser responses.
- Coordinates are validated and retained internally for PostGIS/discovery/delivery but hidden from normal customer UI.
- No address is stored in browser storage by authenticated address management; only the current in-memory browsing location may be temporary.

## Deployment order

1. Run exact-head feature CI.
2. Merge the feature after CI succeeds.
3. Deploy User-Chef Service so Flyway V4 and the additive `districtName` response field are live.
4. Run `azure-pipelines-customer-location-azure-maps.yml` with `confirmBillableAzureMapsProvision=true`.
5. Deploy the customer-web-next image using the existing guarded customer-web deployment pipeline.
6. Purge only the immutable customer-web static assets if the web deployment pipeline does not already perform the Front Door purge.
7. Run authenticated customer and chef smoke tests.

## Acceptance smoke

Customer:

```text
sign in / open home
-> current GPS automatically checked
-> nearby saved address selected OR temporary current location resolved
-> homepage location label changes to current area/city
-> discovery uses active current coordinates
-> add/edit address -> Use current location
-> written address auto-populates
-> latitude/longitude never appear
-> district is present
-> customer can edit flat/house/building
-> save address
-> select at checkout
```

Chef:

```text
chef application -> Use current location
-> written address auto-populates
-> latitude/longitude never appear
-> kitchen profile -> Use current location
-> written address auto-populates
-> save kitchen
-> internal coordinates remain available to Catalog discovery and delivery pickup
```

## Manual / Azure-sensitive steps

The Azure Maps provisioning pipeline is billing-sensitive. It is guarded and must be explicitly run with the confirmation parameter set to true. It creates/reuses the Gen2/G2 Maps account, disables local/shared-key auth, grants the customer-web managed identity `Azure Maps Data Reader`, and binds only the non-secret Maps account unique ID to the Container App.
