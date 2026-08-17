# Craves Subscription and Backoffice Ten-Module Consolidated Handover

Date: 2026-07-30  
Repository: `rmorampudi09-arch/Craves-Build-platform`  
Status: Code prepared on stacked draft branches. No CI, deployment, APIM write, Blob read, subscription mutation or administrator decision has been executed.

## 1. Document purpose
This handover records the ten-module subscription and backoffice batch, exact code paths, security boundaries, pipelines, rollout order, rollback and pending business decisions.

## 2. Locked technology stack
Spring Boot 3/Java 21/Maven, PostgreSQL, Next.js/TypeScript/Tailwind, React Native/TypeScript, Firebase Authentication, Azure Container Apps and Azure API Management.

## 3. Branch discipline
Every module is isolated on a stacked feature branch and opened as a draft pull request. No commit was made directly to `main`.

## 4. Runtime discipline
No pipeline or runtime mutation has been performed from this batch.

## 5. Business-rule discipline
No subscription benefit, renewal, unused-meal, holiday, refund, credit, payout, commission or delivery rule was invented.

## 6. Security baseline
Backend services remain authoritative for roles, ownership, state transitions and durable audit records.

## 7. Module 1
Subscription Service ownership, privacy and audit hardening.

## 8. Module 1 branch
`feature/subscription-ownership-hardening`.

## 9. Module 1 PR
Draft PR #58.

## 10. Module 1 issue closed
A CHEF could previously submit another chef identity when creating a plan.

## 11. Module 1 listing fix
CHEF plan listing now returns only plans owned by the authenticated chef.

## 12. Module 1 mutation fix
CHEF plan status changes require the plan owner to match the authenticated chef.

## 13. Module 1 public privacy
`PublicPlanResponse` excludes internal chef identity and internal timestamps.

## 14. Module 1 customer privacy
`CustomerSubscriptionResponse` excludes customer and chef identity UUIDs.

## 15. Module 1 active-plan rule
Public plan reads return ACTIVE plans only.

## 16. Module 1 audit
Flyway V2 creates `subscription_schema.subscription_plan_audit`.

## 17. Module 1 audit events
Plan creation and status changes record actor, action, old state, new state and time.

## 18. Module 1 CI
`azure-pipelines-subscription-ownership-ci.yml`.

## 19. Module 1 service files
`SubscriptionRepository.java`, `SubscriptionService.java`, `SubscriptionController.java` and `ApiDtos.java`.

## 20. Module 1 migration file
`services/subscription-service/src/main/resources/db/migration/V2__subscription_plan_audit.sql`.

## 21. Module 2
Next.js customer active-plan discovery.

## 22. Module 2 branch
`feature/customer-web-subscription-plans`.

## 23. Module 2 PR
Draft PR #59.

## 24. Module 2 route
`/subscriptions/plans`.

## 25. Module 2 BFF
`GET /api/subscriptions/plans`.

## 26. Module 2 API
`GET /api/v1/subscriptions/plans`.

## 27. Module 2 authentication
Plan browsing is public; no customer session is required.

## 28. Module 2 privacy
Chef identity is rejected by the browser DTO.

## 29. Module 2 pricing
The page formats only backend amount and currency.

## 30. Module 2 CI
`azure-pipelines-customer-web-subscription-plans-ci.yml`.

## 31. Module 3
Next.js customer enrollment and subscription lifecycle.

## 32. Module 3 branch
`feature/customer-web-subscription-management`.

## 33. Module 3 PR
Draft PR #60.

## 34. Module 3 routes
`/subscriptions`, `/subscriptions/new` and `/subscriptions/{subscriptionId}`.

## 35. Module 3 enrollment payload
Plan ID, non-past start date, customer-owned saved address ID and optional notes.

## 36. Module 3 lifecycle
Only pause and cancel are exposed.

## 37. Module 3 session
Uses the HTTP-only Craves access cookie through server BFF routes.

## 38. Module 3 mutation protection
Create, pause and cancel require same-origin requests.

## 39. Module 3 identity protection
Customer and chef identity fields are absent from browser responses.

## 40. Module 3 CI
`azure-pipelines-customer-web-subscription-management-ci.yml`.

## 41. Module 4
React Native active-plan discovery.

## 42. Module 4 branch
`feature/customer-mobile-subscription-plans`.

## 43. Module 4 PR
Draft PR #61.

## 44. Module 4 screen
`SubscriptionPlans`.

## 45. Module 4 refresh
Pull-to-refresh reloads active plans.

## 46. Module 4 placeholder
A non-mutating enrollment placeholder keeps the parent PR independently runnable.

## 47. Module 4 storage
No plan data is persisted in AsyncStorage or secure session storage.

## 48. Module 4 CI
`azure-pipelines-customer-mobile-subscription-plans-ci.yml`.

## 49. Module 5
React Native subscription enrollment and lifecycle.

