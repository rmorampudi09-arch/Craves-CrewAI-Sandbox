# Craves Chef Menu Media and Availability

## Scope

Adds quick Catalog-backed availability updates and menu image uploads for chef-owned dishes.

## Routes

```text
/chef/menu/media
PATCH /api/chef/menu/{menuItemId}/availability
POST  /api/chef/menu/{menuItemId}/images
```

## Safety

- Catalog Service validates chef role and ownership.
- All mutations require same-origin requests.
- Availability reasons are bounded to 500 characters.
- Images are limited to JPEG, PNG or WebP under 10 MB.
- Browser code never receives storage credentials, blob containers or blob names.
- Existing public image URLs must be HTTPS.
- Availability changes do not alter pricing or order state.

## Pipelines

- `azure-pipelines-chef-web-menu-media-ci.yml`
- `azure-pipelines-chef-menu-media-apim.yml`

The APIM pipeline defaults to disabled and adds only the availability and image-upload operations after the kitchen/menu APIM modules.
