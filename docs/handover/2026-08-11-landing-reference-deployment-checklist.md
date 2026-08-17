# Craves landing reference deployment checklist

## Scope boundary

Customer public landing page only. No backend, APIM, Firebase, Cashfree, database, DNS, Azure resource provisioning, customer discovery, checkout, chef workspace, or mobile application changes.

## Git validation gate

Before merge:

1. Review `main...feat/landing-reference-20260811` and confirm only customer landing/UI documentation files are changed.
2. Run customer-web lint, typecheck, tests, and production build.
3. Confirm the build preparation step downloads all four approved PNGs and validates their pinned SHA-256 values.
4. Confirm the canonical `CravesLogo` source and extraction hash are unchanged.
5. Confirm authenticated-session redirect to `/home` remains unchanged.
6. Confirm desktop navigation hotspots still invoke the existing auth/chef flows and section anchors.

## Azure deployment gate

After merge to `main`, use the existing customer-web delivery pipeline only:

`azure-pipelines-customer-web-next-delivery-tracking.yml`

Established runtime parameters:

- source branch: `main`
- `confirmReplaceCurrentCustomerWeb=true`
- `cashfreeMode=sandbox`

The pipeline must use the already-established Craves Azure service connection and existing customer-web deployment target. This change must not provision a replacement Azure resource.

## Post-deployment acceptance

1. Guest landing loads without an authenticated-content flash.
2. Desktop hero visually matches the approved hero reference and displays the canonical current Craves logo.
3. How Craves Works, Why Craves, and Home Chefs/App sections match the supplied reference compositions.
4. Existing mobile/tablet responsive landing remains usable below the desktop breakpoint.
5. Customer sign-in/order action opens the existing auth flow.
6. For Chefs action opens the existing chef registration flow.
7. Footer/contact remains reachable.
8. Existing authenticated session still routes to `/home`.
