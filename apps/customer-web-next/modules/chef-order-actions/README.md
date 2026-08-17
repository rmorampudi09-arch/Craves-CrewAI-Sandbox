# Craves Chef Order Actions

## Scope

Adds only the chef workflow transitions already implemented by Order Service.

```text
POST /api/chef/orders/{orderId}/accept
POST /api/chef/orders/{orderId}/reject
POST /api/chef/orders/{orderId}/ready-for-pickup
```

## Safety

- Order Service validates CHEF role, kitchen ownership, current state and acceptance deadline.
- Browser mutations require same-origin requests.
- Accept requires preparation minutes from 1 to 1440.
- Accept and reject send one UUID as both `X-Correlation-ID` and `Idempotency-Key`.
- Notes and reasons are bounded to 500 characters.
- Ready-for-pickup is offered only for accepted/preparing orders.
- No cancel, refund, delivery, provider or payment transition is added.

## Pipelines

- `azure-pipelines-chef-web-order-actions-ci.yml`
- `azure-pipelines-chef-order-actions-apim.yml`

Run the chef order read APIM module first. The action APIM pipeline defaults to disabled.
