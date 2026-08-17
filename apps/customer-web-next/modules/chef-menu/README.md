# Craves Chef Menu Management

## Scope

Allows the approved kitchen owner to list, create and update Catalog menu items.

## Routes

```text
/chef/menu
GET|POST /api/chef/menu
PUT /api/chef/menu/{menuItemId}
```

## Backend contract

```text
GET  /api/v1/kitchens/me/menu-items
POST /api/v1/kitchens/me/menu-items
PUT  /api/v1/kitchens/me/menu-items/{menuItemId}
```

## Boundaries

- Catalog Service validates CHEF role and kitchen ownership.
- Browser mutations require same-origin requests.
- Price, currency, status and item snapshots are persisted only by Catalog Service.
- Kitchen IDs and blob storage fields are removed from browser responses.
- Image URLs must be HTTPS.
- No commission, discount, tax or delivery fee calculation is added.

## Pipelines

- `azure-pipelines-chef-web-menu-ci.yml`
- `azure-pipelines-chef-menu-apim.yml`

Run the kitchen-profile APIM module first. Menu image upload and quick availability are added by the next child module.
