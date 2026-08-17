# Craves seven-service backend guarded release

## Purpose

This runbook governs the Spring Boot 3 / Java 21 backend release for Auth, Notification, User-Chef, Catalog, Integration, Subscription, and Order Services. It deliberately excludes customer/chef/admin web deployment, React Native deployment, APIM mutations, billable Azure provisioning, and external-provider activation.

## Safety model

- Default mode is `VERIFY_ONLY`; no Azure resource is mutated.
- All seven Maven projects run `clean verify` on Java 21 before image creation.
- Images are built from source, run as UID/GID `10001`, and are tagged with the full Git commit SHA.
- Deployment uses ACR repository digests, not mutable tags.
- Every existing Container App environment value and secret reference is hashed before and after an image-only update.
- All seven target Container Apps and all seven image digests are verified before the first mutation.
- If any revision fails Azure readiness, revision health, public health smoke, or environment-preservation checks, every service already updated by that run is rolled back in reverse order.
- Flyway is forward-only; deployment mode requires explicit confirmation that a restorable PostgreSQL backup/restore point exists.
- Cashfree production, refund execution, delivery-provider execution, FCM, ACS Email, subscription workers, and launch-policy enforcement are not activated by this pipeline.

## Azure DevOps pipeline

Create a pipeline from:

```text
/azure-pipelines-backend-completion.yml
```

Required non-secret pipeline variable:

```text
AZURE_SERVICE_CONNECTION = Craves-Dev-Service-Connection
```

Known guarded inventory:

```text
Resource group: rg-craves-prodlow-centralindia
ACR: cravesprodlowacr82121
Target sizing: 50–100 concurrent users
```

## Run sequence

### 1. Verify source only

```text
releaseMode: VERIFY_ONLY
confirmBuild: DO_NOT_BUILD
confirmDeployment: DO_NOT_DEPLOY
databaseBackupConfirmation: DATABASE_BACKUP_NOT_VERIFIED
```

Expected result: source validation, secret scan, Flyway ordering, Docker hardening, and seven Maven test suites pass. No Azure mutation occurs.

### 2. Build immutable images

```text
releaseMode: BUILD_IMAGES
confirmBuild: BUILD_SEVEN_SERVICES
```

Expected result: seven ACR images are tagged with the exact 40-character source SHA, resolved to digests, and published in `backend-image-manifest`. No Container App changes occur.

### 3. Deploy the backend release

Before this mode, verify a restorable backup/restore point for the Craves PostgreSQL databases.

```text
releaseMode: DEPLOY_BACKEND
confirmBuild: BUILD_SEVEN_SERVICES
confirmDeployment: DEPLOY_SEVEN_SERVICES
databaseBackupConfirmation: DATABASE_BACKUP_VERIFIED
resourceGroupName: rg-craves-prodlow-centralindia
containerRegistryName: cravesprodlowacr82121
```

The run publishes:

- `backend-source-evidence`
- `backend-image-manifest`
- `backend-deployment-evidence`
- JUnit results for all seven services
- previous images and ready revisions for rollback
- environment hashes and service health evidence

## Deployment order

1. Auth Service
2. Notification Service
3. User-Chef Service
4. Catalog Service
5. Integration Service
6. Subscription Service
7. Order Service

## Manual steps required

- Azure DevOps: create/authorize the pipeline and provide `AZURE_SERVICE_CONNECTION`.
- PostgreSQL: verify a restorable backup/restore point before deployment mode.
- Azure billing: no new resource is created by this pipeline.
- Secrets: keep all credentials in existing Key Vault/Container App secret references; do not paste secret values into chat or normal variables.
- Providers: production credentials, KYC, webhook registration, and activation remain separate controlled gates.

## Local verification

Run from repository root:

```bash
python -m pip install 'PyYAML==6.0.2'
bash scripts/release/validate-backend-completion-pack.sh
bash scripts/release/scan-secret-material.sh .
python scripts/release/validate-flyway-migrations.py
for service in $(jq -r '.services[].path' config/production/backend-completion-pack.json); do
  bash scripts/release/validate-dockerfiles.sh "$service"
  (cd "$service" && mvn -B -ntp clean verify)
done
```

## Explicitly deferred product and commercial decisions

Do not encode or activate pricing, commissions, delivery radius, cancellation penalties, refund eligibility beyond the documented lifecycle, FSSAI policy, subscription benefits/pricing, delivery-provider commercial ranking, or production-provider credentials without approved product/functional input.
