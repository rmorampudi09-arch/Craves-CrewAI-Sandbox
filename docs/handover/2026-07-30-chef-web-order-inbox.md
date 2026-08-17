# Craves Chef Web Order Inbox — Engineering Handover

1. **Date** — 2026-07-30.
2. **Branch** — `feature/chef-web-order-inbox`.
3. **Parent** — `feature/chef-web-menu-media-availability`.
4. **Status** — Code only.
5. **Purpose** — Read chef-owned orders.
6. **List page** — `/chef/orders`.
7. **Detail page** — `/chef/orders/{orderId}`.
8. **List BFF** — `GET /api/chef/orders`.
9. **Detail BFF** — `GET /api/chef/orders/{orderId}`.
10. **Upstream** — `/api/v1/chef/orders`.
11. **Auth** — HTTP-only Craves session.
12. **Role authority** — Order Service validates CHEF.
13. **Ownership** — Order Service validates kitchen ownership.
14. **Order UUID** — Validated before upstream call.
15. **Supported statuses** — Exact Order Service enum.
16. **Unknown statuses** — Rejected.
17. **Customer identity ID** — Removed.
18. **Checkout ID** — Removed.
19. **Kitchen ID** — Removed.
20. **Pickup snapshot** — Removed.
21. **Recipient name** — Retained for fulfillment.
22. **Recipient phone** — Retained for fulfillment.
23. **Delivery address** — Retained for fulfillment.
24. **Coordinates** — Not exposed by the web DTO.
25. **Items** — IDs, names, categories, food type, price, quantity and line total.
26. **Amounts** — Backend food subtotal, fees, tax and grand total.
27. **Frontend arithmetic** — None.
28. **Chef note** — Read-only in this module.
29. **Prep time** — Read-only in this module.
30. **Workflow actions** — Deferred to child module.
31. **Caching** — No-store.
32. **Timeout** — Ten seconds.
33. **401 handling** — Session cleared.
34. **403/404 handling** — Safe unowned/not-found response.
35. **Invalid response** — HTTP 502.
36. **UI** — Refreshable inbox and detail cards.
37. **APIM path** — `api/v1/chef/orders`.
38. **APIM owner rule** — Zero or one API path owner.
39. **APIM operations** — List and detail GET.
40. **APIM policy** — Bearer guard and no-store.
41. **CI** — `azure-pipelines-chef-web-order-inbox-ci.yml`.
42. **APIM pipeline** — `azure-pipelines-chef-order-read-apim.yml`.
43. **Confirmation** — Defaults to false.
44. **Tests** — Privacy, status and UUID guards.
45. **Secrets** — None committed.
46. **Runtime writes** — None during development.
47. **Rollback** — Remove only read operations and frontend files.
48. **Next module** — Chef accept/reject/ready actions.
49. **Manual later** — CI, APIM and owned/unowned order smoke tests.
50. **Acceptance** — Owned orders render correctly and unowned/internal fields remain inaccessible.
