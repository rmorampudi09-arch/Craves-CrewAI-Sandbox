# Craves Mobile Chef Mode Shell — Engineering Handover

1. **Date** — 2026-07-30.
2. **Branch** — `feature/chef-mobile-mode-shell`.
3. **Parent** — `feature/chef-web-order-actions`.
4. **Status** — Code only.
5. **Purpose** — Add secure role-aware chef navigation.
6. **Application** — Existing React Native customer app.
7. **Session source** — Device-secure Craves session.
8. **Storage** — Android Keystore/iOS Keychain path from parent.
9. **Role source** — Backend identity roles.
10. **Chef derivation** — Exact normalized CHEF membership.
11. **Role mutation** — None.
12. **Role persistence** — Only existing validated session identity.
13. **Customer coexistence** — Customer Mode remains the default home.
14. **Chef entry** — New Open Chef Mode button.
15. **Chef dashboard** — `ChefModeScreen`.
16. **Approved behavior** — Kitchen/menu and chef orders destinations.
17. **Non-approved behavior** — Application-status destination only.
18. **Application API** — `/api/v1/chef/application` through APIM.
19. **Application response** — Reduced summary only.
20. **Allowed statuses** — NOT_SUBMITTED, PENDING, APPROVED, REJECTED.
21. **Review reason** — Bounded public text.
22. **Submission time** — Optional validated instant.
23. **Review time** — Optional validated instant.
24. **Proof information** — Document type names only.
25. **Proof contents** — Not downloaded.
26. **Original filenames** — Not stored in the mobile summary.
27. **Blob paths** — Excluded.
28. **Identity ID** — Excluded from application summary.
29. **Registered phone** — Excluded from application summary.
30. **Token visibility** — Never rendered or logged.
31. **AsyncStorage** — Not used for session or chef state.
32. **401 handling** — Signs out through AuthProvider.
33. **Timeout** — Existing mobile network timeout.
34. **Offline handling** — Public retry copy.
35. **Typed routes** — ChefMode, ChefApplicationStatus, ChefKitchen and ChefOrders.
36. **Independent build** — Child destinations use safe placeholder screen.
37. **Module 9 behavior** — Replaces ChefKitchen placeholder.
38. **Module 10 behavior** — Replaces ChefOrders placeholder.
39. **CI** — `azure-pipelines-chef-mobile-mode-ci.yml`.
40. **CI tests** — Role derivation and application privacy.
41. **CI scans** — Blocks token/private fields, logs and insecure storage.
42. **Dependencies** — No new native dependency.
43. **Firebase** — No Console change.
44. **Azure** — No write performed.
45. **APIM** — Reuses chef application route prepared by web module.
46. **Business rules** — No approval or compliance rule invented.
47. **Rollback** — Remove chef routes/screens and customer-home button.
48. **Next module** — Native kitchen and menu management.
49. **Manual later** — Exact-head CI after parent stack is validated.
50. **Acceptance** — Customer-only and CHEF identities route correctly without client-side authority escalation.
