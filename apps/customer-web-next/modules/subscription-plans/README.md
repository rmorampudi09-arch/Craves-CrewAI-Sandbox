# Customer Subscription Plans

Customer route: `/subscriptions/plans`.

Browser BFF: `GET /api/subscriptions/plans`.

Backend contract: `GET /api/v1/subscriptions/plans`.

This module displays only active plans returned by Subscription Service. It does not calculate pricing or define renewal, unused-meal, cancellation, refund, chef-payout or holiday rules.

Security controls:

- no customer session required for browsing
- no internal chef identity in the public DTO
- no browser storage
- no-store responses
- HTTPS-only backend base URL
- bounded upstream timeout

Later run `azure-pipelines-customer-web-subscription-plans-ci.yml`, deploy Subscription Service ownership hardening first, configure APIM, and smoke-test active, empty and error states.
