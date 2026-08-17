# Administrative account intervention

This module provides an audited, fail-closed way to suspend or reactivate a Craves identity.

## Endpoints

```text
POST /api/v1/admin/accounts/{identityId}/suspend
POST /api/v1/admin/accounts/{identityId}/reactivate
GET  /api/v1/admin/accounts/{identityId}/intervention-status
```

Mutations require an authenticated Craves `ADMIN`, a 10–500 character reason and an optional UUID `X-Correlation-ID`.

## Transaction boundary

A suspension/reactivation request performs the following in one Auth database transaction:

1. lock the target identity;
2. reject administrator self-suspension;
3. change `auth_identity.status`;
4. increment `token_version`;
5. revoke all active Craves refresh sessions;
6. append `auth_audit` evidence;
7. create a durable Firebase intervention row.

The Firebase worker then disables/enables the Firebase user and revokes Firebase refresh tokens for suspension. Provider failures are retried with bounded exponential backoff and retained as local dead-letter evidence.

Firebase Admin Java supports disabling an existing user through `UserRecord.UpdateRequest#setDisabled` and refresh-token revocation. The code does not expose the Firebase UID or credentials through the public response.

## Safety defaults

```text
CRAVES_ADMIN_ACCOUNT_INTERVENTION_API_ENABLED=false
CRAVES_ADMIN_ACCOUNT_INTERVENTION_FIREBASE_WORKER_ENABLED=false
```

The API and worker are activated separately. The worker cannot start while the API is disabled.

## Access-token boundary

Suspension immediately blocks new Auth exchanges and refresh operations through the local identity status and session revocation. Already-issued Craves access tokens remain valid only until their configured short expiry unless the later distributed revocation module rejects them earlier. The current default access-token TTL is 15 minutes.

## Activation order later

1. Run the Auth Java 21 CI pipeline.
2. Deploy the merged image with both flags false.
3. Confirm Flyway V2/V3 and Auth health.
4. Enable the API only.
5. Submit one test suspension for a non-admin synthetic identity.
6. Confirm local status, token-version increment, session revocation and pending provider work.
7. Enable the Firebase worker.
8. Confirm provider completion and blocked Firebase sign-in.
9. Test reactivation and audit history.

## Rollback

`azure-pipelines-backend-admin-account-intervention-rollback.yml` disables both flags without deleting identities, sessions, intervention rows or audit evidence.

## Manual credentials

`FIREBASE_SERVICE_ACCOUNT_JSON_BASE64` must remain an existing Container App secret reference. Never commit it or paste it into chat.
