# Craves Chef Web Mode Shell — Engineering Handover

1. **Date** — 2026-07-30.
2. **Repository** — `rmorampudi09-arch/Craves-Build-platform`.
3. **Branch** — `feature/chef-web-mode-shell`.
4. **Runtime status** — Code only; no deployment performed.
5. **Purpose** — Introduce the secure chef-mode entry point.
6. **Public route** — `/chef`.
7. **BFF route** — `GET /api/chef/me`.
8. **Upstream route** — `/api/v1/auth/me`.
9. **Session source** — HTTP-only `craves_access_token` cookie.
10. **Token visibility** — Browser JavaScript cannot read the token.
11. **Identity parser** — `src/lib/chef-mode-contract.ts`.
12. **Identity ID** — Validated as UUID and not rendered by the dashboard.
13. **Phone number** — Allowed only as validated identity data.
14. **Display name** — Optional and length bounded.
15. **Status** — Passed through as a bounded public identity value.
16. **Roles** — Normalized to uppercase and bounded.
17. **Chef flag** — Derived only from the `CHEF` role.
18. **Authority boundary** — The shell does not grant or persist roles.
19. **Backend authority** — Each service validates role and ownership again.
20. **Non-chef behavior** — The user is directed to chef application/status.
21. **Approved-chef behavior** — Kitchen, menu, orders and notices are shown.
22. **Customer coexistence** — Customer routes remain available.
23. **Home integration** — The customer home adds a Chef Mode card.
24. **No role mutation** — No API writes roles or approval state.
25. **No admin behavior** — No review or approval capability is included.
26. **Caching** — Chef identity responses are `no-store`.
27. **Timeout** — Upstream identity lookup is bounded to eight seconds.
28. **401 handling** — Expired session is removed by the BFF.
29. **Invalid response** — Malformed upstream identities return HTTP 502.
30. **Network failure** — Returns a public availability error without raw body.
31. **Logging** — Tokens and upstream response bodies are not logged.
32. **Browser storage** — No localStorage or sessionStorage use.
33. **Metadata** — Chef page is marked noindex/nofollow.
34. **UI palette** — Uses Craves navy, cream, gold and purple.
35. **Accessibility** — Loading/error copy uses a status region.
36. **Pipeline** — `azure-pipelines-chef-web-mode-ci.yml`.
37. **Node version** — Node.js 24.
38. **Framework gate** — Typecheck, tests and production build are required.
39. **Security scan** — Blocks token references, debug logging and browser storage.
40. **APIM dependency** — Reuses the existing Auth `/me` operation.
41. **Azure writes** — None in this module.
42. **Billable resources** — None introduced.
43. **Secrets** — No secret value committed.
44. **Firebase** — No Firebase Console change introduced.
45. **Cashfree** — No payment behavior introduced.
46. **Delivery** — No delivery worker or provider flag changed.
47. **Business rules** — No KYC, FSSAI, commission or pricing rule invented.
48. **Rollback** — Remove the `/chef` page, BFF route and home card.
49. **Next dependency** — Chef application and proof-file module.
50. **Acceptance** — CI passes at the exact PR head and role routing is verified with customer-only and approved-chef test identities.
