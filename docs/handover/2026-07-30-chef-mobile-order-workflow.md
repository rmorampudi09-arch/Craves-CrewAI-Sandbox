# Craves Mobile Chef Order Workflow — Engineering Handover

1. **Date** — 2026-07-30.
2. **Branch** — `feature/chef-mobile-order-workflow`.
3. **Parent** — `feature/chef-mobile-kitchen-menu`.
4. **Status** — Code only.
5. **Purpose** — Add native chef order fulfillment workflow.
6. **Inbox screen** — Replaces ChefOrders placeholder.
7. **Detail screen** — Typed order UUID route.
8. **Session** — Device-secure Craves session.
9. **Role authority** — Order Service validates CHEF.
10. **Ownership** — Order Service validates kitchen ownership.
11. **List route** — `/api/v1/chef/orders`.
12. **Detail route** — Owned order UUID.
13. **Accept route** — Existing backend action.
14. **Reject route** — Existing backend action.
15. **Ready route** — Existing backend action.
16. **Unknown actions** — Not exposed.
17. **Customer identity** — Excluded.
18. **Checkout ID** — Excluded.
19. **Kitchen ID** — Excluded.
20. **Pickup snapshot** — Excluded.
21. **Recipient contact** — Retained for fulfillment.
22. **Delivery address** — Retained for fulfillment.
23. **Order items** — Name, quantity and line total.
24. **Food subtotal** — Backend value.
25. **Grand total** — Backend value.
26. **Frontend fee arithmetic** — None.
27. **Supported statuses** — Exact Order Service enum.
28. **Decision action** — CHEF_ACCEPTANCE_PENDING only.
29. **Ready action** — CHEF_ACCEPTED or PREPARING only.
30. **Terminal/other actions** — None.
31. **Prep time** — Sent to backend for acceptance.
32. **Chef note** — Optional and bounded by UI/backend.
33. **Reject reason** — Optional and bounded by UI/backend.
34. **Correlation ID** — Order UUID.
35. **Accept idempotency** — Order/action/prep deterministic key.
36. **Reject idempotency** — Order/reject deterministic key.
37. **Backend idempotency** — Remains authoritative.
38. **Conflict handling** — Safe refresh instruction.
39. **401 handling** — Signs out.
40. **403/404 handling** — Safe unowned/not-found copy.
41. **Timeout** — Existing network timeout.
42. **CI** — `azure-pipelines-chef-mobile-order-workflow-ci.yml`.
43. **Tests** — Privacy, statuses, action mapping and arrays.
44. **CI scans** — Internal fields, unsupported transitions and token logging.
45. **Dependencies** — No new native dependency.
46. **Runtime actions** — None during development.
47. **Rollback** — Restore placeholder and remove order client/screens.
48. **Consolidated handover** — Next ten chef modules runbook.
49. **Manual later** — Exact-head CI, APIM and controlled chef-order smoke tests.
50. **Acceptance** — Owned list/detail/accept/duplicate/reject/ready tests pass with all unsupported transitions blocked.
