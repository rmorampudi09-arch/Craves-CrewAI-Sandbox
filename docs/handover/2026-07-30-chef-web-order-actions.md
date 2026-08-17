# Craves Chef Web Order Actions — Engineering Handover

1. **Date** — 2026-07-30.
2. **Branch** — `feature/chef-web-order-actions`.
3. **Parent** — `feature/chef-web-order-inbox`.
4. **Status** — Code only.
5. **Purpose** — Add supported chef order transitions.
6. **Accept route** — POST by owned order UUID.
7. **Reject route** — POST by owned order UUID.
8. **Ready route** — POST by owned order UUID.
9. **Upstream base** — `/api/v1/chef/orders`.
10. **Auth** — HTTP-only Craves session.
11. **Role authority** — Order Service validates CHEF.
12. **Ownership** — Order Service validates kitchen ownership.
13. **State authority** — Order Service locks and validates the current state.
14. **Acceptance deadline** — Enforced only by Order Service/database time.
15. **Accept input** — Positive prep time and optional note.
16. **Prep lower bound** — One minute.
17. **Prep upper bound** — 1440 minutes client/BFF ceiling.
18. **Note** — Optional and bounded to 500 characters.
19. **Reject input** — Optional bounded reason.
20. **Ready input** — No invented body fields.
21. **Correlation ID** — UUID generated per browser action.
22. **Idempotency key** — Same UUID for accept/reject.
23. **Header forwarding** — Both headers sent unchanged to Order Service.
24. **Backend idempotency** — Order Service remains authoritative.
25. **Same-origin** — Required for every mutation.
26. **Accept UI** — Only for CHEF_ACCEPTANCE_PENDING.
27. **Reject UI** — Only for CHEF_ACCEPTANCE_PENDING.
28. **Ready UI** — Only for CHEF_ACCEPTED or PREPARING.
29. **Other statuses** — No action offered.
30. **Cancel** — Not implemented.
31. **Refund** — Not implemented.
32. **Delivery** — Not implemented.
33. **Provider calls** — None.
34. **Payment calls** — None.
35. **Conflict response** — Safe refresh instruction.
36. **Timeout** — Twelve seconds.
37. **401 handling** — Session cleared.
38. **Response** — Parsed through the chef order allow-list.
39. **APIM path** — Existing `api/v1/chef/orders` API.
40. **APIM prerequisite** — Chef order read APIM first.
41. **APIM operations** — Accept, reject and ready POST.
42. **CI** — `azure-pipelines-chef-web-order-actions-ci.yml`.
43. **APIM pipeline** — `azure-pipelines-chef-order-actions-apim.yml`.
44. **Confirmation** — Defaults to false.
45. **Static gates** — Idempotency headers and unsupported-transition scan.
46. **Secrets** — None committed.
47. **Runtime actions** — None during development.
48. **Rollback** — Remove only three action operations and action component/routes.
49. **Next module** — React Native chef mode shell.
50. **Acceptance** — Exact-head CI and controlled accept/duplicate/reject/ready conflict tests pass.