## 50. Module 5 branch
`feature/customer-mobile-subscription-management`.

## 51. Module 5 PR
Draft PR #62.

## 52. Module 5 screens
`SubscriptionEnrollment` and `Subscriptions`.

## 53. Module 5 address integration
Enrollment uses the existing customer-owned saved-address API.

## 54. Module 5 date input
The accepted format is `YYYY-MM-DD`; the date cannot be in the past.

## 55. Module 5 secure session
Owned operations use the Keychain/Keystore-backed Craves access session.

## 56. Module 5 session expiry
HTTP 401 triggers the existing secure sign-out flow.

## 57. Module 5 lifecycle
Only pause and cancel are shown.

## 58. Module 5 CI
`azure-pipelines-customer-mobile-subscription-management-ci.yml`.

## 59. Module 6
Secure Next.js administrator shell.

## 60. Module 6 branch
`feature/admin-web-shell`.

## 61. Module 6 PR
Draft PR #63.

## 62. Module 6 route
`/admin`.

## 63. Module 6 BFF
`GET /api/admin/me`.

## 64. Module 6 role rule
Only an ACTIVE identity with backend ADMIN role receives the admin shell.

## 65. Module 6 role boundary
The client cannot grant, revoke or persist roles.

## 66. Module 6 privacy
The reduced BFF excludes identity UUID, phone, role list and access token.

## 67. Module 6 CI
`azure-pipelines-admin-web-shell-ci.yml`.

## 68. Module 7
Admin chef review and secure KYC proof streaming.

## 69. Module 7 branch
`feature/admin-chef-review`.

## 70. Module 7 PR
Draft PR #64.

## 71. Module 7 backend gap
The previous backoffice API exposed proof metadata but no secure content stream.

## 72. Module 7 new endpoint
`GET /api/v1/backoffice/chef-reviews/{applicationId}/documents/{documentId}/content`.

## 73. Module 7 backend authorization
`ChefDocumentReviewService` requires ADMIN role.

## 74. Module 7 document ownership
The database lookup requires both application ID and document ID.

## 75. Module 7 storage restriction
Only the configured private documents container and `kyc/` prefix are accepted.

## 76. Module 7 file restrictions
PDF, JPEG and PNG under the configured KYC size limit.

## 77. Module 7 browser protection
The BFF verifies MIME type and length, sets CSP sandbox, `nosniff` and no-store.

## 78. Module 7 decisions
Approval requires confirmation; rejection requires a reason.

## 79. Module 7 backend authority
User-Chef Service owns state validation, CHEF role grant, notifications and admin decision audit.

## 80. Module 7 CI
`azure-pipelines-admin-chef-review-ci.yml`.

## 81. Module 8
Admin subscription plan management.

## 82. Module 8 branch
`feature/admin-subscription-plans`.

## 83. Module 8 PR
Draft PR #65.

## 84. Module 8 route
`/admin/subscription-plans`.

## 85. Module 8 approved-chef selector
Only APPROVED backoffice chef applications can supply a chef owner identity.

## 86. Module 8 plan fields
Plan code, optional owner, name, description, WEEKLY/MONTHLY period, non-negative amount and currency.

## 87. Module 8 status fields
DRAFT, ACTIVE and INACTIVE only.

## 88. Module 8 pricing boundary
All amounts are explicit operator inputs; no recommendation or formula exists.

## 89. Module 8 audit boundary
Subscription Service V2 records create and status audit events.

## 90. Module 8 CI
`azure-pipelines-admin-subscription-plans-ci.yml`.

## 91. Module 9
Controlled admin subscription status operations.

## 92. Module 9 branch and PR
`feature/admin-subscription-operations`, draft PR #66.

## 93. Module 9 lookup
Exact subscription UUID lookup only; no unsupported admin list/search API was invented.

## 94. Module 9 status set
PENDING_PAYMENT, ACTIVE, PAUSED, PAYMENT_FAILED, EXPIRED and CANCELLED.

## 95. Module 9 reason
Every administrative status mutation requires a non-empty operational reason.

## 96. Module 9 exclusions
No refund, credit, payout, renewal, meal-generation or delivery operation.

## 97. Module 9 CI
`azure-pipelines-admin-subscription-operations-ci.yml`.

## 98. Module 10
Guarded subscription and backoffice APIM package plus consolidated runbook.

## 99. Module 10 branch
`feature/subscription-backoffice-apim-runbook`.

## 100. Module 10 APIM paths
`api/v1/subscriptions`, `api/v1/admin/subscription-plans`, `api/v1/admin/subscriptions` and `api/v1/backoffice/chef-reviews`.

## 101. Public APIM operations
Active plan list and active plan detail do not require Bearer authentication.

## 102. Authenticated APIM operations
All customer mutation/read, admin and backoffice operations require a Bearer header.

## 103. Cryptographic validation
APIM performs the header guard; owning Spring services validate the JWT and role.

