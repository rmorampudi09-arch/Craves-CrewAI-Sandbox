# Admin Subscription Operations

Route: `/admin/subscriptions`.

Operations:

```text
GET /api/v1/subscriptions/{subscriptionId}
PATCH /api/v1/admin/subscriptions/{subscriptionId}/status/{status}
```

The backend has no current administrator subscription-list/search contract. This module therefore uses exact UUID lookup rather than inventing a list endpoint.

Every status change requires an explicit reason in the browser. Allowed statuses are the exact Subscription Service statuses: PENDING_PAYMENT, ACTIVE, PAUSED, PAYMENT_FAILED, EXPIRED and CANCELLED.

No refund, credit, payout or renewal action is exposed. Later run `azure-pipelines-admin-subscription-operations-ci.yml`, configure APIM and test against synthetic subscriptions only.
