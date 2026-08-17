# Craves Chef Web Kitchen Profile — Engineering Handover

1. **Date** — 2026-07-30.
2. **Branch** — `feature/chef-web-kitchen-profile`.
3. **Parent** — `feature/chef-web-application`.
4. **Status** — Code only.
5. **Purpose** — Manage the chef-owned Catalog kitchen.
6. **Page** — `/chef/kitchen`.
7. **BFF** — `GET|PUT /api/chef/kitchen`.
8. **Upstream** — `GET|PUT /api/v1/kitchens/me`.
9. **Authentication** — HTTP-only Craves session.
10. **Role authority** — Catalog Service requires CHEF.
11. **Ownership** — Catalog Service resolves the current chef kitchen.
12. **Identity ID** — Removed from browser response.
13. **Required fields** — Kitchen name, address line 1, city and state.
14. **Optional fields** — Display name, description, contact and secondary address fields.
15. **Coordinates** — Optional paired values.
16. **Read statuses** — Draft, active, inactive and suspended.
17. **Editable statuses** — Draft, active and inactive.
18. **Suspended behavior** — Read-only UI.
19. **Backend override** — Backend remains authoritative regardless of UI restrictions.
20. **Create behavior** — GET 404 maps to an empty creation form.
21. **Update behavior** — PUT uses exact `KitchenProfileRequest` fields.
22. **Mutation guard** — Same-origin required.
23. **Caching** — No-store.
24. **Timeout** — Ten seconds.
25. **401 behavior** — Session cookie is removed.
26. **403 behavior** — Approved chef access message.
27. **Invalid response** — Returns HTTP 502.
28. **UI** — Craves chef profile form.
29. **No geocoding** — Coordinates are not resolved by the frontend.
30. **No radius logic** — Serviceability is not calculated.
31. **No ranking logic** — Discovery ranking is not calculated.
32. **No approval logic** — Kitchen approval is not invented.
33. **APIM path** — `api/v1/kitchens/me`.
34. **APIM API** — Dedicated only when no path owner exists.
35. **Path-owner rule** — Multiple owners cause failure.
36. **Subscription rule** — Existing requirements are never relaxed.
37. **Inheritance rule** — `backend-id` inheritance causes failure.
38. **Backend health** — Catalog must be ready and healthy.
39. **Operations** — Get and upsert profile.
40. **Policy** — Bearer guard and no-store response.
41. **CI** — `azure-pipelines-chef-web-kitchen-profile-ci.yml`.
42. **APIM pipeline** — `azure-pipelines-chef-kitchen-profile-apim.yml`.
43. **Confirmation** — Defaults to false.
44. **Tests** — Identity privacy, suspension and coordinate pairing.
45. **Secrets** — None committed.
46. **Azure writes** — None during development.
47. **Rollback** — Remove only profile operations and frontend page/BFF.
48. **Next module** — Menu item management.
49. **Manual later** — Run CI, then APIM with the established service connection.
50. **Acceptance** — Exact-head CI, authenticated profile create/update smoke and suspended-state verification pass.
