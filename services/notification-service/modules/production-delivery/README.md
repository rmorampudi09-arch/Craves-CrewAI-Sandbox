# Production notification delivery

This module converts durable Notification Service requests into real push or email delivery while keeping every external provider disabled by default.

## Supported launch channels

- `IN_APP`: existing database inbox; no external provider.
- `PUSH`: Firebase Cloud Messaging through Firebase Admin.
- `EMAIL`: Azure Communication Services Email.
- `SMS`: deliberately rejected in this service. Firebase Phone Authentication remains the OTP channel; no transactional-SMS vendor or policy is invented.

## Customer-owned APIs

```text
POST   /api/v1/notifications/devices
GET    /api/v1/notifications/devices
DELETE /api/v1/notifications/devices/{deviceId}
GET    /api/v1/notifications/preferences
PUT    /api/v1/notifications/preferences/{channel}
```

A customer can register, list and deactivate only devices bound to the authenticated Craves identity. The raw FCM token is never returned; clients receive only a SHA-256 token hash.

## Durable dispatch

```text
notification_request PENDING
→ preference check
→ PostgreSQL row claim with SKIP LOCKED
→ provider adapter
→ SENT + delivery attempt
or
→ bounded retry
→ local DEAD_LETTER
```

Expired/invalid FCM registrations are disabled automatically. Requests for a channel later disabled by the customer are changed to `SKIPPED` without contacting a provider.

## Safety defaults

```text
CRAVES_NOTIFICATION_DELIVERY_WORKER_ENABLED=false
CRAVES_NOTIFICATION_PUSH_ENABLED=false
CRAVES_NOTIFICATION_EMAIL_ENABLED=false
```

The worker refuses startup if no provider is enabled. Push refuses startup without the Firebase service-account secret. Email refuses startup without both the ACS connection-string secret and verified sender address.

## Secret names later

Container App secret references:

```text
firebase-service-account-json-base64
acs-email-connection-string
```

Do not paste either value into chat, Git, pipeline YAML, a non-secret pipeline variable or logs.

## Provider activation order

1. Deploy Flyway V3 and code with every new flag false.
2. Register device/preference APIM operations.
3. Bind one provider secret reference.
4. Run the matching `push_config` or `email_config` stage.
5. Keep the worker false and verify revision readiness/configuration.
6. Insert one controlled test request.
7. Enable the worker.
8. Verify request, attempt, provider ID, invalid-token behavior and dead-letter count.
9. Add the second provider only after the first is stable.

## Rollback

`azure-pipelines-notification-production-rollback.yml` disables worker, push and email together. It does not delete requests, attempts, devices, preferences or dead-letter evidence.

## Local validation

```bash
cd services/notification-service
mvn -B -ntp verify
```

## Manual console actions

- Firebase Console: create/use a server service account with only required Firebase Messaging permission and rotate it after validation.
- ACS: confirm the sender domain/address is verified and permitted to send.
- Azure Key Vault/Container Apps: bind credentials by secret reference.
- APIM: expose only the five owned device/preference operations.

No provider request, Firebase change, ACS send, pipeline, migration or Azure change was executed while this module was created.
