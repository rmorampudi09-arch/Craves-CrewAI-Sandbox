# Craves Admin Shell

Route: `/admin`.

BFF: `GET /api/admin/me`.

The shell derives access from the backend `ADMIN` role and active identity status. It does not grant or mutate roles. Every linked operation is re-authorized by its owning backend service.

Navigation is prepared for chef application review, subscription plan management and subscription status operations.

Security controls:

- HTTP-only Craves session
- no access token in browser JavaScript
- no identity UUID returned by the admin BFF
- no browser storage
- no-store response
- no indexing

Later run `azure-pipelines-admin-web-shell-ci.yml`, then deploy only after the dependent customer web/auth stack passes.
