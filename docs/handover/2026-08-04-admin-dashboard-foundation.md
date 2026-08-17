# Craves Admin Dashboard Foundation — Engineering Handover

Date: 2026-08-04  
Status: implemented locally; not deployed  
Module: secure admin workspace and Order Service operational summary

## Outcome

The existing Next.js administrator tools are now integrated into one responsive workspace with a persistent navigation shell, verified administrator identity, live Order Service metrics, Syncfusion charts and a privacy-reduced exception grid. The implementation reuses the existing `ADMIN` role and does not grant, mutate or infer roles in the browser.

The admin UI has a dedicated container build and a new Azure pipeline that targets a separate, pre-created Azure Container App. A separate GitHub Actions YAML performs pull-request validation only. The deployment pipeline is deliberately update-only and refuses to create a billable Azure resource.

## Preserved product behaviour

No pricing, commission, refund eligibility, delivery-radius or FSSAI rule was introduced or changed. Dashboard counts are direct representations of documented order statuses already stored by Order Service. Existing administrator mutations remain owned and validated by their current Spring services.

## Runtime flow

1. The administrator opens `/admin` on the dedicated admin Container App.
2. the workspace calls `/api/admin/me` using the existing HTTP-only Craves session cookie.
3. Auth Service must return an active identity containing the existing `ADMIN` role.
4. the dashboard calls the same-origin BFF route `/api/admin/dashboard/summary`.
5. the BFF forwards the Bearer access token to APIM and validates the response against a strict allow-list.
6. APIM routes `GET /api/v1/admin/dashboard/summary` to Order Service.
7. Order Service revalidates the token, independently requires `ADMIN`, and returns no-store aggregate data.

## Changed paths

### Order Service

- `services/order-service/src/main/java/in/craves/order/admin/AdminDashboardController.java`
  - exposes `GET /api/v1/admin/dashboard/summary`
  - returns `401` without a Craves principal and `403` without `ADMIN`
  - adds `Cache-Control: no-store`
- `services/order-service/src/main/java/in/craves/order/admin/AdminDashboardService.java`
  - counts current workflow stages and 24-hour created/delivered activity
  - returns a seven-day UTC trend
  - returns at most ten privacy-reduced exception rows
- `services/order-service/src/main/resources/db/migration/V13__admin_dashboard_indexes.sql`
  - indexes created time, exception update time and delivered update time
- `services/order-service/src/test/java/in/craves/order/admin/AdminDashboardControllerTest.java`
  - verifies non-admin rejection and admin no-store success

### Next.js administrator application

- `apps/customer-web-next/src/app/admin/layout.tsx`
  - applies the shared administrator workspace and Syncfusion component styles
- `apps/customer-web-next/src/components/admin-workspace.tsx`
  - responsive sidebar, mobile navigation, identity gate and current-route state
- `apps/customer-web-next/src/components/admin-dashboard.tsx`
  - live loading, refresh, KPI cards, empty/error states and module shortcuts
- `apps/customer-web-next/src/components/admin-dashboard-visuals.tsx`
  - client-only Syncfusion column chart and paged/sortable exception grid
- `apps/customer-web-next/src/components/syncfusion-license.tsx`
  - registers the vendor browser license when configured
- `apps/customer-web-next/src/lib/admin-dashboard-contract.ts`
  - strict allow-list parser; discards unapproved fields
- `apps/customer-web-next/src/lib/admin-dashboard-contract.test.ts`
  - validates safe parsing and malformed-input rejection
- `apps/customer-web-next/src/app/api/admin/dashboard/summary/route.ts`
  - authenticated same-origin BFF read with timeout, no-store and safe errors
- `apps/customer-web-next/src/proxy.ts`
  - in the dedicated image, restricts routes to admin pages and required auth/admin APIs
- `apps/customer-web-next/Dockerfile.admin`
  - creates the separate non-root administrator image
- `apps/customer-web-next/Dockerfile`
  - accepts the Syncfusion browser license for the shared web build
- `apps/customer-web-next/.env.example`
  - documents `NEXT_PUBLIC_SYNCFUSION_LICENSE_KEY`
- `apps/customer-web-next/package.json` and `package-lock.json`
  - fixed Syncfusion EJ2 chart/grid/theme versions

The six existing administrator page routes and the chef-review detail route were restyled to use the common workspace without changing their business operations.

### APIM and delivery automation

- `infra/apim/admin-dashboard/authenticated-policy.xml`
- `infra/apim/admin-dashboard/README.md`
- `scripts/apim/configure-admin-dashboard-apim.sh`
- `azure-pipelines-admin-dashboard-apim.yml`
- `azure-pipelines-admin-dashboard.yml`
- `azure-pipelines-admin-dashboard-rollback.yml`
- `.github/workflows/admin-dashboard-ci.yml`

## Data contract

The summary returns:

- `generatedAt`
- `metrics.ordersCreated24h`
- `metrics.chefAcceptancePending`
- `metrics.preparing`
- `metrics.readyForPickup`
- `metrics.outForDelivery`
- `metrics.refundPending`
- `metrics.refundFailed`
- `metrics.delivered24h`
- bounded `statusCounts`
- seven UTC `orderTrend` points
- at most ten exception rows containing only order ID, kitchen display name, status and update time

It does not return customer identity IDs, phone numbers, delivery addresses, monetary values, Cashfree identifiers, delivery-provider identifiers, webhook payloads or audit reasons.

