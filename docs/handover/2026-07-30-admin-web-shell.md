# Craves Admin Web Shell — Handover

Date: 2026-07-30

## Scope
Adds a role-aware administrative entry point to the existing secure Next.js application.

## Branch
`feature/admin-web-shell`.

## Route
`/admin`.

## BFF
`GET /api/admin/me`.

## Authorization
The BFF calls `/api/v1/auth/me`, requires active status and the backend `ADMIN` role, and returns a reduced identity.

## Role boundary
The browser cannot grant, revoke or persist roles. Each backoffice service checks ADMIN again.

## Privacy
Identity UUID, phone number, role array and token are excluded from the admin BFF response.

## Session
Uses the existing HTTP-only `craves_access_token` cookie.

## Storage
No localStorage or sessionStorage.

## Caching
No-store responses.

## Search indexing
Admin pages set `robots: noindex, nofollow`.

## Navigation
Chef reviews, subscription plans and subscription operations.

## Main files

```text
src/lib/admin-contract.ts
src/app/api/admin/me/route.ts
src/components/admin-shell.tsx
src/app/admin/page.tsx
```

## Tests
Role and privacy contract tests.

## CI
`azure-pipelines-admin-web-shell-ci.yml`.

## APIM
No gateway change is required for `/auth/me`; backoffice operations are configured later.

## Azure
No resource or runtime update was executed.

## Manual later
Run CI, deploy after web/auth dependencies, then validate with one ADMIN and one non-admin identity.

## Rollback
Restore the previous customer-web image.

## Acceptance
ADMIN sees navigation; non-admin receives controlled denial; token and identity UUID never appear in browser responses.
