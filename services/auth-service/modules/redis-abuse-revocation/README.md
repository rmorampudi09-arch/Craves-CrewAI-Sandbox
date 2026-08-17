# Redis authentication abuse protection and distributed token revocation

This module adds two independent, fail-closed security controls to the Craves Java backend:

1. Redis-backed rate limits for Firebase token exchange and Craves refresh-token rotation.
2. Distributed account-status and minimum-token-version enforcement across all seven Spring services.

No Redis resource is provisioned by this code.

## Services covered

```text
Auth Service
User-Chef Service
Catalog Service
Order Service
Subscription Service
Integration Service
Notification Service
```

Each service contains the same opt-in `RedisTokenRevocationWebConfiguration` contract.

## Revocation flow

```text
ADMIN suspends/reactivates an identity
→ Auth identity status/token_version changes transactionally
→ PostgreSQL trigger creates auth_token_revocation_outbox row
→ Auth publisher claims with FOR UPDATE SKIP LOCKED
→ Redis TTL key is written
→ authenticated API request passes existing JWT signature/claim checks
→ service reads Redis projection by identity UUID
→ SUSPENDED or older token_version is rejected
```

Redis key format:

```text
craves:auth:revocation:<identity UUID>
```

Redis value format:

```text
<account status>|<minimum token version>
```

The TTL equals the configured Craves access-token lifetime plus a grace interval. This keeps the projection only as long as an older access token could still be presented.

## JWT validation boundary

The Redis interceptor does not replace JWT validation. It runs only when:

- an Authorization Bearer header exists; and
- the service's existing JWT security filter has authenticated the request.

Public anonymous routes continue without a Redis lookup.

The interceptor reads only the already-verified `sub` and `token_version` claims. It does not store or log the JWT.

## Failure behavior

Default consumer behavior when enabled:

```text
CRAVES_TOKEN_REVOCATION_FAIL_CLOSED=true
```

A Redis outage or malformed revocation projection returns HTTP 503 for authenticated protected requests instead of silently bypassing revocation.

A matching `SUSPENDED` state or an older token version returns HTTP 401.

## Durable publisher

Auth Service owns the durable source of truth:

```text
auth_token_revocation_outbox
```

The publisher supports:

- row-lock-safe multi-replica claims;
- stale lease recovery;
- bounded exponential retries;
- local dead-letter status;
- idempotent Redis replacement by identity key;
- short-lived TTL projections.

Publishing and consuming are controlled separately.

## Auth rate limits

Protected routes:

```text
POST /api/v1/auth/firebase/exchange
POST /api/v1/auth/refresh
```

The filter uses an atomic Redis Lua script:

```text
INCR
→ set expiry only for the first request in the window
→ return current count
```

Keys contain a SHA-256 hash of the selected client IP, not the raw IP, Firebase token, refresh token, phone number or request body.

When the limit is exceeded, Auth returns:

```text
HTTP 429
Retry-After: <window seconds>
X-RateLimit-Limit: <configured limit>
X-RateLimit-Remaining: 0
```

When Redis is unavailable, the protected Auth operation returns HTTP 503.

## Explicit limits

Source code intentionally supplies no production request threshold.

When rate limiting is enabled, all of these must be explicitly approved:

```text
CRAVES_AUTH_RATE_LIMIT_EXCHANGE_LIMIT
CRAVES_AUTH_RATE_LIMIT_REFRESH_LIMIT
CRAVES_AUTH_RATE_LIMIT_WINDOW_SECONDS
CRAVES_AUTH_RATE_LIMIT_TRUST_FORWARDED_FOR
```

Both limits must be positive. The window must be between 1 and 3600 seconds.

`X-Forwarded-For` is ignored unless `CRAVES_AUTH_RATE_LIMIT_TRUST_FORWARDED_FOR=true`. Enable that only after APIM/Front Door is confirmed to overwrite untrusted client forwarding headers.

## Safety defaults

