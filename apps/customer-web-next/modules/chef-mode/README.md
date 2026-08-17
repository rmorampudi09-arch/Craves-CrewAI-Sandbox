# Craves Chef Web Mode Shell

## Purpose

Adds the role-aware `/chef` entry point to the approved Next.js customer web without changing backend authorization.

## Request flow

```text
HTTP-only Craves session cookie
  -> GET /api/chef/me
  -> APIM /api/v1/auth/me
  -> validated public identity fields
  -> CHEF role-aware navigation
```

## Security

- Browser JavaScript never reads the Craves token.
- No localStorage or sessionStorage authentication path is used.
- The shell does not grant CHEF authority.
- Catalog, Order and User/Chef services remain authoritative for role and ownership.
- Non-chef identities can open only the chef application module.
- Responses are no-store.

## Local validation

```bash
cd apps/customer-web-next
npm install --ignore-scripts
npm run typecheck
npm run test
npm run build
```

## Pipeline

`azure-pipelines-chef-web-mode-ci.yml`

## Manual later

Run CI against the exact PR head. No Azure or APIM change is needed for this shell because it reuses the existing Auth `/me` operation.
