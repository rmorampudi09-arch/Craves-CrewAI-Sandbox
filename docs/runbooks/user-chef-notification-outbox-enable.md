# User-Chef Notification Outbox Deployment Runbook

This runbook continues the Notification + Outbox work for Craves.

## Current known state

- Repository: `rmorampudi09-arch/Craves-Build-platform`
- Resource group: `rg-craves-prodlow-centralindia`
- User-Chef Container App: `ca-craves-user-chef-service-prod`
- Notification Container App: `ca-craves-notification-service-p`
- Order Service is already in outbox-first notification mode.
- User-Chef Service notification outbox foundation is committed and must be deployed before enabling outbox-first mode.

## Why this runbook exists

Chef application approval/rejection notifications should not depend only on direct HTTP calls from User-Chef Service to Notification Service.

Target flow:

```text
Admin approves/rejects chef application
→ User-Chef Service writes notification intent into notification_outbox
→ User-Chef dispatcher sends notification to Notification Service
→ Chef sees in-app notification
```

## Step 1 — Run User-Chef Service pipeline

Run the User-Chef Service pipeline first so the latest code and Flyway migration are deployed.

The pipeline must deploy the image that contains:

```text
services/user-chef-service/src/main/resources/db/migration/V2__notification_outbox.sql
services/user-chef-service/src/main/java/in/craves/userchef/service/ChefNoticeOutboxEvent.java
services/user-chef-service/src/main/java/in/craves/userchef/service/PendingChefNoticeOutboxEvent.java
services/user-chef-service/src/main/java/in/craves/userchef/service/ChefNoticeOutboxRepository.java
services/user-chef-service/src/main/java/in/craves/userchef/service/ReviewEventBuffer.java
services/user-chef-service/src/main/java/in/craves/userchef/config/ChefNoticeDispatcherProperties.java
services/user-chef-service/src/main/java/in/craves/userchef/service/ChefNoticeDispatcher.java
services/user-chef-service/src/main/java/in/craves/userchef/service/NotificationInternalClient.java
```

Do not disable direct dispatch before this pipeline finishes successfully.

## Step 2 — Check current state

From Azure Cloud Shell at repo root:

```bash
chmod +x scripts/check-notification-outbox-state.sh
./scripts/check-notification-outbox-state.sh
```

Expected:

```text
Order Service:
  CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED=true
  CRAVES_NOTIFICATION_DIRECT_DISPATCH_ENABLED=false

User-Chef Service before safe enablement:
  dispatcher may be false
  direct dispatch should remain true
```

## Step 3 — Enable User-Chef outbox in safe parallel mode

After the User-Chef pipeline has deployed successfully:

```bash
chmod +x scripts/enable-user-chef-notification-outbox-safe.sh
./scripts/enable-user-chef-notification-outbox-safe.sh parallel
```

This sets:

```text
CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED=true
CRAVES_NOTIFICATION_DIRECT_DISPATCH_ENABLED=true
```

This is intentional. In this mode, the existing direct notification path remains available while the outbox dispatcher is verified.

## Step 4 — Perform one chef approval/rejection test

Use one admin and one chef user.

Test either:

```text
POST /api/v1/backoffice/chef-reviews/{applicationId}/approve
```

or:

```text
POST /api/v1/backoffice/chef-reviews/{applicationId}/reject
```

Then check logs:

```bash
az containerapp logs show \
  --name "ca-craves-user-chef-service-prod" \
  --resource-group "rg-craves-prodlow-centralindia" \
  --tail 500 | grep -iE "outbox|Review event buffered|Chef notice|event sent|dispatcher"
```

Expected log patterns:

```text
Review event buffered ...
Chef notice outbox event sent ...
```

Then check chef notification inbox through APIM:

```bash
APIM_URL="https://api.craves.in"
CHEF_TOKEN="paste_chef_craves_access_token_here"

curl -sS "$APIM_URL/api/v1/notifications/in-app" \
  -H "Authorization: Bearer $CHEF_TOKEN" | jq
```

Expected:

```text
Chef profile approved
```

or:

```text
Chef profile needs changes
```

Also confirm the notification appears once, not duplicated.

## Step 5 — Switch User-Chef to outbox-first

Only after Step 4 succeeds:

```bash
./scripts/enable-user-chef-notification-outbox-safe.sh outbox-first
```

This sets:

```text
CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED=true
CRAVES_NOTIFICATION_DIRECT_DISPATCH_ENABLED=false
```

## Rollback

If chef review notifications fail after switching to outbox-first, restore direct dispatch while investigating:

```bash
RG="rg-craves-prodlow-centralindia"
USER_CHEF_APP="ca-craves-user-chef-service-prod"

az containerapp update \
  --name "$USER_CHEF_APP" \
  --resource-group "$RG" \
  --set-env-vars \
    CRAVES_NOTIFICATION_DIRECT_DISPATCH_ENABLED="true" \
    FORCE_RESTART="$(date +%s)" \
  -o table
```

## Important safety rules

- Do not paste `CRAVES_NOTIFICATION_INTERNAL_KEY` into chat.
- Do not disable direct dispatch before the User-Chef pipeline deploys the outbox code.
- Do not switch to outbox-first until one approval/rejection test is verified.
- Do not create new paid Azure resources for this step.

## Next after this

After User-Chef outbox-first is stable, the next backend hardening step is to move any best-effort outbox writes into the same transaction as the domain state change where practical, then continue with Cashfree UI payment and delivery-provider flow.
