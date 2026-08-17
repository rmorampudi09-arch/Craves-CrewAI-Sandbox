# Customer Web Nearby Discovery

This module adds a production-safe Next.js discovery experience backed by the existing Catalog Service discovery API.

## Routes

- `GET /api/discovery/kitchens`
- `GET /api/discovery/menu-items`
- `/discover`

## Backend contract

The module calls the already configured APIM path:

```text
/api/v1/discovery/kitchens
/api/v1/discovery/menu-items
```

It does not create or update catalog records.

## Privacy and security

- Browser geolocation is permission-based.
- Coordinates are sent only when the customer starts a discovery request.
- No location is written to browser storage.
- API responses are allow-listed.
- Kitchen phone numbers, email addresses, exact coordinates and internal identity IDs are not returned to the browser.
- Menu image URLs must use HTTPS.
- Response caching is disabled.

## Local setup

Set:

```text
CRAVES_API_BASE_URL=https://<apim-host>/api/v1
```

Then run the standard `apps/customer-web-next` development command.

## Testing

```bash
cd apps/customer-web-next
npm run typecheck
npm run test
npm run build
```

## Manual steps required later

- Confirm the existing Catalog discovery APIM operations are still present.
- Run the read-only catalog discovery smoke test.
- Review the final browser geolocation consent copy before production launch.

No billable Azure resource, Firebase setting, provider integration or business rule is introduced.