## Validation completed

- Next.js lint: passed with zero warnings
- TypeScript: passed
- customer-web test suite: 103 tests passed
- production Next.js build: passed; `/admin` and `/api/admin/dashboard/summary` emitted successfully
- full Order Service test suite: 49 tests passed, including the 2 new dashboard authorization tests
- Syncfusion dashboard contract tests: passed
- APIM shell script syntax: passed using Git Bash
- Azure and GitHub YAML parsing/format check: passed with Prettier
- npm audit: no critical finding; one moderate and two high findings remain in transitive build dependencies

No live Azure write, APIM write, ACR push, Container App creation or production data read was performed.

## Manual steps required

### Syncfusion

- Sign in to the Syncfusion account and obtain the EJ2 JavaScript license key covered by the Craves subscription/community entitlement.
- Add it as a secret Azure DevOps variable named `NEXT_PUBLIC_SYNCFUSION_LICENSE_KEY`.
- Do not paste the value into chat, Git or a committed `.env` file.

### Azure Portal — billing-sensitive

- Review cost before creating the separate Container App `ca-craves-admin-web-prodlow`.
- Create it in the existing Craves Container Apps environment; do not create a second environment unless networking or compliance requires it.
- Configure external HTTPS ingress on port 3000.
- Configure ACR pull access using the established managed-identity pattern.
- Set the replica and scaling limits from measured load tests and the approved availability target. The pipeline intentionally preserves those settings.
- Protect the admin hostname with the approved Front Door/WAF and access policy before production use.

### Azure DevOps

- Create/verify the service connection variable `AZURE_SERVICE_CONNECTION`.
- Create an environment named `craves-admin-prodlow` and add the required production approval.
- Supply `CRAVES_API_BASE_URL`, Firebase public web identifiers, `NEXT_PUBLIC_CASHFREE_MODE` and `NEXT_PUBLIC_SYNCFUSION_LICENSE_KEY` through pipeline variables/Key Vault integration.
- Create pipelines from `azure-pipelines-admin-dashboard.yml` and `azure-pipelines-admin-dashboard-apim.yml`.
- Create the manual rollback pipeline from `azure-pipelines-admin-dashboard-rollback.yml`.

### Deployment order

1. Merge and deploy Order Service so Flyway migration V13 and the summary endpoint are healthy.
2. Run the APIM pipeline with `confirmApimWrite=true`.
3. Confirm unauthenticated APIM access returns `401` and an authenticated non-admin returns `403`.
4. Create/approve the separate admin Container App if it does not already exist.
5. Run the admin dashboard pipeline and approve the `craves-admin-prodlow` deployment.
6. Sign in with an existing active `ADMIN` identity and verify each navigation module.
7. Record the previous image digest/tag before traffic promotion for rollback.

## Local test instructions

From `apps/customer-web-next`:

```text
npm ci
npm run lint
npm run typecheck
npm run test
npm run build
```

Set `CRAVES_API_BASE_URL` to an HTTPS APIM/test endpoint and configure Firebase public web values. Use `CRAVES_ADMIN_PORTAL=true` to exercise the dedicated-image route restriction. A real Syncfusion key is not required to compile, but it is required to remove the license notice in an entitled deployment.

From `services/order-service` with Java 21:

```text
mvn -B -ntp -Dtest=AdminDashboardControllerTest test
```

## Risks and controls

### Dependency advisories

The npm audit currently reports high advisories in `postcss` and `brace-expansion`. The available full automated repair would move Next.js outside the repository's currently pinned version, so no forced dependency upgrade was made silently. Schedule a controlled Next.js/ESLint upgrade to a patched stable release, then rerun the full 103-test suite and production build.

### Scale

The dashboard performs bounded aggregate reads with supporting indexes, but it is not a long-term analytics warehouse. At very high order volume, move historical trend aggregation to a read replica/materialized projection or approved analytics store. Do not point wide ad-hoc BI queries at the transactional primary.

### Refresh and freshness

Refresh is operator-triggered and every response is no-store. No polling interval or freshness SLA was invented. Product/Operations must approve any automatic refresh interval before it is introduced.

### Separate image boundary

The dedicated image is built from the existing Next.js codebase to reuse the reviewed Firebase/BFF security implementation. Runtime proxy rules expose only admin/auth pages and APIs on that image. This is deployment isolation, not a separate source repository. If organizational separation is required, create a dedicated Next.js package only after agreeing how shared auth contracts will be versioned.

### Admin access

The browser identity check is a usability gate, not the security boundary. Every backend endpoint remains responsible for its own `ADMIN` authorization. Never add browser-only authorization or role mutation.

## Remaining gaps

- The Azure resource and hostname have not been created because they are billable/manual actions.
- APIM has not been changed; its pipeline requires explicit approval after backend deployment.
- No authenticated end-to-end smoke test was run against live Azure.
- Automatic dashboard refresh, alert thresholds, SLA targets and executive commercial analytics require documented product/operations decisions.
- npm dependency advisories require a controlled framework upgrade.
- Load, accessibility and browser-compatibility testing should be executed in the deployment environment before production traffic.

## Sign-ins still required

No additional sign-in is required to continue local engineering. Deployment will require Krishna to be signed in to the approved Azure/Azure DevOps tenant, and the Syncfusion license must be copied into the secret variable directly from the signed-in vendor account. No secrets should be shared in chat.
