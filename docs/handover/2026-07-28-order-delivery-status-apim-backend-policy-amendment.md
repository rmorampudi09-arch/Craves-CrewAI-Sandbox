# Delivery Status APIM Handover Amendment — Inherited Backend Policy Guard

**Date:** 2026-07-28  
**Parent handover:** `2026-07-28-order-delivery-status-apim.md`  
**Branch:** `feature/delivery-status-apim-route`

## 1. Reason for this amendment

The final review against the current Microsoft API Management policy documentation identified an inheritance limitation:

```text
When an inherited base policy selects a backend using backend-id,
an operation policy cannot safely override that selection using base-url.
```

The delivery-status operation uses an exact dynamic Container App `base-url`, so silently applying it above an inherited `backend-id` policy could produce policy compilation or request-routing failure.

## 2. Fail-closed correction

The configure and verification scripts now read:

```text
APIM service/global policy
API-level policy for the resolved Order API
```

They search for inherited statements shaped like:

```xml
<set-backend-service backend-id="..." />
```

If found, the scripts stop before writing or validating the operation.

## 3. Operator error message

The configuration path reports that an approved APIM backend-entity design is required instead of attempting an incompatible `base-url` override.

## 4. Why the script does not create a backend entity automatically

Creating or changing a shared APIM backend entity can affect other APIs and may require authentication, managed identity, certificate, header, circuit-breaker, or network settings that are outside this single operation's scope.

The module therefore refuses ambiguous inherited routing rather than inventing shared gateway architecture.

## 5. CI coverage

The build-only APIM CI pipeline now verifies that:

```text
configure script contains inherited backend-id rejection
verification script repeats the compatibility check
```

## 6. Runtime consequence

Normal environments without inherited `backend-id` routing continue using the operation-level dynamic backend:

```text
https://<order-container-app-fqdn>/api/v1/orders
```

Environments with inherited backend-ID routing require a separate reviewed APIM backend module before this route can be activated.

## 7. Safety conclusion

This amendment introduces no Azure resource, API operation, provider call, secret, or runtime activation.

It makes the later rollout more conservative by converting a possible misrouting condition into an explicit pre-deployment failure.
