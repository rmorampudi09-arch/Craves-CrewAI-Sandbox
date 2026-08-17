# Admin Subscription Plans

Route: `/admin/subscription-plans`.

Operations:

```text
GET|POST /api/v1/admin/subscription-plans
PATCH /api/v1/admin/subscription-plans/{planId}/status
GET /api/v1/backoffice/chef-reviews?status=APPROVED
```

The approved-chef endpoint supplies an ADMIN-only selector for chef assignment. The plan form forwards exact operator-entered plan code, owner, name, description, weekly/monthly period, amount and currency. It does not calculate or recommend pricing.

Only DRAFT, ACTIVE and INACTIVE are exposed. Subscription Service ownership hardening and plan audit remain authoritative.

Later run `azure-pipelines-admin-subscription-plans-ci.yml`, deploy Subscription Service PR #58, configure APIM, and test with approved/non-approved chef references.
