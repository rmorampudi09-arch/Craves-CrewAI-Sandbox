# Admin account intervention APIM

## API

- API ID: `craves-admin-account-intervention-v1`
- Path: `api/v1/admin/accounts`
- Backend: Auth Service

## Operations

- `GET /{identityId}/intervention-status`
- `POST /{identityId}/suspend`
- `POST /{identityId}/reactivate`

## Controls

The rollout refuses an unknown path owner, inherited `backend-id`, subscription-key relaxation, an unhealthy Auth Service or missing explicit confirmation. Each operation has an operation-level backend URL, Bearer syntax guard and no-store response hardening. Auth Service remains responsible for JWT validation, ADMIN authorization, self-suspension prevention and audit evidence.

## Pipelines

- static CI
- controlled rollout
- read-only status
- bounded rollback

No APIM pipeline runs automatically.
