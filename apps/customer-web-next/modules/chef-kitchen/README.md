# Craves Chef Kitchen Profile

Provides approved chefs with an owned Catalog Service kitchen profile.

## Routes

```text
/chef/kitchen
GET|PUT /api/chef/kitchen
```

## Backend

`GET|PUT /api/v1/kitchens/me`

## Boundaries

- Catalog Service validates CHEF role and ownership.
- Browser mutations require same-origin requests.
- Backend identity IDs are removed.
- Exact kitchen contact/address fields are supported.
- Coordinates must be absent or supplied as a valid pair.
- SUSPENDED is accepted as a read state but cannot be submitted by the web form.
- Serviceability, ranking, radius and public-contact policies are not invented.

## Pipelines

- `azure-pipelines-chef-web-kitchen-profile-ci.yml`
- `azure-pipelines-chef-kitchen-profile-apim.yml`

APIM configuration defaults to disabled and adds only profile GET/PUT operations on `api/v1/kitchens/me`.
