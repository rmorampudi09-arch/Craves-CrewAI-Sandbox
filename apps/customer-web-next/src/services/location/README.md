# Craves location service

Craves uses device GPS only to obtain a precise map point. Customers and chefs do not enter or see latitude/longitude in normal UI.

## Current-location flow

```text
Browser location permission
  -> latitude/longitude kept in component state only
  -> POST /api/location/reverse-geocode
  -> same-origin + per-instance request-rate guard
  -> Next.js BFF obtains a Microsoft Entra token from the Container App managed identity
  -> Azure Maps Reverse Geocoding
  -> sanitized written-address response
  -> editable Craves address fields
```

The BFF returns only:

- formatted address
- house/building number when Azure Maps resolves one
- street/road
- area/neighborhood
- city/locality
- district
- state
- pincode
- country
- confidence metadata

It never returns Azure credentials or managed-identity tokens.

## Production Azure configuration

Required non-secret runtime environment variables on `ca-craves-web-prodlow`:

```text
AZURE_MAPS_CLIENT_ID=<Azure Maps account properties.uniqueId>
AZURE_MAPS_ENDPOINT=https://atlas.microsoft.com
```

`IDENTITY_ENDPOINT` and `IDENTITY_HEADER` are injected by Azure Container Apps when the app has a managed identity. They must never be configured manually in source control.

Production uses:

- Azure Maps Gen2 / G2
- shared/local key authentication disabled
- customer-web system-assigned managed identity
- `Azure Maps Data Reader` RBAC scoped only to the Maps account

Provisioning is intentionally guarded because Azure Maps is a billable metered Azure resource:

```text
azure-pipelines-customer-location-azure-maps.yml
confirmBillableAzureMapsProvision=true
```

## Accuracy rule

Reverse geocoding can only fill what the map provider can resolve. When Azure Maps returns a street/house number, Craves prefills it. If a private flat/unit number cannot be resolved from GPS, Craves fills the best available postal address and asks the user to correct the flat/house/building field. Craves never invents an apartment or door number.

## Security and metered-usage protection

- reverse geocoding is server-side only;
- the browser never receives an Azure Maps key;
- the public BFF route accepts same-origin POST requests only;
- the BFF applies a 30-requests-per-minute per-client process-level safety limit before calling the metered provider;
- precise coordinates remain internal to Craves requests used for PostGIS discovery and delivery;
- provider responses are normalized before they reach UI code;
- the Azure Maps account itself uses Entra/RBAC with local/shared-key authentication disabled.

The in-process rate guard is a cost-abuse safety layer, not a replacement for Azure Front Door/WAF traffic policy. Production monitoring should alert on unusual reverse-geocoding volume and 429/5xx rates.
