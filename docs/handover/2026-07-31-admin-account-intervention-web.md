# Craves admin account intervention web handover

**Date:** 31 July 2026  
**Scope:** Next.js administrator access to existing Auth Service intervention APIs  
**Runtime changes:** None  
**Pipeline runs:** None

## 1. Outcome

The administrator can load one exact identity UUID, review privacy-reduced status and submit an audited suspend/reactivate request.

## 2. Files

- `apps/customer-web-next/src/lib/admin-account-intervention-contract.ts`
- `apps/customer-web-next/src/lib/admin-account-intervention-contract.test.ts`
- `apps/customer-web-next/src/app/api/admin/accounts/[identityId]/route.ts`
- `apps/customer-web-next/src/components/admin-account-intervention.tsx`
- `apps/customer-web-next/src/app/admin/accounts/page.tsx`
- `apps/customer-web-next/src/components/admin-shell.tsx`
- `apps/customer-web-next/modules/admin-account-intervention/README.md`
- `azure-pipelines-admin-account-intervention-web-ci.yml`

## 3. Backend ownership

Auth Service remains the sole owner of identity status, token version, refresh-session revocation, audit evidence and durable Firebase synchronization.

## 4. UI safeguards

The user must first load status for the exact UUID. The action form remains disabled until a validated response is returned. A 10–500 character reason and exact typed action are mandatory.

## 5. Privacy

Only the backend masked phone value and bounded operational fields are rendered. Firebase UID, raw phone, access tokens, refresh tokens and provider payloads are not accepted or displayed.

## 6. Failure behavior

Malformed backend responses are rejected. Disabled feature flags return a controlled unavailable message. Self-suspension and invalid transitions remain blocked by Auth Service.

## 7. Deployment dependencies

1. PR stack merged parent-first.
2. Auth Service deployed with intervention flags false.
3. APIM child module configured.
4. Web deployed.
5. Read-only status validated.
6. API flag activated only through the existing guarded pipeline.
7. Firebase worker activated separately after credentials and rollback evidence are validated.

## 8. Manual steps later

- Azure DevOps: run CI and activation pipelines in the approved sequence.
- Azure Portal/CLI: verify Auth Container App revision and secret references.
- Firebase Console: no manual disable/enable should be performed during deployment; validate service-account permissions only.
- Secrets: never paste Firebase credentials into chat or source control.

## 9. Local test

```bash
cd apps/customer-web-next
npm install --ignore-scripts
npm run typecheck
npm run test
npm run build
```

## 10. Rollback

Rollback the web revision first, then APIM operations if necessary. Disabling the backend API and worker is non-destructive and preserves audit evidence.

## 11. Pending

CI, merge, deployment, APIM configuration, feature activation and live account tests are intentionally deferred to the controlled pipeline session.