```text
CRAVES_TOKEN_REVOCATION_PUBLISHER_ENABLED=false
CRAVES_TOKEN_REVOCATION_ENABLED=false
CRAVES_TOKEN_REVOCATION_FAIL_CLOSED=true
CRAVES_AUTH_RATE_LIMIT_ENABLED=false
CRAVES_AUTH_RATE_LIMIT_EXCHANGE_LIMIT=0
CRAVES_AUTH_RATE_LIMIT_REFRESH_LIMIT=0
```

With all execution switches false, the new Redis classes do not change runtime request behavior.

## Redis connection secret

Every Container App must receive:

```text
SPRING_DATA_REDIS_URL
```

It must be bound through a Container App `secretRef`. Never commit the Redis URL, access key or password, and never paste it into chat.

The activation pipeline refuses an application whose Redis URL is absent or stored as a plaintext environment value.

## Three-phase rollout

Use:

```text
azure-pipelines-backend-redis-security-activation.yml
```

### Phase 1 — PUBLISHER

- verify the Auth Redis secret reference;
- enable only the Auth revocation publisher;
- keep every consumer and Auth rate limiter disabled;
- inspect outbox and Redis projection behavior.

### Phase 2 — CONSUMERS

- require the publisher to already be enabled;
- verify Redis secret references on all seven services;
- enable revocation enforcement with fail-closed behavior;
- test ACTIVE, SUSPENDED and old-token-version scenarios.

### Phase 3 — RATE_LIMITER

- require Auth publisher and consumer to be enabled;
- require explicit positive exchange/refresh limits;
- enable the Auth rate limiter only after synthetic tests.

## Rollback

Use:

```text
azure-pipelines-backend-redis-security-rollback.yml
```

Rollback order:

```text
Auth rate limiter off
→ all seven revocation consumers off
→ Auth publisher off
```

Rollback preserves:

- Auth identity state;
- revoked refresh sessions;
- Auth intervention and audit records;
- revocation outbox history;
- existing Redis TTL keys.

It does not flush Redis or delete the Redis resource.

## CI

```text
azure-pipelines-backend-redis-security-ci.yml
```

The CI gate:

- compiles/tests all seven Spring services on Java 21;
- confirms the Redis dependency exists in every service;
- confirms all execution flags default false;
- confirms public routes bypass Redis;
- confirms no destructive Redis command exists;
- scans for committed Redis credentials/URLs;
- validates the durable outbox and row-lock claims.

## Local testing

A local Redis instance can be supplied through a local-only environment variable:

```text
SPRING_DATA_REDIS_URL=redis://localhost:6379
```

Run each service independently with its normal local database prerequisites. Keep all Redis security flags false for baseline tests, then activate the publisher/consumer/rate limiter one phase at a time.

## Scale and availability risk

At the initial 50–100 concurrent-user launch target, one Redis GET per authenticated request is operationally reasonable when Redis is healthy and located close to the Container Apps.

It is not the final design for approximately one million concurrent users. Before that scale, add:

- a very short local near-cache of revocation projections;
- bounded cache invalidation/version refresh;
- Redis clustering/zone redundancy appropriate to the selected Azure tier;
- APIM or Azure Front Door edge rate controls;
- capacity/load tests for authentication and Redis connection pools;
- circuit and latency alerts;
- regional failure and failover exercises.

Do not enable fail-closed distributed revocation in production until Redis availability, networking and alerting meet the backend availability target.

## Manual and billing-sensitive work

Creating or scaling Azure Managed Redis/Cache is a billable Azure Portal/IaC action. This module does not perform it.

Manual work later includes:

1. select the approved Azure Redis offering and tier;
2. create private networking/VNet integration if required;
3. store the connection URL in each Container App secret or Key Vault reference;
4. confirm TLS and connectivity from every service;
5. configure metrics and alerts;
6. run the CI pipeline;
7. deploy all seven merged images with flags false;
8. execute the three rollout phases;
9. perform synthetic suspension and rate-limit tests;
10. retain the rollback pipeline for emergency use.

## CDN boundary

CDN/Azure Front Door remains a separate infrastructure phase. It should be implemented after this backend stack has passed CI, deployment validation, API security testing and APIM routing verification.
