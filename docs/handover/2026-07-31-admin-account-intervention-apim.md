# Craves admin account intervention APIM handover

**Date:** 31 July 2026  
**Runtime changes:** None  
**Pipeline runs:** None

## Scope

Adds a dedicated API path for the three existing Auth Service account-intervention operations. It does not enable the backend API or Firebase worker.

## Deployment order

1. Run APIM static CI.
2. Deploy and validate Auth Service with both intervention flags false.
3. Run the controlled APIM rollout with explicit confirmation.
4. Run the read-only APIM status pipeline.
5. Deploy the administrator web.
6. Validate unauthenticated 401 and authenticated ADMIN status lookup.
7. Enable the backend API through its separate activation pipeline.
8. Enable the Firebase worker only after service-account and rollback validation.

## Rollback

Disable the worker, disable the API, roll back web, then remove only the three named APIM operations. Audit and identity records are preserved.

## Manual steps later

Azure DevOps must provide `AZURE_SERVICE_CONNECTION=Craves-Dev-Service-Connection`. Any Firebase credential remains in Azure secret configuration and must never be committed or pasted into chat.

## Pending

CI, merge, deployment, gateway mutation, feature activation and live account intervention are intentionally deferred.
