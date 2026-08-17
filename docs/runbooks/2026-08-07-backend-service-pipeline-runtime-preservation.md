# Backend Service Pipeline Runtime Preservation

Date: 2026-08-07
Status: implementation ready for CI validation
Scope: Auth, User-Chef, Order, Subscription, Integration and Notification service-specific Azure DevOps pipelines

## Why this change exists

The seven Craves backend Container Apps have completed active-secret migration to Azure Key Vault using managed identity. After that migration, a review of the service-specific Azure DevOps pipelines found legacy deployment tasks that could undo the secured runtime state.

The legacy patterns included:

- writing pipeline credential values back into Container App local secrets with `az containerapp secret set`
- rebinding environment variables to those local secret objects
- reconstructing runtime environment variables during every image deployment
- forcing feature/provider flags to hard-coded values
- changing ingress or replica settings during an application-image release
- copying the Auth internal secret into Notification as a local Container App secret

Those behaviors are no longer appropriate after the Key Vault migration because a normal code deployment must not also become an implicit runtime-configuration or credential deployment.

## Files

Shared deployment helper:

```text
scripts/release/deploy-single-service-preserve-runtime.sh
```

Updated service pipelines:

```text
azure-pipelines-auth-service.yml
azure-pipelines-user-chef-service.yml
azure-pipelines-order-service.yml
azure-pipelines-subscription-service.yml
azure-pipelines-integration-service.yml
azure-pipelines-notification-service.yml
```

Regression gate:

```text
scripts/release/validate-backend-completion-pack.sh
```

Catalog was hardened separately in PR #159 and remains protected by its existing image-only deployment flow.

## Deployment behavior

Each updated service pipeline now performs only these responsibilities:

1. checkout source
2. use Java 21
3. run Maven `clean verify`
4. build an immutable service image
5. push that image to the existing ACR
6. invoke the shared runtime-preserving deployment helper

The deployment helper performs an image-only `az containerapp update --image ...`.

It does not call:

```text
az containerapp secret set
az containerapp ingress update
--set-env-vars
--replace-env-vars
--min-replicas
--max-replicas
```

## Pre-deployment security checks

Before the image is changed, the helper reads only metadata and verifies:

- the Container App exists
- a ready rollback revision exists
- the previous image is known
- the target image is a new immutable image
- every active Container App `secretRef` points to a Key Vault-backed Container App secret
- the Key Vault reference uses managed identity

No secret values are requested or displayed.

## Runtime preservation fingerprints

The helper records four independent fingerprints before deployment:

```text
runtime template hash
Container App configuration hash
managed identity hash
secret metadata hash
```

The runtime-template fingerprint excludes only the container image and revision suffix, because those are expected to change during a deployment.

The other runtime properties remain part of the preservation contract, including environment variables, secret references, probes, resources, scale settings and other revision-template configuration.

The Container App configuration fingerprint excludes ingress traffic allocation because Azure may update revision traffic as a new revision becomes current. Other configuration remains protected.

The secret metadata fingerprint includes only:

```text
secret object name
Key Vault URL
managed identity reference
```

Secret values are never read.

## Health verification

The helper waits for the new revision to satisfy all of these conditions:

```text
latest revision == latest ready revision
Container App running status == Running
Azure revision health state == Healthy
revision image == requested target image
```

For Container Apps with external ingress, it then verifies:

```text
/actuator/health/liveness  -> HTTP 200 and status UP
/actuator/health/readiness -> HTTP 200 and status UP
```

If ingress is internal, external curl validation is skipped and Azure revision health remains the deployment readiness signal.

## Automatic rollback

If the new revision fails readiness, runtime preservation, Key Vault preservation, identity preservation or health validation, the helper performs image-only rollback to the previous image.

After rollback it verifies that the original runtime-template, configuration, identity and secret-metadata fingerprints are restored.

The rollback does not write secret values and does not reconstruct environment variables.

## Regression prevention

`scripts/release/validate-backend-completion-pack.sh` now rejects service pipeline changes that reintroduce any of these deployment mutations:

```text
--set-env-vars
--replace-env-vars
az containerapp secret set
az containerapp ingress update
--min-replicas
--max-replicas
```

It also requires the six updated pipelines to invoke the shared runtime-preserving deployment helper and validates the helper with `bash -n`.

## Manual steps required

No Azure Portal resource creation is required.

No new Azure resource is created and no billing-sensitive infrastructure is provisioned.

No new Key Vault secret is required.

No credential or password value needs to be supplied to these pipelines.

The pipelines continue to require the existing Azure DevOps service connection variable:

```text
AZURE_SERVICE_CONNECTION=Craves-Dev-Service-Connection
```

After this change is merged, future individual-service deployments should always be started from `main` so the runtime-preserving pipeline definition is used.

## Local validation

From the repository root:

```bash
python -m pip install 'PyYAML==6.0.2'
bash scripts/release/validate-backend-completion-pack.sh
bash -n scripts/release/deploy-single-service-preserve-runtime.sh
```

Service code remains testable independently, for example:

```bash
cd services/auth-service
mvn -B -ntp clean verify
```

Repeat for the service being changed.

## Deliberately deferred

This work does not:

- rotate credentials
- delete historical local Container App secret objects
- retire old revisions
- normalize the two version-pinned shared Key Vault references
- enable Redis/token revocation
- activate Cashfree production execution
- activate Borzo production execution
- change refund, delivery, notification or subscription worker flags
- provision new Azure resources

Credential rotation remains deferred until the final product security phase.
