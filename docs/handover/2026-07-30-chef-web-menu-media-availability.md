# Craves Chef Web Menu Media and Availability — Engineering Handover

1. **Date** — 2026-07-30.
2. **Branch** — `feature/chef-web-menu-media-availability`.
3. **Parent** — `feature/chef-web-menu-management`.
4. **Status** — Code only.
5. **Purpose** — Manage dish availability and images.
6. **Page** — `/chef/menu/media`.
7. **Availability BFF** — PATCH operation by menu UUID.
8. **Image BFF** — POST multipart operation by menu UUID.
9. **Upstream availability** — Catalog availability endpoint.
10. **Upstream image** — Catalog image endpoint.
11. **Auth** — HTTP-only Craves session.
12. **Role authority** — Catalog validates CHEF.
13. **Ownership** — Catalog validates kitchen/menu ownership.
14. **Menu UUID** — Validated before upstream call.
15. **Availability value** — Boolean only.
16. **Reason** — Optional and bounded to 500 characters.
17. **Pricing** — Not changed.
18. **Menu status** — Not changed by the availability route.
19. **Order status** — Not changed.
20. **Allowed image types** — JPEG, PNG and WebP.
21. **Image size** — Maximum 10 MB.
22. **Multipart** — Rebuilt server-side by the BFF.
23. **Primary flag** — Exact boolean query parameter.
24. **Upload response** — Browser receives only upload success.
25. **Storage credentials** — Never exposed.
26. **Blob container** — Never exposed.
27. **Blob name** — Never exposed.
28. **Public URL** — Read through the menu contract and HTTPS only.
29. **Mutation protection** — Same-origin required.
30. **Caching** — No-store.
31. **Availability timeout** — Ten seconds.
32. **Image timeout** — Thirty seconds.
33. **401 handling** — Session cleared.
34. **404 handling** — Safe item-not-found error.
35. **Invalid files** — Rejected before upstream upload.
36. **UI** — Dish selector, image gallery and actions.
37. **APIM path** — Existing `api/v1/kitchens/me`.
38. **Prerequisite** — Kitchen and menu APIM modules.
39. **APIM operations** — Availability PATCH and image POST.
40. **APIM policy** — Existing chef bearer guard.
41. **CI** — `azure-pipelines-chef-web-menu-media-ci.yml`.
42. **APIM pipeline** — `azure-pipelines-chef-menu-media-apim.yml`.
43. **Confirmation** — Defaults to false.
44. **Static gates** — Blocks private storage fields and debug logging.
45. **Secrets** — None committed.
46. **Azure writes** — None during development.
47. **Rollback** — Remove only the two named operations and frontend files.
48. **Next module** — Chef order inbox and details.
49. **Manual later** — Run CI, APIM and controlled image/availability smoke tests.
50. **Acceptance** — Owned item updates succeed; invalid IDs/files and unowned items fail safely.
