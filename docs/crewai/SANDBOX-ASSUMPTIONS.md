# SANDBOX_ASSUMPTIONS

- CRV-ARCH-HLD-002 v2.0 and CRV-FUNC-001 v1.0 were not machine-readable in-repo during this sandbox run, so explicit repository code, existing handover docs, and current contracts were treated as authoritative implementation evidence.
- The approved web implementation surface remains `apps/customer-web-next`; legacy `apps/customer-web` and `apps/admin-portal` are retained as historical/reference-only paths and are not the primary Next.js delivery surface.
- Admin Console is delivered inside the approved Next.js app with secure route segmentation and server-side BFF enforcement unless a stronger dedicated approved app already exists.
- Admin dashboard data must remain operational and privacy-minimised: counts, workflow status, and bounded exception summaries are allowed; customer PII, payment secrets, raw webhook payloads, and unrestricted audit data are not exposed to browser clients.
- Missing live vendor credentials, Azure provisioning, Razorpay activation, or Syncfusion license activation do not block sandbox code completion; static adapters, contracts, environment hooks, and documentation are completed instead.
- Static validation only is performed in this autonomous run. Runtime commands such as `npm ci`, `npm run build`, `npm test`, GitHub Actions, Azure DevOps, and Azure deployment are not executed here and must be recorded as pending final human verification.
- Mobile scope is unchanged from repository reality: readiness/documentation only unless a committed React Native baseline is present.
- Any missing requirement on dashboard UX is resolved with a reversible sandbox default favouring accessibility, bounded data volume, responsive layouts, deterministic formatting, and secure server-side authorization.
- Existing Razorpay support must be preserved as first-class; no web change may remove or downgrade Razorpay readiness paths.
- Existing admin and customer/chef BFF contracts remain backward-compatible unless a strict parser rejects undocumented fields for safety; reject-by-default contract parsing is an approved sandbox hardening choice.
