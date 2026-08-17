# Craves Subscription Ownership Hardening

Date: 2026-07-30  
Status: Code complete on feature branch; no pipeline or deployment executed.

## 1. Purpose
Prevent cross-chef subscription-plan management and remove internal identity fields from customer/public responses.

## 2. Service
`services/subscription-service`.

## 3. Branch
`feature/subscription-ownership-hardening`.

## 4. Base
`feature/chef-mobile-order-workflow`.

## 5. Runtime impact
None until the service pipeline is run later.

## 6. Existing issue
A CHEF request could supply another `chefIdentityId` during plan creation.

## 7. Listing issue
CHEF users previously received all plans through the admin plan list endpoint.

## 8. Status issue
CHEF users could attempt status changes on plans they did not own.

## 9. Public-plan issue
The public plan DTO exposed the internal chef identity UUID.

## 10. Customer-subscription issue
Customer responses exposed customer and chef identity UUIDs.

## 11. Create-plan rule
For CHEF actors, the plan owner is always the authenticated identity.

## 12. Admin create rule
ADMIN may continue supplying the owner where operationally required.

## 13. Chef listing rule
CHEF receives only plans where `chef_identity_id` equals the authenticated identity.

## 14. Admin listing rule
ADMIN continues to receive all plans.

## 15. Chef status rule
Status changes use an ownership-constrained update.

## 16. Admin status rule
ADMIN retains controlled status management.

## 17. Public plan visibility
Only ACTIVE plans are returned through public plan reads.

## 18. Public plan DTO
`PublicPlanResponse` excludes `chefIdentityId`, status timestamps and internal ownership data.

## 19. Customer subscription DTO
`CustomerSubscriptionResponse` excludes customer and chef identity UUIDs.

## 20. Internal admin DTO
`SubscriptionResponse` remains available only for admin operations.

## 21. Billing periods
No change: `WEEKLY` and `MONTHLY` only.

## 22. Plan statuses
No change: `DRAFT`, `ACTIVE`, `INACTIVE`.

## 23. Customer lifecycle
No change: customers may pause or cancel only.

## 24. Admin lifecycle
No new status or transition was introduced.

## 25. Pricing
No subscription price, benefit or discount rule was invented.

## 26. Renewal
Automatic renewal remains outside this module.

## 27. Meal generation
Scheduled meal-order generation remains pending product rules.

## 28. Refunds
Subscription refund policy remains pending Product and Finance.

## 29. Delivery
No delivery service or provider flag is changed.

## 30. Audit table
Flyway V2 creates `subscription_schema.subscription_plan_audit`.

## 31. Audit contents
Plan, actor, action, old status, new status and timestamp are recorded.

## 32. Create audit
Plan creation records action `CREATE` and new status `DRAFT`.

## 33. Status audit
Plan status changes record previous and new status.

## 34. Database indexes
Indexes support plan and actor chronological audit lookup.

## 35. Repository changes
Owned listing and owned status-update methods were added.

## 36. Service changes
Role-specific ownership and response mapping are centralized in `SubscriptionService`.

## 37. Controller changes
Customer/public and admin methods now return separate DTO families.

## 38. Unit tests
Tests cover forced chef ownership, owned listing and privacy-reduced responses.

## 39. CI
`azure-pipelines-subscription-ownership-ci.yml` runs Java 21 Maven tests.

## 40. Static gates
CI confirms owned lookup, active-plan reads and audit migration presence.

## 41. Secret handling
No secret or credential was added.

## 42. Azure cost
No Azure resource is created by this branch.

## 43. APIM
No gateway operation is changed in this module.

## 44. Deployment prerequisite
Run CI against the exact branch head before merging.

## 45. Migration prerequisite
Verify Flyway V2 in development before exposing subscription screens.

## 46. Smoke test
Use separate chef identities and prove cross-chef plan reads/updates are denied.

## 47. Privacy smoke
Confirm public and customer JSON contains no identity UUID fields.

## 48. Rollback
Restore the previous image; retain the audit table because it is additive.

## 49. Pending manual work
Register and run the CI and later service-deployment pipelines.

## 50. Acceptance
CI, migration, ownership denial, public privacy and customer privacy checks must all pass.
