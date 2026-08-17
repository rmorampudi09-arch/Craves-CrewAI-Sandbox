# Craves Chef Web Menu Management — Engineering Handover

1. **Date** — 2026-07-30.
2. **Branch** — `feature/chef-web-menu-management`.
3. **Parent** — `feature/chef-web-kitchen-profile`.
4. **Status** — Code only.
5. **Purpose** — Manage chef-owned Catalog menu items.
6. **Page** — `/chef/menu`.
7. **Collection BFF** — `GET|POST /api/chef/menu`.
8. **Item BFF** — `PUT /api/chef/menu/{menuItemId}`.
9. **Upstream base** — `/api/v1/kitchens/me/menu-items`.
10. **Auth** — HTTP-only Craves session.
11. **Role authority** — Catalog Service validates CHEF.
12. **Ownership** — Catalog resolves the current kitchen.
13. **Kitchen ID** — Removed from browser response.
14. **Menu item ID** — UUID validated.
15. **Food types** — VEG, NON_VEG and EGG only.
16. **Statuses** — DRAFT, ACTIVE and INACTIVE only.
17. **Spice levels** — MILD, MEDIUM and SPICY when supplied.
18. **Price** — Positive and backend-persisted.
19. **Currency** — Three-character code and backend-persisted.
20. **Serves count** — Optional positive integer.
21. **Preparation time** — Optional positive integer.
22. **Package weight** — Required positive integer.
23. **Thermobox** — Exact backend boolean.
24. **Availability** — Exact backend boolean.
25. **Description** — Optional and bounded.
26. **Category** — Required and bounded.
27. **Images** — Read-only in this module.
28. **Image URL** — HTTPS only.
29. **Blob metadata** — Removed.
30. **Mutation guard** — Same-origin required.
31. **Caching** — No-store.
32. **Timeout** — Ten seconds.
33. **401 behavior** — Session cleared.
34. **403 behavior** — Approved chef/kitchen required.
35. **Invalid response** — HTTP 502.
36. **No frontend totals** — No tax, fee, commission or discount calculation.
37. **APIM path** — Existing `api/v1/kitchens/me` API.
38. **APIM prerequisite** — Kitchen profile APIM runs first.
39. **APIM operations** — List, create and update menu item.
40. **APIM policy** — Reuses bearer guard and exact Catalog backend.
41. **CI** — `azure-pipelines-chef-web-menu-ci.yml`.
42. **APIM pipeline** — `azure-pipelines-chef-menu-apim.yml`.
43. **Confirmation** — Defaults to false.
44. **Tests** — Private fields, unsafe image URLs and invalid prices.
45. **Secrets** — None committed.
46. **Runtime writes** — None during development.
47. **Rollback** — Remove only the three menu operations and frontend files.
48. **Next module** — Availability switching and image upload.
49. **Manual later** — CI, APIM, then authenticated Catalog smoke tests.
50. **Acceptance** — Create/edit/list tests pass without exposing storage metadata or bypassing Catalog ownership.
