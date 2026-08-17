# Craves Chef Order Inbox

## Scope

Provides read-only chef-owned order history and detail views before workflow mutations are introduced.

## Routes

```text
/chef/orders
/chef/orders/{orderId}
GET /api/chef/orders
GET /api/chef/orders/{orderId}
```

## Backend

`GET /api/v1/chef/orders` and `GET /api/v1/chef/orders/{orderId}`.

## Safety

- Order Service validates CHEF role and kitchen ownership.
- Customer identity IDs, checkout IDs, kitchen IDs and pickup snapshots are removed.
- Recipient name, contact and delivery address are retained because the owned chef order contract exposes them for fulfillment.
- No accept, reject, ready, refund or delivery mutation is included.
- All amounts are rendered exactly as returned by Order Service.

## Pipelines

- `azure-pipelines-chef-web-order-inbox-ci.yml`
- `azure-pipelines-chef-order-read-apim.yml`

The APIM pipeline is confirmation-gated and adds only list/detail operations on `api/v1/chef/orders`.
