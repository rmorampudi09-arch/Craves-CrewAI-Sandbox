# Customer Subscription Management

Customer routes:

```text
/subscriptions
/subscriptions/new?planId=<uuid>
/subscriptions/{subscriptionId}
```

Browser BFF:

```text
GET|POST /api/subscriptions
GET /api/subscriptions/{subscriptionId}
PATCH /api/subscriptions/{subscriptionId}/pause
PATCH /api/subscriptions/{subscriptionId}/cancel
```

The module requires the HTTP-only Craves session. Enrollment forwards only plan ID, future/today start date, saved delivery address ID and optional notes. Subscription Service owns status and service dates.

Only pause and cancel are exposed. Renewal, resume, unused meals, refunds, credits and chef payouts remain pending product rules.

Later run `azure-pipelines-customer-web-subscription-management-ci.yml`, configure APIM, deploy in stack order and test with a customer-owned saved address.
