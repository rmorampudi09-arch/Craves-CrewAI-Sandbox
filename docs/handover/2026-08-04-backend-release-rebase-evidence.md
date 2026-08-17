# Backend guarded-release rebase evidence

## Baseline

- Repository: `rmorampudi09-arch/Craves-Build-platform`
- Base branch: `main`
- Base commit: `d9e0d025a5da32afb24aee7df82bd22a829b4660`
- Superseded draft: PR #118 (`agent/backend-completion-guarded-release`)
- Original draft divergence at rebase start: 15 commits ahead, 85 commits behind current `main`

## Reconciliation performed

The guarded release work was reconstructed on a fresh branch from current `main` rather than merging the stale draft. The following controls were preserved and updated:

- canonical seven-service Azure/ACR inventory
- source-only ACR builds for every Spring Boot service
- Java 21 Maven verification for all seven services
- non-root runtime UID/GID `10001`
- full source-SHA image tags and digest-pinned deployment
- preflight validation of all Container Apps and image digests
- preservation hash for existing environment values and secret references
- Azure revision readiness and health-state verification
- public health smoke after revision readiness
- reverse-order full-release rollback on failure
- explicit build, deploy, and database-backup confirmations
- published source, image, deployment, and rollback evidence
- external-provider and background-worker activation excluded from the release pipeline

## Additional validation added during rebase

A permanent GitHub Actions workflow now runs:

- guarded release contract validation
- repository secret-material scan
- Flyway filename/order/destructive-operation validation
- Dockerfile hardening validation
- parallel Java 21 `mvn clean verify` for Auth, Notification, User-Chef, Catalog, Integration, Subscription, and Order Services

## Safety boundary

This branch does not create Azure resources, deploy Container Apps, change APIM, rotate secrets, change environment variables, enable Cashfree production, execute refunds, enable delivery providers, enable FCM/ACS delivery, or start subscription workers.
