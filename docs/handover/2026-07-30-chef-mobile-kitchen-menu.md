# Craves Mobile Chef Kitchen and Menu — Engineering Handover

1. **Date** — 2026-07-30.
2. **Branch** — `feature/chef-mobile-kitchen-menu`.
3. **Parent** — `feature/chef-mobile-mode-shell`.
4. **Status** — Code only.
5. **Purpose** — Add owned native kitchen/menu management.
6. **Kitchen screen** — Replaces ChefKitchen placeholder.
7. **Menu editor** — New typed route.
8. **Session** — Device-secure Craves session.
9. **Role authority** — Catalog Service validates CHEF.
10. **Ownership** — Catalog Service resolves current kitchen.
11. **Kitchen GET** — `/api/v1/kitchens/me`.
12. **Kitchen PUT** — Exact Catalog update.
13. **No-kitchen state** — HTTP 404 maps to creation form.
14. **Suspended state** — Read-only profile.
15. **Coordinates** — Optional paired values.
16. **Geocoding** — Not introduced.
17. **Serviceability** — Not calculated.
18. **Menu list** — Owned menu collection.
19. **Menu create** — Exact backend fields.
20. **Menu update** — UUID-owned item.
21. **Availability** — Existing PATCH operation.
22. **Availability reason** — Null in this mobile quick action.
23. **Food types** — VEG, NON_VEG and EGG.
24. **Statuses** — DRAFT, ACTIVE and INACTIVE.
25. **Price** — Backend persisted.
26. **Currency** — Backend persisted.
27. **Preparation time** — Optional bounded input.
28. **Package weight** — Required positive input.
29. **Thermobox** — Exact backend boolean.
30. **Images** — Not included in mobile DTO.
31. **Image upload** — Deferred.
32. **Native picker** — CI blocks unreviewed picker usage.
33. **Identity ID** — Excluded.
34. **Kitchen ID** — Excluded from menu DTO.
35. **Blob metadata** — Excluded.
36. **Token logging** — Blocked.
37. **AsyncStorage** — Not used.
38. **401 handling** — Signs out.
39. **403 handling** — Approved chef message.
40. **Timeout** — Existing network timeout.
41. **CI** — `azure-pipelines-chef-mobile-kitchen-menu-ci.yml`.
42. **Tests** — Kitchen privacy, menu validation and arrays.
43. **Dependencies** — No new native dependency.
44. **APIM** — Reuses web-prepared kitchen/menu operations.
45. **Azure** — No write performed.
46. **Business rules** — No pricing/serviceability rule invented.
47. **Rollback** — Restore ChefKitchen placeholder and remove editor/client files.
48. **Next module** — Native chef order workflow.
49. **Manual later** — CI, native shell build and owned Catalog smoke tests.
50. **Acceptance** — Create/update/list/availability pass with ownership and privacy enforced.