## 104. Readiness checks
The configure script requires latest revision equals latest-ready revision, Running state and healthy actuator response.

## 105. Path ownership
Configuration fails if multiple APIM APIs own a target path.

## 106. Subscription-key safety
Configuration refuses to relax an existing subscription-key requirement.

## 107. Inherited backend safety
Configuration fails if a global/API inherited `backend-id` policy would block safe base-url override.

## 108. Write confirmation
`CONFIRM_APIM_WRITE` defaults to false.

## 109. Rollback confirmation
`CONFIRM_APIM_ROLLBACK` defaults to false.

## 110. Rollback scope
Rollback deletes only named operations and retains API containers.

## 111. APIM CI
`azure-pipelines-subscription-backoffice-apim-ci.yml`.

## 112. APIM write pipeline
`azure-pipelines-subscription-backoffice-apim.yml`.

## 113. APIM status pipeline
`azure-pipelines-subscription-backoffice-apim-status.yml`.

## 114. APIM rollback pipeline
`azure-pipelines-subscription-backoffice-apim-rollback.yml`.

## 115. Service deployment order
Deploy User-Chef secure proof streaming, then Subscription Service ownership/audit hardening.

## 116. CI order
Run PRs #58 through the final APIM PR independently against each exact branch head.

## 117. Merge order
Merge #58, #59, #60, #61, #62, #63, #64, #65, #66 and the final APIM PR in strict stack order.

## 118. APIM rollout order
Run APIM CI, explicit write, read-only status, then authenticated and unauthenticated smoke tests.

## 119. Web deployment
Only after backend/APIM gates pass, build the cumulative Next.js branch and run the previously prepared guarded web deployment.

## 120. Mobile build
Only after native shell/Firebase/signing setup, run cumulative Android and iOS builds and smoke tests.

## 121. Firebase manual work
No change in this batch; existing Phone Auth, domains and native app configuration remain prerequisites.

## 122. Azure DevOps manual work
Register each YAML pipeline and use `Craves-Dev-Service-Connection` through `AZURE_SERVICE_CONNECTION`.

## 123. Blob manual work
Confirm the existing private documents container and managed storage secret are available to User-Chef Service.

## 124. Security smoke tests
Use ADMIN and non-admin identities; test document mismatch, unsupported type, oversized content and expired session.

## 125. Subscription ownership smoke
Use two chef identities and two customer identities to prove cross-owner access is denied.

## 126. Plan audit smoke
Create a synthetic draft and change status, then verify plan audit rows.

## 127. Subscription history smoke
Apply a synthetic admin status update with a reason and verify status history.

## 128. Public plan smoke
Unauthenticated active plan list/detail succeeds; inactive/draft plan detail is not public.

## 129. Customer web smoke
Plan browse, enrollment with owned address, list/detail, pause and cancel.

## 130. Customer mobile smoke
Plan browse, enrollment, list and pause/cancel using the secure device session.

## 131. Admin chef smoke
List pending, inspect each proof, approve one synthetic application and reject another with a reason.

## 132. Admin plan smoke
Approved chef selector, draft create and allowed status changes.

## 133. Admin subscription smoke
Exact-ID lookup and reasoned status update.

## 134. Rollback services
Restore exact previous User-Chef and Subscription Service images.

## 135. Rollback APIM
Run operation-scoped rollback only after confirming the gateway defect.

## 136. Rollback web/mobile
Restore the previous web image or mobile source/release; retain audit and business records.

## 137. Data retention
Do not delete KYC, subscription, plan-audit or status-history rows during rollback.

## 138. Billable resources
This batch does not provision a new Azure resource; APIM and Container Apps are existing resources.

## 139. Secrets
No credential, SAS token, connection string, Firebase private key or Cashfree secret is committed.

## 140. Delivery safety
No delivery command, webhook, tracking, status publisher or Borzo flag is enabled.

## 141. Payment safety
No Cashfree call or subscription payment integration is introduced by this batch.

## 142. Deferred subscription payment
Payment intent, webhook correlation and activation after verified payment remain a separate integration module.

## 143. Deferred subscription order generation
Recurring order generation must reuse Order Service and requires schedule/cutoff/product decisions.

## 144. Deferred renewal
Automatic/manual renewal policy is not defined.

## 145. Deferred pause/resume
Customer pause exists; resume behavior and eligibility are not defined.

## 146. Deferred cancellation economics
Refund, credit and unused-meal handling are not defined.

## 147. Deferred chef payout
Subscription settlement and payout rules remain Finance/Product decisions.

## 148. Deferred holidays
Holiday skips, rescheduling and chef closure rules are not defined.

## 149. Deferred serviceability
No delivery radius or serviceability rule is introduced.

## 150. Final acceptance
The batch is accepted only after independent CI, strict merges, Flyway V2, service deployments, APIM verification, web/mobile builds, ownership/privacy tests, KYC proof tests, audited admin decisions and all safety flags remaining approved.
