# Craves Next Ten Chef Modules — Consolidated Engineering Handover

1. **Date** — 2026-07-30.
2. **Repository** — `rmorampudi09-arch/Craves-Build-platform`.
3. **Batch status** — Code prepared on stacked feature branches.
4. **Runtime status** — No pipeline, Azure write, APIM write or provider action performed.
5. **Stack** — Next.js/TypeScript/Tailwind, React Native/TypeScript, Spring Boot services and Azure APIM.
6. **Authentication** — Existing Firebase-to-Craves secure sessions.
7. **Authorization** — Backend roles and ownership remain authoritative.
8. **Business-rule discipline** — No pricing, commission, radius, FSSAI or approval rule invented.
9. **Provider discipline** — No delivery provider or Cashfree operation enabled.
10. **Branch discipline** — Every module is a stacked draft PR.
11. **Module 1 branch** — `feature/chef-web-mode-shell`.
12. **Module 1 PR** — Draft PR #46.
13. **Module 1 route** — `/chef`.
14. **Module 1 API** — Safe `/auth/me` role projection.
15. **Module 1 role rule** — CHEF is derived from backend roles only.
16. **Module 1 non-chef rule** — Application/status access only.
17. **Module 1 CI** — `azure-pipelines-chef-web-mode-ci.yml`.
18. **Module 1 APIM** — No new operation; existing Auth route reused.
19. **Module 2 branch** — `feature/chef-web-application`.
20. **Module 2 PR** — Draft PR #47.
21. **Module 2 page** — `/chef/application`.
22. **Module 2 APIs** — Read, submit and proof-file upload.
23. **Module 2 proof types** — Aadhaar and PAN only, matching backend enum.
24. **Module 2 file rules** — PDF/JPEG/PNG under 10 MB.
25. **Module 2 privacy** — Blob paths, reviewer identity and registered phone removed.
26. **Module 2 APIM** — `azure-pipelines-chef-application-apim.yml`.
27. **Module 2 CI** — `azure-pipelines-chef-web-application-ci.yml`.
28. **Module 3 branch** — `feature/chef-web-kitchen-profile`.
29. **Module 3 PR** — Draft PR #48.
30. **Module 3 page** — `/chef/kitchen`.
31. **Module 3 backend** — `GET|PUT /api/v1/kitchens/me`.
32. **Module 3 suspension** — SUSPENDED is read-only.
33. **Module 3 location** — Optional paired coordinates; no geocoding.
34. **Module 3 APIM** — `azure-pipelines-chef-kitchen-profile-apim.yml`.
35. **Module 3 CI** — `azure-pipelines-chef-web-kitchen-profile-ci.yml`.
36. **Module 4 branch** — `feature/chef-web-menu-management`.
37. **Module 4 PR** — Draft PR #49.
38. **Module 4 page** — `/chef/menu`.
39. **Module 4 operations** — List, create and update menu item.
40. **Module 4 enums** — Exact food type, status and spice values.
41. **Module 4 pricing** — Catalog persists price/currency; no fee arithmetic.
42. **Module 4 privacy** — Kitchen and blob storage fields removed.
43. **Module 4 APIM** — `azure-pipelines-chef-menu-apim.yml`.
44. **Module 4 CI** — `azure-pipelines-chef-web-menu-ci.yml`.
45. **Module 5 branch** — `feature/chef-web-menu-media-availability`.
46. **Module 5 PR** — Draft PR #50.
47. **Module 5 page** — `/chef/menu/media`.
48. **Module 5 availability** — Boolean plus optional bounded reason.
49. **Module 5 image rules** — JPEG/PNG/WebP under 10 MB.
50. **Module 5 storage rule** — Browser never receives Blob credentials or paths.
51. **Module 5 APIM** — `azure-pipelines-chef-menu-media-apim.yml`.
52. **Module 5 CI** — `azure-pipelines-chef-web-menu-media-ci.yml`.
53. **Module 6 branch** — `feature/chef-web-order-inbox`.
54. **Module 6 PR** — Draft PR #51.
55. **Module 6 pages** — Chef order list and detail.
56. **Module 6 APIs** — Owned read operations only.
57. **Module 6 privacy** — Customer identity, checkout, kitchen and pickup fields removed.
58. **Module 6 fulfillment** — Recipient contact/address retained from owned order contract.
59. **Module 6 APIM** — `azure-pipelines-chef-order-read-apim.yml`.
60. **Module 6 CI** — `azure-pipelines-chef-web-order-inbox-ci.yml`.
61. **Module 7 branch** — `feature/chef-web-order-actions`.
62. **Module 7 PR** — Draft PR #52.
63. **Module 7 actions** — Accept, reject and ready-for-pickup only.
64. **Module 7 idempotency** — UUID correlation/idempotency headers for decisions.
65. **Module 7 deadline** — Order Service/database remains authoritative.
66. **Module 7 unsupported actions** — Cancel, refund and provider transitions absent.
67. **Module 7 APIM** — `azure-pipelines-chef-order-actions-apim.yml`.
68. **Module 7 CI** — `azure-pipelines-chef-web-order-actions-ci.yml`.
69. **Module 8 branch** — `feature/chef-mobile-mode-shell`.
70. **Module 8 PR** — Draft PR #53.
71. **Module 8 entry** — Customer home to Chef Mode.
72. **Module 8 approved path** — Kitchen/menu and chef-order destinations.
73. **Module 8 non-approved path** — Reduced application status only.
74. **Module 8 storage** — Existing Keychain/Keystore session.
75. **Module 8 privacy** — No file names, blob paths, contents or identity fields.
76. **Module 8 CI** — `azure-pipelines-chef-mobile-mode-ci.yml`.
77. **Module 9 branch** — `feature/chef-mobile-kitchen-menu`.
78. **Module 9 PR** — Draft PR #54.
79. **Module 9 operations** — Kitchen profile, menu list/edit and availability.
80. **Module 9 no-kitchen state** — Backend 404 maps to profile creation.
81. **Module 9 suspension** — Read-only.
82. **Module 9 image decision** — Native picker/upload deferred.
83. **Module 9 CI** — `azure-pipelines-chef-mobile-kitchen-menu-ci.yml`.
84. **Module 10 branch** — `feature/chef-mobile-order-workflow`.
85. **Module 10 PR** — Final stacked draft PR #55.
86. **Module 10 operations** — List, detail, accept, reject and ready.
87. **Module 10 correlation** — Order UUID used as valid correlation UUID.
88. **Module 10 idempotency** — Deterministic per-order action keys.
89. **Module 10 privacy** — Internal ownership/pickup fields removed.
90. **Module 10 CI** — `azure-pipelines-chef-mobile-order-workflow-ci.yml`.
91. **Required merge order** — Existing stack through PR #43, then PR #46 through #55 in order.
92. **Independent CI rule** — Each PR CI runs against its exact head.
93. **APIM order** — Chef application, kitchen profile, menu, menu media, order reads, then order actions.
94. **APIM confirmation** — Every write pipeline defaults to false.
95. **Service connection** — `Craves-Dev-Service-Connection` through `AZURE_SERVICE_CONNECTION`.
96. **Web deployment** — Only after lockfile/npm-ci amendment and all APIM/auth gates.
97. **Native build** — Only after shell generation, Firebase files, pods/Gradle and signing review.
98. **Controlled tests** — Use test identities, exact owned records and sandbox/non-production state.
99. **Rollback** — Remove named APIM operations, restore prior web image and revert exact feature modules.
100. **Final acceptance** — All independent CI, APIM verification, role/ownership smokes, kitchen/menu operations and chef-order duplicate/conflict tests pass without enabling provider or commercial rules.
