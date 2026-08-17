# Admin dashboard APIM route

This module exposes one read-only operational summary owned by Order Service.

```text
API ID: craves-admin-dashboard-v1
Path: api/v1/admin/dashboard
Operation: GET /summary
Backend: Order Service /api/v1/admin/dashboard/summary
```

APIM requires a Bearer header and applies no-store response headers. Order Service still validates the token and independently requires the existing `ADMIN` role. The response intentionally excludes customer identities, phone numbers, provider references and commercial calculations.

Run `azure-pipelines-admin-dashboard-apim.yml` only after the Order Service revision containing the endpoint is healthy. The pipeline requires an explicit `confirmApimWrite=true` parameter and does not perform authenticated production reads.
